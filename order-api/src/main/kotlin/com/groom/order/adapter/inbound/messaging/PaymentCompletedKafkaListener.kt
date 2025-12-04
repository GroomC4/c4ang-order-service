package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.payment.event.avro.PaymentCompleted
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
 * 결제 완료 이벤트 Kafka Listener
 *
 * Payment Service에서 발행한 PaymentCompleted 이벤트를 수신하여
 * 주문 상태를 업데이트합니다.
 *
 * 토픽: payment.completed
 */
@Component
class PaymentCompletedKafkaListener(
    private val orderEventHandler: OrderEventHandler,
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${kafka.topics.payment-completed:payment.completed}"],
        groupId = "\${kafka.consumer.group-id:order-service}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentCompleted(
        record: ConsumerRecord<String, PaymentCompleted>,
        acknowledgment: Acknowledgment,
    ) {
        val event = record.value()
        val orderId = UUID.fromString(event.orderId)
        val paymentId = UUID.fromString(event.paymentId)

        logger.info {
            "Received PaymentCompleted event: " +
                "orderId=$orderId, paymentId=$paymentId, eventId=${event.eventId}, " +
                "partition=${record.partition()}, offset=${record.offset()}"
        }

        try {
            // Avro의 decimal 타입은 자동으로 BigDecimal로 변환됨
            val totalAmount = event.totalAmount
            val completedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.completedAt),
                ZoneId.systemDefault(),
            )

            orderEventHandler.handlePaymentCompleted(
                orderId = orderId,
                paymentId = paymentId,
                totalAmount = totalAmount,
                completedAt = completedAt,
            )

            acknowledgment.acknowledge()
            logger.info { "PaymentCompleted event processed successfully: orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process PaymentCompleted event: orderId=$orderId" }
            throw e
        }
    }
}
