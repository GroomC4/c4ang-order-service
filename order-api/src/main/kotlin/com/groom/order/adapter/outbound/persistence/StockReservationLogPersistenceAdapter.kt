package com.groom.order.adapter.outbound.persistence

import com.groom.order.domain.model.StockReservationLog
import com.groom.order.domain.port.SaveStockReservationLogPort
import com.groom.order.adapter.outbound.persistence.StockReservationLogJpaEntity
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
        val entity = StockReservationLogJpaEntity.from(log)
        val savedEntity = stockReservationLogJpaRepository.save(entity)
        return savedEntity.toDomain()
    }
}
