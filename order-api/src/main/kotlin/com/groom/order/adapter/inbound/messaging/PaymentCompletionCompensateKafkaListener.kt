package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.saga.event.avro.PaymentCompletionCompensate
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
 * 결제 완료 보상 이벤트 Kafka Listener (SAGA 보상)
 *
 * Product Service에서 재고 확정이 실패하면 Payment Service가 환불 처리 후
 * 이 이벤트를 발행합니다. Order Service는 주문을 취소합니다.
 *
 * 토픽: saga.payment-completion.compensate
 * Consumer Group: order-service-saga
 *
 * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
 */
@Component
class PaymentCompletionCompensateKafkaListener(
    private val orderEventHandler: OrderEventHandler,
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${kafka.topics.saga-payment-completion-compensate:saga.payment-completion.compensate}"],
        containerFactory = "sagaListenerContainerFactory",
    )
    fun onPaymentCompletionCompensate(
        record: ConsumerRecord<String, PaymentCompletionCompensate>,
        acknowledgment: Acknowledgment,
    ) {
        val event = record.value()
        val orderId = UUID.fromString(event.orderId)
        val paymentId = UUID.fromString(event.paymentId)

        logger.info {
            "Received PaymentCompletionCompensate event: " +
                "orderId=$orderId, paymentId=$paymentId, eventId=${event.eventId}, " +
                "reason=${event.compensationReason}, " +
                "partition=${record.partition()}, offset=${record.offset()}"
        }

        try {
            val compensatedAt =
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.compensatedAt),
                    ZoneId.systemDefault(),
                )

            orderEventHandler.handlePaymentCompletionCompensate(
                orderId = orderId,
                paymentId = paymentId,
                compensationReason = event.compensationReason,
                compensatedAt = compensatedAt,
            )

            acknowledgment.acknowledge()
            logger.info { "PaymentCompletionCompensate event processed successfully: orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process PaymentCompletionCompensate event: orderId=$orderId" }
            throw e // DefaultErrorHandler가 재시도 처리
        }
    }
}
