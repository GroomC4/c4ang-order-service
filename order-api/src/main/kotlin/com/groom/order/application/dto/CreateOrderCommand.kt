package com.groom.order.application.dto

import java.util.UUID

/**
 * 주문 생성 Command DTO
 *
 * 고객이 주문을 생성할 때 사용합니다.
 */
data class CreateOrderCommand(
    val userExternalId: UUID,
    val storeId: UUID,
    val items: List<OrderItemDto>,
    val note: String? = null,
    val idempotencyKey: String, // 중복 요청 방지용 키
) {
    data class OrderItemDto(
        val productId: UUID,
        val quantity: Int,
    )
}
