package com.groom.order.domain.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 재고 예약 (Value Object)
 *
 * 재고 예약에 대한 도메인 개념을 표현합니다.
 */
data class StockReservation(
    val reservationId: String,
    val storeId: UUID,
    val items: List<ReservationItem>,
    val expiresAt: LocalDateTime,
) {
    /**
     * 예약 항목
     */
    data class ReservationItem(
        val productId: UUID,
        val quantity: Int,
    ) {
        init {
            require(quantity > 0) { "Reservation quantity must be positive" }
        }
    }
}

/**
 * 재고 예약 요청 항목 (Value Object)
 *
 * 재고 예약을 요청할 때 사용하는 최소 정보를 담습니다.
 */
data class ReservationItemRequest(
    val productId: UUID,
    val quantity: Int,
) {
    init {
        require(quantity > 0) { "Reservation quantity must be positive" }
    }
}
