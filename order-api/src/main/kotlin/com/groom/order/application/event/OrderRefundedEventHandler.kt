package com.groom.order.application.event

import com.groom.order.domain.event.OrderRefundedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 환불 이벤트 핸들러
 *
 * OrderRefundedEvent 발생 시 감사 로그를 기록합니다.
 */
@Component
class OrderRefundedEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderRefunded(event: OrderRefundedEvent) {
        logger.info { "Handling OrderRefundedEvent: orderId=${event.orderId}, refundId=${event.refundId}" }

        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "refundId" to event.refundId,
                "refundAmount" to event.refundAmount,
                "refundReason" to (event.refundReason ?: "사유 없음"),
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.ORDER_REFUNDED,
            changeSummary = "주문이 환불되었습니다. (환불ID: ${event.refundId}, 금액: ${event.refundAmount}원, 사유: ${event.refundReason ?: "사유 없음"})",
            actorUserId = event.userExternalId,
            metadata = metadata,
        )

        logger.debug { "OrderRefundedEvent handled successfully: orderId=${event.orderId}" }
    }
}
