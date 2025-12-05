package com.groom.order.application.service

import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.application.dto.CreateOrderResult
import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.common.idempotency.IdempotencyService
import com.groom.order.domain.event.OrderCreatedEvent
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.domain.service.OrderManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 생성 서비스 (이벤트 기반 비동기 플로우)
 *
 * 이벤트 기반 아키텍처:
 * 1. 주문 생성 (status: ORDER_CREATED)
 * 2. OrderCreatedEvent 발행 → Kafka → Product Service
 * 3. Product Service에서 재고 예약 후 stock.reserved/stock.reservation.failed 발행
 * 4. Order Service에서 해당 이벤트를 소비하여 주문 상태 업데이트
 *
 * Note: Order Service는 Product Service에 직접 접근하지 않습니다.
 * 상품 정보(productName, unitPrice)는 클라이언트에서 전달받습니다.
 */
@Service
class CreateOrderService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val orderManager: OrderManager,
    private val idempotencyService: IdempotencyService,
    private val domainEventPublisher: DomainEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 생성
     *
     * 빠른 응답 (~100ms)을 위해 재고 확인을 기다리지 않고 즉시 주문을 접수합니다.
     * 재고 예약은 비동기로 Product Service에서 처리됩니다.
     *
     * @param command 주문 생성 커맨드
     * @param now 현재 시각
     * @return 생성된 주문 결과
     */
    @Transactional
    fun createOrder(
        command: CreateOrderCommand,
        now: LocalDateTime = LocalDateTime.now(),
    ): CreateOrderResult {
        // 1. 멱등성 확인 (Stripe 방식 - 중복 요청 시 기존 주문 반환)
        val existingOrderId = idempotencyService.getOrderId(command.idempotencyKey)
        if (existingOrderId != null) {
            logger.info { "Duplicate request detected, returning existing order: $existingOrderId" }
            val existingOrder =
                loadOrderPort.loadById(UUID.fromString(existingOrderId))
                    ?: throw OrderException.OrderNotFound(UUID.fromString(existingOrderId))
            return CreateOrderResult.from(existingOrder, now)
        }

        // 멱등성 키 등록 (첫 요청)
        if (!idempotencyService.ensureIdempotency(command.idempotencyKey)) {
            // 동시 요청 경합 발생 - 다시 시도
            val concurrentOrderId = idempotencyService.getOrderId(command.idempotencyKey)
            if (concurrentOrderId != null) {
                logger.info { "Concurrent request detected, returning existing order: $concurrentOrderId" }
                val concurrentOrder =
                    loadOrderPort.loadById(UUID.fromString(concurrentOrderId))
                        ?: throw OrderException.OrderNotFound(UUID.fromString(concurrentOrderId))
                return CreateOrderResult.from(concurrentOrder, now)
            }
            throw OrderException.DuplicateOrderRequest(command.idempotencyKey)
        }

        // 2. 주문 생성 (도메인 서비스 오케스트레이션)
        val orderItemRequests =
            command.items.map { itemDto ->
                OrderManager.OrderItemRequest(
                    productId = itemDto.productId,
                    productName = itemDto.productName,
                    quantity = itemDto.quantity,
                    unitPrice = itemDto.unitPrice,
                )
            }

        val savedOrder =
            orderManager
                .createOrder(
                    userId = command.userExternalId,
                    storeId = command.storeId,
                    itemRequests = orderItemRequests,
                    note = command.note,
                    now = now,
                ).let { order ->
                    saveOrderPort.save(order)
                    order
                }

        logger.info { "Order created successfully: ${savedOrder.orderNumber} (status: ${savedOrder.status})" }

        // 3. 멱등성 키에 주문 ID 저장 (Stripe 방식)
        idempotencyService.storeOrderId(command.idempotencyKey, savedOrder.id.toString())

        // 4. 도메인 이벤트 발행 (order.created → Product Service로 전달)
        val orderCreatedEvent =
            OrderCreatedEvent(
                orderId = savedOrder.id,
                orderNumber = savedOrder.orderNumber,
                userExternalId = savedOrder.userExternalId,
                storeId = savedOrder.storeId,
                totalAmount = savedOrder.calculateTotalAmount(),
                status = savedOrder.status,
                items =
                    command.items.map { itemDto ->
                        OrderCreatedEvent.OrderItem(
                            productId = itemDto.productId,
                            quantity = itemDto.quantity,
                        )
                    },
            )
        domainEventPublisher.publish(orderCreatedEvent)

        logger.info { "OrderCreatedEvent published for order: ${savedOrder.orderNumber}" }

        return CreateOrderResult.from(savedOrder, now)
    }
}
