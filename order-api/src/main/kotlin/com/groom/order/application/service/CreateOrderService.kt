package com.groom.order.application.service

import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.common.exception.ProductException
import com.groom.order.common.exception.StoreException
import com.groom.order.common.idempotency.IdempotencyService
import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.application.dto.CreateOrderResult
import com.groom.order.domain.event.OrderCreatedEvent
import com.groom.order.domain.event.ReservedProduct
import com.groom.order.domain.event.StockReservedEvent
import com.groom.order.domain.port.ProductPort
import com.groom.order.domain.port.StorePort
import com.groom.order.domain.service.OrderManager
import com.groom.order.domain.service.OrderPolicy
import com.groom.order.domain.service.StockReservationManager
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문 생성 서비스 (비동기 플로우)
 *
 * 애플리케이션 서비스의 책임:
 * 1. 트랜잭션 관리
 * 2. 인프라 계층 접근 (Repository, Redis 등)
 * 3. 도메인 서비스 오케스트레이션
 * 4. 도메인 이벤트 발행
 *
 * 비즈니스 로직은 도메인 계층(OrderManager, OrderPolicy)에 위임
 */
@Service
class CreateOrderService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val productPort: ProductPort,
    private val storePort: StorePort,
    private val stockReservationManager: StockReservationManager,
    private val orderManager: OrderManager,
    private val orderPolicy: OrderPolicy,
    private val idempotencyService: IdempotencyService,
    private val domainEventPublisher: DomainEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

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
                loadOrderPort.loadById(java.util.UUID.fromString(existingOrderId))
                    ?: throw OrderException.OrderNotFound(java.util.UUID.fromString(existingOrderId))
            return CreateOrderResult.from(existingOrder, now)
        }

        // 멱등성 키 등록 (첫 요청)
        if (!idempotencyService.ensureIdempotency(command.idempotencyKey)) {
            // 동시 요청 경합 발생 - 다시 시도
            val concurrentOrderId = idempotencyService.getOrderId(command.idempotencyKey)
            if (concurrentOrderId != null) {
                logger.info { "Concurrent request detected, returning existing order: $concurrentOrderId" }
                val concurrentOrder =
                    loadOrderPort.loadById(java.util.UUID.fromString(concurrentOrderId))
                        ?: throw OrderException.OrderNotFound(java.util.UUID.fromString(concurrentOrderId))
                return CreateOrderResult.from(concurrentOrder, now)
            }
            throw OrderException.DuplicateOrderRequest(command.idempotencyKey)
        }

        // 2. 스토어 존재 확인 (Port를 통한 접근)
        if (!storePort.existsById(command.storeId)) {
            throw StoreException.StoreNotFound(command.storeId)
        }

        // 3. 상품 조회 (Port를 통한 접근)
        val products =
            command.items.map { itemDto ->
                productPort
                    .loadById(itemDto.productId)
                    ?: throw ProductException.ProductNotFound(itemDto.productId)
            }

        // 4. 상품 검증 (도메인 로직 위임)
        orderPolicy.validateProductsBelongToStore(products, command.storeId)

        // 5. 재고 예약 (도메인 서비스 위임)
        val reservationItems =
            command.items.map { itemDto ->
                StockReservationManager.ReservationItemRequest(
                    productId = itemDto.productId,
                    quantity = itemDto.quantity,
                )
            }

        val stockReservation =
            stockReservationManager
                .generateStockReservation(
                    storeId = command.storeId,
                    items = reservationItems,
                    now = now,
                ).apply(stockReservationManager::tryReserve)

        // 6. 주문 생성 (도메인 서비스 오케스트레이션)
        val orderItemRequests =
            command.items.map { itemDto ->
                OrderManager.OrderItemRequest(
                    productId = itemDto.productId,
                    quantity = itemDto.quantity,
                )
            }

        val savedOrder =
            orderManager
                .createOrder(
                    userId = command.userExternalId,
                    storeId = command.storeId,
                    itemRequests = orderItemRequests,
                    products = products,
                    reservationId = stockReservation.reservationId,
                    expiresAt = stockReservation.expiresAt,
                    note = command.note,
                    now = now,
                ).let { order ->
                    // 재고 예약 성공 시 즉시 상태를 STOCK_RESERVED로 변경
                    order.markStockReserved()
                    saveOrderPort.save(order)
                    order
                }

        logger.info { "Order created successfully: ${savedOrder.orderNumber}" }

        // 6-1. 멱등성 키에 주문 ID 저장 (Stripe 방식)
        idempotencyService.storeOrderId(command.idempotencyKey, savedOrder.id.toString())

        // 7. 도메인 이벤트 발행
        val orderCreatedEvent =
            OrderCreatedEvent(
                orderId = savedOrder.id,
                orderNumber = savedOrder.orderNumber,
                userExternalId = savedOrder.userExternalId,
                storeId = savedOrder.storeId,
                totalAmount = savedOrder.calculateTotalAmount(),
                status = savedOrder.status,
            )
        domainEventPublisher.publish(orderCreatedEvent)

        val stockReservedEvent =
            StockReservedEvent(
                orderId = savedOrder.id,
                orderNumber = savedOrder.orderNumber,
                reservationId = stockReservation.reservationId,
                storeId = savedOrder.storeId,
                products =
                    command.items.map { itemDto ->
                        ReservedProduct(
                            productId = itemDto.productId,
                            quantity = itemDto.quantity,
                        )
                    },
                expiresAt = stockReservation.expiresAt,
            )
        domainEventPublisher.publish(stockReservedEvent)

        return CreateOrderResult.from(savedOrder, now)
    }
}
