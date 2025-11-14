package com.groom.order.domain.port

import com.groom.order.domain.model.StockReservationLog

/**
 * StockReservationLog 저장을 위한 Outbound Port
 *
 * Domain이 재고 예약 로그 영속성 계층에 요구하는 저장 계약입니다.
 */
interface SaveStockReservationLogPort {
    /**
     * 재고 예약 로그 저장
     *
     * @param log 저장할 재고 예약 로그
     * @return 저장된 재고 예약 로그
     */
    fun save(log: StockReservationLog): StockReservationLog
}
