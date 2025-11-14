package com.groom.order.application.event

import com.groom.order.domain.event.StockReservedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.model.ProductReservation
import com.groom.order.domain.model.StockReservationLog
import com.groom.order.domain.model.StockReservationStatus
import com.groom.order.domain.service.OrderAuditRecorder
import com.groom.order.infrastructure.persistence.StockReservationLogJpaEntity
import com.groom.order.domain.port.SaveStockReservationLogPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

/**
 * 재고 예약 이벤트 핸들러
 *
 * StockReservedEvent 발생 시:
 * 1. 재고 예약 로그를 DB에 백업
 * 2. 주문 감사 로그를 기록
 */
@Component
class StockReservedEventHandler(
    private val saveStockReservationLogPort: SaveStockReservationLogPort,
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleStockReserved(event: StockReservedEvent) {
        logger.info { "Handling StockReservedEvent: orderId=${event.orderId}, reservationId=${event.reservationId}" }

        // 1. 재고 예약 로그 저장
        val stockReservationLog =
            StockReservationLog(
                id = UUID.randomUUID(),
                reservationId = event.reservationId,
                orderId = event.orderId,
                storeId = event.storeId,
                products = event.products.map { ProductReservation(it.productId, it.quantity) },
                status = StockReservationStatus.RESERVED,
                reservedAt = event.occurredAt,
                expiresAt = event.expiresAt,
                createdAt = event.occurredAt,
                updatedAt = event.occurredAt,
            )

        saveStockReservationLogPort.save(stockReservationLog)
        logger.info { "Stock reservation log saved: reservationId=${event.reservationId}" }

        // 2. 주문 감사 로그 기록
        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "reservationId" to event.reservationId,
                "expiresAt" to event.expiresAt.toString(),
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.STOCK_RESERVED,
            changeSummary = "재고가 예약되었습니다. (예약ID: ${event.reservationId}, 만료: ${event.expiresAt})",
            actorUserId = null, // 시스템 자동 처리
            metadata = metadata,
        )

        logger.debug { "StockReservedEvent handled successfully: orderId=${event.orderId}" }
    }
}
