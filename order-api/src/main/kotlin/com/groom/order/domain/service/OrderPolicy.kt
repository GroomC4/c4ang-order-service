package com.groom.order.domain.service

import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.model.ProductInfo
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 주문 정책 검증 서비스
 *
 * 주문 관련 비즈니스 규칙을 검증합니다.
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
     * @param order 주문
     * @return 취소 가능 여부
     */
    fun canCancelOrder(order: Order): Boolean =
        order.status in
            listOf(
                OrderStatus.PENDING,
                OrderStatus.STOCK_RESERVED,
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

    /**
     * 상품이 지정된 스토어에 속하는지 검증
     *
     * @param products 상품 목록
     * @param storeId 스토어 ID
     */
    fun validateProductsBelongToStore(
        products: List<ProductInfo>,
        storeId: UUID,
    ) {
        products.forEach { product ->
            require(product.storeId == storeId) {
                "${product.name}(${product.id}) 스토어($storeId)의 상품이 아닙니다"
            }
        }
    }
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
