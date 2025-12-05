package com.groom.order.domain.service

import com.groom.order.common.exception.OrderException
import com.groom.order.domain.event.OrderCancelledEvent
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 관리자 (도메인 서비스)
 *
 * 주문 생성, 취소 등 복잡한 비즈니스 로직을 조율합니다.
 *
 * Note: 이벤트 기반 아키텍처에서 Order Service는 Product Service에 직접 접근하지 않습니다.
 * 주문 생성 시 클라이언트에서 상품 정보(productName, unitPrice)를 전달받습니다.
 */
@Component
class OrderManager(
    private val orderPolicy: OrderPolicy,
    private val orderNumberGenerator: OrderNumberGenerator,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 생성 (비즈니스 규칙 검증)
     *
     * 이벤트 기반 플로우:
     * 1. 주문 생성 (status: ORDER_CREATED)
     * 2. OrderCreatedEvent 발행 → Product Service로 전달
     * 3. Product Service에서 재고 예약 후 stock.reserved/stock.reservation.failed 발행
     * 4. Order Service에서 해당 이벤트를 소비하여 주문 상태 업데이트
     *
     * @param userId 사용자 ID
     * @param storeId 스토어 ID
     * @param itemRequests 주문 상품 요청 정보 (productId, productName, quantity, unitPrice)
     * @param note 주문 메모
     * @return 생성된 주문
     */
    fun createOrder(
        userId: UUID,
        storeId: UUID,
        itemRequests: List<OrderItemRequest>,
        note: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): Order {
        // 1. OrderItemData 생성 (도메인 객체 초기화)
        val items =
            itemRequests.map { request ->
                OrderItemData(
                    productId = request.productId,
                    productName = request.productName,
                    quantity = request.quantity,
                    unitPrice = request.unitPrice,
                )
            }

        // 2. 정책 검증
        orderPolicy.validateOrderCreation(userId, storeId, items)

        // 3. 주문 번호 생성
        val orderNumber = orderNumberGenerator.generate(now)

        // 4. 총액 계산
        val totalAmount =
            items.sumOf { item ->
                item.unitPrice.multiply(BigDecimal(item.quantity))
            }

        logger.info { "Creating order: $orderNumber, total: $totalAmount" }

        // 5. Order 엔티티 생성 (초기 상태: ORDER_CREATED)
        val order =
            Order(
                userExternalId = userId,
                storeId = storeId,
                orderNumber = orderNumber,
                status = OrderStatus.ORDER_CREATED,
                paymentSummary = emptyMap(),
                timeline =
                    listOf(
                        mapOf(
                            "status" to OrderStatus.ORDER_CREATED.name,
                            "timestamp" to now.toString(),
                            "description" to "주문 생성됨 (재고 확인 대기 중)",
                        ),
                    ),
                note = note,
            )

        // 6. OrderItem 추가
        items.forEach { itemData ->
            val orderItem =
                OrderItem(
                    productId = itemData.productId,
                    productName = itemData.productName,
                    quantity = itemData.quantity,
                    unitPrice = itemData.unitPrice,
                )
            order.addItem(orderItem)
        }

        logger.info { "Order created: $orderNumber (${items.size} items, total: $totalAmount)" }

        return order
    }

    /**
     * 주문 상품 요청 정보
     *
     * 클라이언트에서 상품 정보를 함께 전달합니다.
     */
    data class OrderItemRequest(
        val productId: UUID,
        val productName: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
    )

    /**
     * 주문 취소 (결제 전)
     *
     * @param order 주문
     * @param userId 요청 사용자 ID
     * @param cancelReason 취소 사유
     * @return 주문 취소 이벤트
     */
    fun cancelOrder(
        order: Order,
        userId: UUID,
        cancelReason: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): OrderCancelledEvent {
        // 1. 소유권 검증
        orderPolicy.checkOrderOwnership(order, userId)

        // 2. 취소 가능 상태 확인
        if (!orderPolicy.canCancelOrder(order)) {
            throw OrderException.CannotCancelOrder(
                orderId = order.id,
                currentStatus = order.status.name,
            )
        }

        logger.info { "Cancelling order: ${order.orderNumber}, reason: $cancelReason" }

        // 3. 주문 상태 변경
        order.cancel(cancelReason, now)

        // 4. 이벤트 생성
        return OrderCancelledEvent(
            orderId = order.id,
            orderNumber = order.orderNumber,
            userExternalId = order.userExternalId,
            storeId = order.storeId,
            cancelReason = cancelReason,
            cancelledAt = order.cancelledAt ?: now,
        )
    }

    /**
     * 주문 환불 가능 여부 확인
     *
     * @param order 주문
     * @param userId 요청 사용자 ID
     */
    fun validateRefund(
        order: Order,
        userId: UUID,
    ) {
        // 1. 소유권 검증
        orderPolicy.checkOrderOwnership(order, userId)

        // 2. 환불 가능 상태 확인
        if (!orderPolicy.canRefundOrder(order)) {
            throw OrderException.CannotRefundOrder(
                orderId = order.id,
                currentStatus = order.status.name,
            )
        }
    }
}
