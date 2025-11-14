package com.groom.order.application.event

import com.groom.order.domain.event.OrderCreatedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 생성 이벤트 핸들러
 *
 * OrderCreatedEvent 발생 시 감사 로그를 기록합니다.
 */
@Component
class OrderCreatedEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        logger.info { "Handling OrderCreatedEvent: orderId=${event.orderId}, orderNumber=${event.orderNumber}" }

        val metadata =
            mapOf(
                "orderNumber" to event.orderNumber,
                "storeId" to event.storeId.toString(),
                "totalAmount" to event.totalAmount.toString(),
                "occurredAt" to event.occurredAt.toString(),
            )

        orderAuditRecorder.record(
            orderId = event.orderId,
            eventType = OrderAuditEventType.ORDER_CREATED,
            changeSummary = "주문이 생성되었습니다. (주문번호: ${event.orderNumber}, 총액: ${event.totalAmount}원)",
            actorUserId = event.userExternalId,
            metadata = metadata,
        )

        logger.debug { "OrderCreatedEvent handled successfully: orderId=${event.orderId}" }
    }
}
