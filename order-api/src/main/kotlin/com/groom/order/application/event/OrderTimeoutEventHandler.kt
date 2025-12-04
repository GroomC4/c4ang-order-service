package com.groom.order.application.event

import com.groom.order.domain.event.OrderTimeoutEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventPublisher
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 타임아웃 이벤트 핸들러
 *
 * OrderTimeoutEvent 발생 시:
 * - 감사 로그 기록
 * - Kafka 이벤트 발행 (order.cancelled, order.expiration.notification)
 */
@Component
class OrderTimeoutEventHandler(
    private val orderAuditRecorder: OrderAuditRecorder,
    private val loadOrderPort: LoadOrderPort,
    private val orderEventPublisher: OrderEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val DEFAULT_EXPIRATION_REASON = "결제 시간 초과"
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderTimeout(event: OrderTimeoutEvent) {
        logger.info { "Handling OrderTimeoutEvent: orderId=${event.orderId}, reservationId=${event.reservationId}" }

        // 1. 감사 로그 기록
        recordAuditLog(event)

        // 2. Kafka 이벤트 발행
        publishKafkaEvents(event)

        logger.debug { "OrderTimeoutEvent handled successfully: orderId=${event.orderId}" }
    }

    private fun recordAuditLog(event: OrderTimeoutEvent) {
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
    }

    private fun publishKafkaEvents(event: OrderTimeoutEvent) {
        val order = loadOrderPort.loadById(event.orderId)
        if (order == null) {
            logger.error { "Order not found for Kafka publishing: orderId=${event.orderId}" }
            return
        }

        // 1. order.cancelled 이벤트 발행 (재고 복원을 위해)
        orderEventPublisher.publishOrderCancelled(order, "Payment timeout")

        // 2. order.expiration.notification 이벤트 발행 (고객 알림을 위해)
        orderEventPublisher.publishOrderExpirationNotification(
            orderId = event.orderId,
            userId = order.userExternalId,
            expirationReason = DEFAULT_EXPIRATION_REASON,
            expiredAt = event.timeoutAt,
        )

        logger.debug { "OrderTimeout Kafka events published: orderId=${event.orderId}" }
    }
}
