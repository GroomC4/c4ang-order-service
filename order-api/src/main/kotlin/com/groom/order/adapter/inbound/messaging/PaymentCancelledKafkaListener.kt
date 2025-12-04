package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.payment.event.avro.PaymentCancelled
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
 * 결제 취소 이벤트 Kafka Listener
 *
 * Payment Service에서 발행한 PaymentCancelled 이벤트를 수신하여
 * 주문을 취소하고 재고 복원 이벤트를 발행합니다.
 *
 * 토픽: payment.cancelled
 */
@Component
class PaymentCancelledKafkaListener(
    private val orderEventHandler: OrderEventHandler,
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${kafka.topics.payment-cancelled:payment.cancelled}"],
        groupId = "\${kafka.consumer.group-id:order-service}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentCancelled(
        record: ConsumerRecord<String, PaymentCancelled>,
        acknowledgment: Acknowledgment,
    ) {
        val event = record.value()
        val orderId = UUID.fromString(event.orderId)
        val paymentId = UUID.fromString(event.paymentId)

        logger.info {
            "Received PaymentCancelled event: " +
                "orderId=$orderId, paymentId=$paymentId, eventId=${event.eventId}, " +
                "reason=${event.cancellationReason}, " +
                "partition=${record.partition()}, offset=${record.offset()}"
        }

        try {
            val cancelledAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.cancelledAt),
                ZoneId.systemDefault(),
            )

            orderEventHandler.handlePaymentCancelled(
                orderId = orderId,
                paymentId = paymentId,
                cancellationReason = event.cancellationReason.name,
                cancelledAt = cancelledAt,
            )

            acknowledgment.acknowledge()
            logger.info { "PaymentCancelled event processed successfully: orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process PaymentCancelled event: orderId=$orderId" }
            throw e
        }
    }
}
