package com.groom.order.domain.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 재고 예약 로그 상태
 */
enum class StockReservationStatus {
    RESERVED, // 예약됨
    CONFIRMED, // 확정됨 (결제 완료)
    RELEASED, // 해제됨 (취소)
    EXPIRED, // 만료됨
}

/**
 * 재고 예약 로그 도메인 모델
 *
 * Redis 재고 예약 시스템의 백업 및 모니터링을 위한 DB 로그
 */
data class StockReservationLog(
    val id: UUID,
    val reservationId: String,
    val orderId: UUID,
    val storeId: UUID,
    val products: List<ProductReservation>,
    var status: StockReservationStatus,
    val reservedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    var confirmedAt: LocalDateTime? = null,
    var releasedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * 예약 확정 (결제 완료 시)
     */
    fun confirm() {
        status = StockReservationStatus.CONFIRMED
        confirmedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    /**
     * 예약 해제 (주문 취소 시)
     */
    fun release() {
        status = StockReservationStatus.RELEASED
        releasedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    /**
     * 예약 만료 (타임아웃 시)
     */
    fun expire() {
        status = StockReservationStatus.EXPIRED
        releasedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }
}

/**
 * 예약된 상품 정보
 */
data class ProductReservation(
    val productId: UUID,
    val quantity: Int,
)
