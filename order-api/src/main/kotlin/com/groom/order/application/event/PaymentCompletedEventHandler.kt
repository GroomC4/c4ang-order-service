package com.groom.order.application.event

import com.groom.order.domain.event.PaymentCompletedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 결제 완료 이벤트 핸들러
 *
 * PaymentCompletedEvent 발생 시 감사 로그를 기록합니다.
 */
@Component("orderDomainPaymentCompletedEventHandler")
class PaymentCompletedEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        logger.info { "Handling PaymentCompletedEvent: orderId=${event.orderId}, paymentId=${event.paymentId}" }

        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "paymentId" to event.paymentId.toString(),
                "paymentAmount" to event.paymentAmount,
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.PAYMENT_COMPLETED,
            changeSummary = "결제가 완료되었습니다. (결제ID: ${event.paymentId}, 금액: ${event.paymentAmount}원)",
            actorUserId = null, // 시스템 자동 처리
            metadata = metadata,
        )

        logger.debug { "PaymentCompletedEvent handled successfully: orderId=${event.orderId}" }
    }
}
