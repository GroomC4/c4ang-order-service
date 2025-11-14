package com.groom.order.infrastructure.repository

import com.groom.order.infrastructure.persistence.StockReservationLogJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 재고 예약 로그 리포지토리
 */
@Repository
interface StockReservationLogRepositoryImpl : JpaRepository<StockReservationLogJpaEntity, UUID> {
    /**
     * 예약 ID로 재고 예약 로그 조회
     */
    fun findByReservationId(reservationId: String): StockReservationLogJpaEntity?

    /**
     * 주문 ID로 재고 예약 로그 목록 조회
     */
    fun findByOrderId(orderId: UUID): List<StockReservationLogJpaEntity>
}
