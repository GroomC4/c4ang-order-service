package com.groom.order.application.dto

import com.groom.order.domain.model.OrderStatus
import java.util.UUID

/**
 * 주문 리스트 조회 Query DTO
 */
data class ListOrdersQuery(
    val requestUserId: UUID,
    val status: OrderStatus? = null,
)
