package com.groom.order.application.dto

import java.math.BigDecimal
import java.util.UUID

/**
 * 주문 생성 Command DTO
 *
 * 고객이 주문을 생성할 때 사용합니다.
 *
 * Note: productName, unitPrice는 클라이언트에서 전달받습니다.
 * 이벤트 기반 아키텍처에서 Order Service는 Product Service에 직접 접근하지 않고,
 * order.created 이벤트를 발행하면 Product Service가 재고를 예약합니다.
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
        val productName: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
    )
}
