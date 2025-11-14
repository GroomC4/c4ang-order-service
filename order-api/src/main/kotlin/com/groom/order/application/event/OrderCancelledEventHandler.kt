package com.groom.order.application.event

import com.groom.order.domain.event.OrderCancelledEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 취소 이벤트 핸들러
 *
 * OrderCancelledEvent 발생 시 감사 로그를 기록합니다.
 */
@Component
class OrderCancelledEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCancelled(event: OrderCancelledEvent) {
        logger.info { "Handling OrderCancelledEvent: orderId=${event.orderId}, reason=${event.cancelReason}" }

        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "cancelReason" to (event.cancelReason ?: "사유 없음"),
                "cancelledAt" to event.cancelledAt.toString(),
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.ORDER_CANCELLED,
            changeSummary = "주문이 취소되었습니다. (사유: ${event.cancelReason ?: "사유 없음"})",
            actorUserId = event.userExternalId,
            metadata = metadata,
        )

        logger.debug { "OrderCancelledEvent handled successfully: orderId=${event.orderId}" }
    }
}
