package com.groom.order.application.dto

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 취소 Result DTO
 */
data class CancelOrderResult(
    val orderId: UUID,
    val orderNumber: String,
    val status: OrderStatus,
    val cancelledAt: LocalDateTime,
    val cancelReason: String?,
) {
    companion object {
        fun from(order: Order): CancelOrderResult =
            CancelOrderResult(
                orderId = order.id,
                orderNumber = order.orderNumber,
                status = order.status,
                cancelledAt = order.cancelledAt ?: LocalDateTime.now(),
                cancelReason = order.failureReason,
            )
    }
}
