package com.groom.order.application.event

import com.groom.order.domain.event.OrderConfirmedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 확정 이벤트 핸들러
 *
 * OrderConfirmedEvent 발생 시 감사 로그를 기록합니다.
 */
@Component
class OrderConfirmedEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderConfirmed(event: OrderConfirmedEvent) {
        logger.info { "Handling OrderConfirmedEvent: orderId=${event.orderId}" }

        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "confirmedAt" to event.confirmedAt.toString(),
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.ORDER_CONFIRMED,
            changeSummary = "주문이 확정되었습니다. (확정시각: ${event.confirmedAt})",
            actorUserId = null, // 시스템 자동 처리
            metadata = metadata,
        )

        logger.debug { "OrderConfirmedEvent handled successfully: orderId=${event.orderId}" }
    }
}
