package com.groom.order.application.dto

import java.util.UUID

/**
 * 주문 환불 Command DTO
 */
data class RefundOrderCommand(
    val orderId: UUID,
    val requestUserId: UUID,
    val refundReason: String?,
)
