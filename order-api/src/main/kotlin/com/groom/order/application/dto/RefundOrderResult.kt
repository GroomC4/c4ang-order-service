package com.groom.order.application.dto

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.util.UUID

/**
 * 주문 환불 Result DTO
 */
data class RefundOrderResult(
    val orderId: UUID,
    val orderNumber: String,
    val status: OrderStatus,
    val refundId: String,
    val refundAmount: BigDecimal,
    val refundReason: String?,
) {
    companion object {
        fun from(
            order: Order,
            refundAmount: BigDecimal,
        ): RefundOrderResult =
            RefundOrderResult(
                orderId = order.id,
                orderNumber = order.orderNumber,
                status = order.status,
                refundId = order.refundId ?: "",
                refundAmount = refundAmount,
                refundReason = order.failureReason,
            )
    }
}
