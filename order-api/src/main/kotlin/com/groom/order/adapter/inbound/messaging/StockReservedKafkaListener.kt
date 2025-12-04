package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.product.event.avro.StockReserved
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
 * 재고 예약 완료 이벤트 Kafka Listener
 *
 * Product Service에서 발행한 StockReserved 이벤트를 수신하여
 * 주문 상태를 업데이트합니다.
 *
 * 토픽: stock.reserved
 */
@Component
class StockReservedKafkaListener(
    private val orderEventHandler: OrderEventHandler,
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${kafka.topics.stock-reserved:stock.reserved}"],
        groupId = "\${kafka.consumer.group-id:order-service}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onStockReserved(
        record: ConsumerRecord<String, StockReserved>,
        acknowledgment: Acknowledgment,
    ) {
        val event = record.value()
        val orderId = UUID.fromString(event.orderId)

        logger.info {
            "Received StockReserved event: " +
                "orderId=$orderId, eventId=${event.eventId}, " +
                "partition=${record.partition()}, offset=${record.offset()}"
        }

        try {
            val reservedItems = event.reservedItems.map { item ->
                OrderEventHandler.ReservedItemInfo(
                    productId = UUID.fromString(item.productId),
                    quantity = item.quantity,
                    reservedStock = item.reservedStock,
                )
            }

            val reservedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.reservedAt),
                ZoneId.systemDefault(),
            )

            orderEventHandler.handleStockReserved(
                orderId = orderId,
                reservedItems = reservedItems,
                reservedAt = reservedAt,
            )

            acknowledgment.acknowledge()
            logger.info { "StockReserved event processed successfully: orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process StockReserved event: orderId=$orderId" }
            throw e // DefaultErrorHandler가 재시도 처리
        }
    }
}
