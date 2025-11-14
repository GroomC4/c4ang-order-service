package com.groom.order.application.dto

import java.util.UUID

/**
 * 주문 상세 조회 Query DTO
 */
data class GetOrderDetailQuery(
    val orderId: UUID,
    val requestUserId: UUID,
)
