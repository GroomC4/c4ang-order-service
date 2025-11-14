package com.groom.order.application.event

import com.groom.order.domain.event.OrderPaymentPendingEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 결제 대기 이벤트 핸들러
 *
 * OrderPaymentPendingEvent 발생 시 감사 로그를 기록합니다.
 */
@Component
class OrderPaymentPendingEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentPending(event: OrderPaymentPendingEvent) {
        logger.info {
            "Handling OrderPaymentPendingEvent: orderId=${event.orderId}, paymentId=${event.paymentId}"
        }

        val metadata =
            mapOf(
                "paymentId" to event.paymentId.toString(),
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.PAYMENT_PENDING,
            changeSummary = "결제 대기 상태로 변경되었습니다. (결제ID: ${event.paymentId})",
            actorUserId = null, // 시스템 자동 처리
            metadata = metadata,
        )

        logger.debug { "OrderPaymentPendingEvent handled successfully: orderId=${event.orderId}" }
    }
}
