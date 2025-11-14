package com.groom.order.application.event

import com.groom.order.domain.event.PaymentRequestedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 결제 요청 이벤트 핸들러
 *
 * PaymentRequestedEvent 발생 시 감사 로그를 기록합니다.
 */
@Component
class PaymentRequestedEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentRequested(event: PaymentRequestedEvent) {
        logger.info { "Handling PaymentRequestedEvent: orderId=${event.orderId}" }

        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "paymentAmount" to event.paymentAmount,
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.PAYMENT_REQUESTED,
            changeSummary = "결제가 요청되었습니다. (금액: ${event.paymentAmount}원)",
            actorUserId = null, // 시스템 자동 처리
            metadata = metadata,
        )

        logger.debug { "PaymentRequestedEvent handled successfully: orderId=${event.orderId}" }
    }
}
