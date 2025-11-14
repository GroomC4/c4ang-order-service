package com.groom.order.adapter.out.persistence

import com.groom.order.domain.model.StockReservationLog
import com.groom.order.domain.port.SaveStockReservationLogPort
import com.groom.order.infrastructure.persistence.StockReservationLogJpaEntity
import org.springframework.stereotype.Component

/**
 * StockReservationLog 영속성 Adapter
 *
 * SaveStockReservationLogPort를 구현하여 재고 예약 로그 저장 기능을 제공합니다.
 */
@Component
class StockReservationLogPersistenceAdapter(
    private val stockReservationLogJpaRepository: StockReservationLogJpaRepository,
) : SaveStockReservationLogPort {
    override fun save(log: StockReservationLog): StockReservationLog {
        val entity = log.toEntity()
        val savedEntity = stockReservationLogJpaRepository.save(entity)
        return savedEntity.toDomain()
    }
}

// Extension functions for entity-domain conversion
private fun StockReservationLog.toEntity(): StockReservationLogJpaEntity =
    StockReservationLogJpaEntity(
        id = this.id,
        reservationId = this.reservationId,
        orderId = this.orderId,
        storeId = this.storeId,
        products = this.products,
        status = this.status,
        expiresAt = this.expiresAt,
    )

private fun StockReservationLogJpaEntity.toDomain(): StockReservationLog =
    StockReservationLog(
        id = this.id,
        reservationId = this.reservationId,
        orderId = this.orderId,
        storeId = this.storeId,
        products = this.products,
        status = this.status,
        expiresAt = this.expiresAt,
    )
