package com.groom.order.adapter.outbound.persistence

import com.groom.order.adapter.outbound.persistence.StockReservationLogJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * StockReservationLog JPA Repository
 *
 * StockReservationLogJpaEntity에 대한 JPA 인터페이스입니다.
 */
interface StockReservationLogJpaRepository : JpaRepository<StockReservationLogJpaEntity, UUID>
