package com.groom.order.application.event

import com.groom.order.domain.event.OrderCreatedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventPublisher
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 생성 이벤트 핸들러
 *
 * OrderCreatedEvent 발생 시:
 * - 감사 로그 기록
 * - Kafka 이벤트 발행 (order.created)
 */
@Component
class OrderCreatedEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
    private val loadOrderPort: LoadOrderPort,
    private val orderEventPublisher: OrderEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        logger.info { "Handling OrderCreatedEvent: orderId=${event.orderId}, orderNumber=${event.orderNumber}" }

        // 1. 감사 로그 기록
        recordAuditLog(event)

        // 2. Kafka 이벤트 발행
        publishKafkaEvent(event)

        logger.debug { "OrderCreatedEvent handled successfully: orderId=${event.orderId}" }
    }

    private fun recordAuditLog(event: OrderCreatedEvent) {
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
    }

    private fun publishKafkaEvent(event: OrderCreatedEvent) {
        val order = loadOrderPort.loadById(event.orderId)
        if (order == null) {
            logger.error { "Order not found for Kafka publishing: orderId=${event.orderId}" }
            return
        }

        orderEventPublisher.publishOrderCreated(order)
        logger.debug { "OrderCreated Kafka event published: orderId=${event.orderId}" }
    }
}
