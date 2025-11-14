package com.groom.order.application.event

import com.groom.order.domain.event.OrderTimeoutEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 타임아웃 이벤트 핸들러
 *
 * OrderTimeoutEvent 발생 시 감사 로그를 기록합니다.
 */
@Component
class OrderTimeoutEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderTimeout(event: OrderTimeoutEvent) {
        logger.info { "Handling OrderTimeoutEvent: orderId=${event.orderId}, reservationId=${event.reservationId}" }

        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "storeId" to event.storeId.toString(),
                "reservationId" to (event.reservationId ?: "없음"),
                "paymentId" to (event.paymentId?.toString() ?: "없음"),
                "timeoutAt" to event.timeoutAt.toString(),
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.ORDER_TIMEOUT,
            changeSummary = "주문이 타임아웃되었습니다. (결제 미완료)",
            actorUserId = null, // 시스템 자동 처리
            metadata = metadata,
        )

        logger.debug { "OrderTimeoutEvent handled successfully: orderId=${event.orderId}" }
    }
}
