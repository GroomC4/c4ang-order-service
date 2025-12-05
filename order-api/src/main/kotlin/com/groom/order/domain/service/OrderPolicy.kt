package com.groom.order.domain.service

import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 주문 정책 검증 서비스
 *
 * 주문 관련 비즈니스 규칙을 검증합니다.
 *
 * Note: 이벤트 기반 아키텍처에서 Order Service는 Product Service에 직접 접근하지 않습니다.
 * 상품-스토어 소속 검증은 Product Service에서 수행합니다.
 */
@Component
class OrderPolicy {
    /**
     * 주문 생성 가능 여부 검증
     *
     * @param userId 사용자 ID
     * @param storeId 스토어 ID
     * @param items 주문 상품 목록
     */
    fun validateOrderCreation(
        userId: UUID,
        storeId: UUID,
        items: List<OrderItemData>,
    ) {
        // 주문 상품이 비어있는지 검증
        require(items.isNotEmpty()) {
            "Order must contain at least one item"
        }

        // 주문 상품 수량 검증
        items.forEach { item ->
            require(item.quantity > 0) {
                "Item quantity must be positive: ${item.productId}"
            }
            require(item.quantity <= 999) {
                "Item quantity cannot exceed 999: ${item.productId}"
            }
        }
    }

    /**
     * 주문 소유권 검증
     *
     * @param order 주문
     * @param userId 요청 사용자 ID
     */
    fun checkOrderOwnership(
        order: Order,
        userId: UUID,
    ) {
        if (order.userExternalId != userId) {
            throw OrderException.OrderAccessDenied(order.id, userId)
        }
    }

    /**
     * 주문 취소 가능 여부 검증
     *
     * 취소 가능 상태:
     * - ORDER_CREATED: 재고 확인 대기 중
     * - ORDER_CONFIRMED: 재고 예약 완료 (결제 대기)
     * - PAYMENT_PENDING: 결제 대기 중
     * - PAYMENT_PROCESSING: 결제 진행 중
     * - PREPARING: 상품 준비 중
     *
     * @param order 주문
     * @return 취소 가능 여부
     */
    fun canCancelOrder(order: Order): Boolean =
        order.status in
            listOf(
                OrderStatus.ORDER_CREATED,
                OrderStatus.ORDER_CONFIRMED,
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAYMENT_PROCESSING,
                OrderStatus.PREPARING,
            )

    /**
     * 주문 환불 가능 여부 검증
     *
     * @param order 주문
     * @return 환불 가능 여부
     */
    fun canRefundOrder(order: Order): Boolean = order.status == OrderStatus.DELIVERED
}

/**
 * 주문 상품 데이터 (도메인 서비스용)
 */
data class OrderItemData(
    val productId: UUID,
    val productName: String,
    val quantity: Int,
    val unitPrice: java.math.BigDecimal,
)
