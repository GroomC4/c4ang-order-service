package com.groom.order.application.dto

import java.util.UUID

/**
 * 주문 취소 Command DTO
 */
data class CancelOrderCommand(
    val orderId: UUID,
    val requestUserId: UUID,
    val cancelReason: String?,
)
