package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.saga.event.avro.StockReservationFailed
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
 * 재고 예약 실패 이벤트 Kafka Listener (SAGA 보상)
 *
 * Product Service에서 발행한 StockReservationFailed 이벤트를 수신하여
 * 주문을 취소합니다.
 *
 * 토픽: saga.stock-reservation.failed
 * Consumer Group: order-service-saga
 *
 * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
 */
@Component
class StockReservationFailedKafkaListener(
    private val orderEventHandler: OrderEventHandler,
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${kafka.topics.saga-stock-reservation-failed:saga.stock-reservation.failed}"],
        containerFactory = "sagaListenerContainerFactory",
    )
    fun onStockReservationFailed(
        record: ConsumerRecord<String, StockReservationFailed>,
        acknowledgment: Acknowledgment,
    ) {
        val event = record.value()
        val orderId = UUID.fromString(event.orderId)

        logger.info {
            "Received StockReservationFailed event: " +
                "orderId=$orderId, eventId=${event.eventId}, " +
                "reason=${event.failureReason}, " +
                "partition=${record.partition()}, offset=${record.offset()}"
        }

        try {
            val failedItems =
                event.failedItems.map { item ->
                    OrderEventHandler.FailedItemInfo(
                        productId = UUID.fromString(item.productId),
                        requestedQuantity = item.requestedQuantity,
                        availableStock = item.availableStock,
                    )
                }

            val failedAt =
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.failedAt),
                    ZoneId.systemDefault(),
                )

            orderEventHandler.handleStockReservationFailed(
                orderId = orderId,
                failedItems = failedItems,
                failureReason = event.failureReason,
                failedAt = failedAt,
            )

            acknowledgment.acknowledge()
            logger.info { "StockReservationFailed event processed successfully: orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process StockReservationFailed event: orderId=$orderId" }
            throw e // DefaultErrorHandler가 재시도 처리
        }
    }
}
