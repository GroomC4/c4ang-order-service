package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.payment.event.avro.PaymentFailed
import com.groom.order.domain.port.OrderEventHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * 결제 실패 이벤트 Kafka Listener
 *
 * Payment Service에서 발행한 PaymentFailed 이벤트를 수신하여
 * 주문을 취소하고 재고 복원 이벤트를 발행합니다.
 *
 * 토픽: payment.failed
 */
@Component
class PaymentFailedKafkaListener(
    private val orderEventHandler: OrderEventHandler,
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${kafka.topics.payment-failed:payment.failed}"],
        groupId = "\${kafka.consumer.group-id:order-service}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentFailed(
        record: ConsumerRecord<String, PaymentFailed>,
        acknowledgment: Acknowledgment,
    ) {
        val event = record.value()
        val orderId = UUID.fromString(event.orderId)
        val paymentId = UUID.fromString(event.paymentId)

        logger.info {
            "Received PaymentFailed event: " +
                "orderId=$orderId, paymentId=$paymentId, eventId=${event.eventId}, " +
                "reason=${event.failureReason}, " +
                "partition=${record.partition()}, offset=${record.offset()}"
        }

        try {
            val failedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.failedAt),
                ZoneId.systemDefault(),
            )

            orderEventHandler.handlePaymentFailed(
                orderId = orderId,
                paymentId = paymentId,
                failureReason = event.failureReason,
                failedAt = failedAt,
            )

            acknowledgment.acknowledge()
            logger.info { "PaymentFailed event processed successfully: orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process PaymentFailed event: orderId=$orderId" }
            throw e
        }
    }
}
