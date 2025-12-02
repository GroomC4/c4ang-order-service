package com.groom.order.adapter.outbound.stock

import com.groom.order.domain.model.ReservationResult
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * 재고 예약 서비스 인터페이스
 */
interface StockReservationService {
    /**
     * 재고 예약 시도
     *
     * @param storeId 스토어 ID
     * @param items 주문 상품 목록
     * @param reservationId 예약 ID
     * @param ttl 예약 유지 시간 (기본 10분)
     * @return 예약 결과
     */
    fun tryReserve(
        storeId: UUID,
        items: List<OrderItemRequest>,
        reservationId: String,
        ttl: Duration = Duration.ofMinutes(10),
    ): ReservationResult

    /**
     * 예약 확정 (결제 완료 시)
     */
    fun confirmReservation(reservationId: String)

    /**
     * 예약 취소 (주문 취소 또는 타임아웃)
     */
    fun cancelReservation(reservationId: String)

    /**
     * 만료된 예약 자동 처리 (스케줄러 호출)
     */
    fun processExpiredReservations(now: LocalDateTime = LocalDateTime.now())
}

/**
 * 주문 상품 요청 데이터
 */
data class OrderItemRequest(
    val productId: UUID,
    val quantity: Int,
)
