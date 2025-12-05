package com.groom.order.application.dto

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.util.UUID

/**
 * Internal API - 주문 조회 Result DTO
 *
 * Payment Service에서 결제 요청 시 주문 정보 검증에 사용합니다.
 */
data class GetInternalOrderResult(
    val orderId: UUID,
    val userId: UUID,
    val orderNumber: String,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val items: List<OrderItemInfo>,
) {
    data class OrderItemInfo(
        val productId: UUID,
        val productName: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
    )

    companion object {
        fun from(order: Order): GetInternalOrderResult =
            GetInternalOrderResult(
                orderId = order.id,
                userId = order.userExternalId,
                orderNumber = order.orderNumber,
                status = order.status,
                totalAmount = order.calculateTotalAmount(),
                items = order.items.map { item ->
                    OrderItemInfo(
                        productId = item.productId,
                        productName = item.productName,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                    )
                },
            )
    }
}

/**
 * Internal API - 결제 대기 상태 변경 Result DTO
 */
data class MarkPaymentPendingResult(
    val orderId: UUID,
    val status: OrderStatus,
    val paymentId: UUID,
) {
    companion object {
        fun from(order: Order): MarkPaymentPendingResult =
            MarkPaymentPendingResult(
                orderId = order.id,
                status = order.status,
                paymentId = order.paymentId!!,
            )
    }
}

/**
 * Internal API - 결제 존재 여부 확인 Result DTO
 */
data class CheckHasPaymentResult(
    val orderId: UUID,
    val hasPayment: Boolean,
) {
    companion object {
        fun from(order: Order): CheckHasPaymentResult =
            CheckHasPaymentResult(
                orderId = order.id,
                hasPayment = order.paymentId != null,
            )
    }
}
