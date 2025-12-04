package com.groom.order.adapter.outbound.messaging

import com.groom.ecommerce.analytics.event.avro.DailyStatistics
import com.groom.ecommerce.analytics.event.avro.TopProduct
import com.groom.ecommerce.order.event.avro.CancellationReason
import com.groom.ecommerce.order.event.avro.CancelledOrderItem
import com.groom.ecommerce.order.event.avro.OrderCancelled
import com.groom.ecommerce.order.event.avro.OrderConfirmed
import com.groom.ecommerce.order.event.avro.OrderCreated
import com.groom.ecommerce.order.event.avro.OrderExpirationNotification
import com.groom.ecommerce.order.event.avro.OrderItem as AvroOrderItem
import com.groom.order.configuration.kafka.KafkaTopicProperties
import com.groom.order.domain.model.Order
import com.groom.order.domain.port.OrderEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Kafka를 통한 주문 이벤트 발행 어댑터
 *
 * contract-hub의 Avro 스키마로 생성된 클래스를 사용하여
 * 주문 관련 이벤트를 Kafka로 발행합니다.
 *
 * ## 발행 이벤트
 * - order.created: Product Service가 재고 예약 처리
 * - order.confirmed: Payment Service가 결제 대기 생성
 * - order.cancelled: Product Service가 재고 복원 처리
 * - order.expiration.notification: Notification Service가 고객 알림 발송
 * - daily.statistics: Analytics Service가 리포트 생성
 *
 * ## 파티션 키
 * 모든 이벤트는 `orderId`를 파티션 키로 사용하여
 * 동일 주문의 이벤트가 순서대로 처리되도록 보장합니다.
 */
@Component
@EnableConfigurationProperties(KafkaTopicProperties::class)
class KafkaOrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val topicProperties: KafkaTopicProperties,
) : OrderEventPublisher {
    private val logger = KotlinLogging.logger {}

    override fun publishOrderCreated(order: Order) {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val avroItems = order.items.map { item ->
            AvroOrderItem.newBuilder()
                .setProductId(item.productId.toString())
                .setQuantity(item.quantity)
                .setUnitPrice(item.unitPrice)
                .build()
        }

        val event = OrderCreated.newBuilder()
            .setEventId(eventId)
            .setEventTimestamp(now)
            .setOrderId(order.id.toString())
            .setUserId(order.userExternalId.toString())
            .setStoreId(order.storeId.toString())
            .setItems(avroItems)
            .setTotalAmount(order.calculateTotalAmount())
            .setCreatedAt(order.createdAt?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: now)
            .build()

        val topic = topicProperties.orderCreated
        val partitionKey = order.id.toString()

        kafkaTemplate.send(topic, partitionKey, event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) {
                        "Failed to publish OrderCreated event: orderId=${order.id}, topic=$topic"
                    }
                } else {
                    logger.info {
                        "Published OrderCreated event: orderId=${order.id}, eventId=$eventId, " +
                            "topic=$topic, partition=${result.recordMetadata.partition()}, " +
                            "offset=${result.recordMetadata.offset()}"
                    }
                }
            }
    }

    override fun publishOrderConfirmed(order: Order) {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val event = OrderConfirmed.newBuilder()
            .setEventId(eventId)
            .setEventTimestamp(now)
            .setOrderId(order.id.toString())
            .setUserId(order.userExternalId.toString())
            .setTotalAmount(order.calculateTotalAmount())
            .setConfirmedAt(order.confirmedAt?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: now)
            .build()

        val topic = topicProperties.orderConfirmed
        val partitionKey = order.id.toString()

        kafkaTemplate.send(topic, partitionKey, event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) {
                        "Failed to publish OrderConfirmed event: orderId=${order.id}, topic=$topic"
                    }
                } else {
                    logger.info {
                        "Published OrderConfirmed event: orderId=${order.id}, eventId=$eventId, " +
                            "topic=$topic, partition=${result.recordMetadata.partition()}, " +
                            "offset=${result.recordMetadata.offset()}"
                    }
                }
            }
    }

    override fun publishOrderCancelled(
        order: Order,
        cancellationReason: String?,
    ) {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val cancelledItems = order.items.map { item ->
            CancelledOrderItem.newBuilder()
                .setProductId(item.productId.toString())
                .setQuantity(item.quantity)
                .build()
        }

        val reason = mapToCancellationReason(cancellationReason)

        val event = OrderCancelled.newBuilder()
            .setEventId(eventId)
            .setEventTimestamp(now)
            .setOrderId(order.id.toString())
            .setUserId(order.userExternalId.toString())
            .setItems(cancelledItems)
            .setCancellationReason(reason)
            .setCancelledAt(order.cancelledAt?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: now)
            .build()

        val topic = topicProperties.orderCancelled
        val partitionKey = order.id.toString()

        kafkaTemplate.send(topic, partitionKey, event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) {
                        "Failed to publish OrderCancelled event: orderId=${order.id}, topic=$topic"
                    }
                } else {
                    logger.info {
                        "Published OrderCancelled event: orderId=${order.id}, eventId=$eventId, " +
                            "reason=$reason, topic=$topic, partition=${result.recordMetadata.partition()}, " +
                            "offset=${result.recordMetadata.offset()}"
                    }
                }
            }
    }

    override fun publishOrderExpirationNotification(
        orderId: UUID,
        userId: UUID,
        expirationReason: String,
        expiredAt: LocalDateTime,
    ) {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val event = OrderExpirationNotification.newBuilder()
            .setEventId(eventId)
            .setEventTimestamp(now)
            .setOrderId(orderId.toString())
            .setUserId(userId.toString())
            .setExpirationReason(expirationReason)
            .setExpiredAt(expiredAt.toInstant(ZoneOffset.UTC).toEpochMilli())
            .build()

        val topic = topicProperties.orderExpirationNotification
        val partitionKey = orderId.toString()

        kafkaTemplate.send(topic, partitionKey, event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) {
                        "Failed to publish OrderExpirationNotification event: orderId=$orderId, topic=$topic"
                    }
                } else {
                    logger.info {
                        "Published OrderExpirationNotification event: orderId=$orderId, eventId=$eventId, " +
                            "topic=$topic, partition=${result.recordMetadata.partition()}, " +
                            "offset=${result.recordMetadata.offset()}"
                    }
                }
            }
    }

    override fun publishDailyStatistics(statistics: OrderEventPublisher.DailyStatisticsData) {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val topProducts = statistics.topProducts.map { product ->
            TopProduct.newBuilder()
                .setProductId(product.productId.toString())
                .setProductName(product.productName)
                .setTotalSold(product.totalSold)
                .build()
        }

        val event = DailyStatistics.newBuilder()
            .setEventId(eventId)
            .setEventTimestamp(now)
            .setDate(statistics.date.toString())
            .setTotalOrders(statistics.totalOrders)
            .setTotalSales(statistics.totalSales)
            .setAvgOrderAmount(statistics.avgOrderAmount)
            .setTopProducts(topProducts)
            .setGeneratedAt(now)
            .build()

        val topic = topicProperties.dailyStatistics
        val partitionKey = statistics.date.toString()

        kafkaTemplate.send(topic, partitionKey, event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) {
                        "Failed to publish DailyStatistics event: date=${statistics.date}, topic=$topic"
                    }
                } else {
                    logger.info {
                        "Published DailyStatistics event: date=${statistics.date}, eventId=$eventId, " +
                            "totalOrders=${statistics.totalOrders}, totalSales=${statistics.totalSales}, " +
                            "topic=$topic, partition=${result.recordMetadata.partition()}, " +
                            "offset=${result.recordMetadata.offset()}"
                    }
                }
            }
    }

    private fun mapToCancellationReason(reason: String?): CancellationReason {
        return when {
            reason == null -> CancellationReason.USER_REQUESTED
            reason.contains("timeout", ignoreCase = true) -> CancellationReason.PAYMENT_TIMEOUT
            reason.contains("stock", ignoreCase = true) ||
                reason.contains("재고", ignoreCase = true) -> CancellationReason.STOCK_UNAVAILABLE
            reason.contains("user", ignoreCase = true) ||
                reason.contains("사용자", ignoreCase = true) ||
                reason.contains("고객", ignoreCase = true) -> CancellationReason.USER_REQUESTED
            else -> CancellationReason.SYSTEM_ERROR
        }
    }
}
