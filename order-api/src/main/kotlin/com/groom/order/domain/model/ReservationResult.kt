package com.groom.order.domain.model

/**
 * 재고 예약 결과
 */
sealed class ReservationResult {
    /**
     * 예약 성공
     */
    data class Success(
        val reservationId: String,
    ) : ReservationResult()

    /**
     * 재고 부족
     */
    data object InsufficientStock : ReservationResult()

    /**
     * 스토어 닫힘
     */
    data object StoreClosed : ReservationResult()
}
