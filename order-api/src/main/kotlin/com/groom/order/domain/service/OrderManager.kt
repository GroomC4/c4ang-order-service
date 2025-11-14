package com.groom.order.domain.service

import com.groom.order.common.exception.OrderException
import com.groom.order.domain.event.OrderCancelledEvent
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.model.ProductInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 관리자 (도메인 서비스)
 *
 * 주문 생성, 취소 등 복잡한 비즈니스 로직을 조율합니다.
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
     * @param userId 사용자 ID
     * @param storeId 스토어 ID
     * @param itemRequests 주문 상품 요청 정보 (productId, quantity)
     * @param products 조회된 상품 목록
     * @param reservationId 재고 예약 ID
     * @param expiresAt 결제 만료 시각
     * @param note 주문 메모
     * @return 생성된 주문
     */
    fun createOrder(
        userId: UUID,
        storeId: UUID,
        itemRequests: List<OrderItemRequest>,
        products: List<ProductInfo>,
        reservationId: String,
        expiresAt: LocalDateTime,
        note: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): Order {
        // 1. OrderItemData 생성 (도메인 객체 초기화)
        val items =
            itemRequests.zip(products).map { (itemRequest, product) ->
                OrderItemData(
                    productId = product.id,
                    productName = product.name,
                    quantity = itemRequest.quantity,
                    unitPrice = product.price,
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

        // 5. Order 엔티티 생성
        val order =
            Order(
                userExternalId = userId,
                storeId = storeId,
                orderNumber = orderNumber,
                status = OrderStatus.PENDING,
                paymentSummary = emptyMap(),
                timeline =
                    listOf(
                        mapOf(
                            "status" to OrderStatus.PENDING.name,
                            "timestamp" to now.toString(),
                            "description" to "주문 생성됨",
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

        // 7. 재고 예약 정보 설정
        order.reserveStock(reservationId, expiresAt)

        logger.info { "Order created: $orderNumber (${items.size} items, total: $totalAmount)" }

        return order
    }

    /**
     * 주문 상품 요청 정보
     */
    data class OrderItemRequest(
        val productId: UUID,
        val quantity: Int,
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
