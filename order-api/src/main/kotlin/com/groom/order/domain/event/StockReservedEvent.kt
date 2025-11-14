package com.groom.order.domain.event

import com.groom.ecommerce.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 재고 예약 이벤트
 *
 * Redis에서 재고 예약이 성공했을 때 발행 (STOCK_RESERVED 상태)
 */
data class StockReservedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val reservationId: String,
    val storeId: UUID,
    val products: List<ReservedProduct>,
    val expiresAt: LocalDateTime,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent

/**
 * 예약된 상품 정보
 */
data class ReservedProduct(
    val productId: UUID,
    val quantity: Int,
)
