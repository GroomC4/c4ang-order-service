package com.groom.order.infrastructure.kafka

import com.groom.ecommerce.order.event.avro.CancellationReason
import com.groom.ecommerce.order.event.avro.CancelledOrderItem
import com.groom.ecommerce.order.event.avro.OrderCancelled
import com.groom.ecommerce.order.event.avro.OrderConfirmed
import com.groom.ecommerce.order.event.avro.OrderCreated
import com.groom.ecommerce.order.event.avro.OrderItem
import com.groom.order.common.config.KafkaTopicConfig
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Order 도메인 이벤트 발행자
 *
 * Contract-Hub의 Avro 스키마를 사용하여 타입 안전한 이벤트를 발행합니다.
 */
@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val topicConfig: KafkaTopicConfig,
) {
    /**
     * 주문 생성 이벤트 발행
     *
     * @param order 생성된 주문
     */
    fun publishOrderCreated(order: Order): CompletableFuture<SendResult<String, Any>> {
        val event =
            OrderCreated
                .newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setOrderId(order.id.toString())
                .setUserId(order.userId.toString())
                .setStoreId(order.storeId.toString())
                .setItems(
                    order.items.map { item ->
                        OrderItem
                            .newBuilder()
                            .setProductId(item.productId.toString())
                            .setQuantity(item.quantity)
                            .setUnitPrice(item.unitPrice.toBigDecimalBytes())
                            .build()
                    },
                ).setTotalAmount(order.totalAmount.toBigDecimalBytes())
                .setCreatedAt(order.createdAt.toEpochMilli())
                .build()

        logger.info { "Publishing OrderCreated event: orderId=${order.id}" }

        return kafkaTemplate.send(topicConfig.orderCreated, order.id.toString(), event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) { "Failed to publish OrderCreated event: orderId=${order.id}" }
                } else {
                    logger.info {
                        "Successfully published OrderCreated event: " +
                            "orderId=${order.id}, topic=${result.recordMetadata.topic()}, " +
                            "partition=${result.recordMetadata.partition()}, offset=${result.recordMetadata.offset()}"
                    }
                }
            }
    }

    /**
     * 주문 확정 이벤트 발행
     *
     * @param order 확정된 주문
     */
    fun publishOrderConfirmed(order: Order): CompletableFuture<SendResult<String, Any>> {
        require(order.status == OrderStatus.STOCK_RESERVED) {
            "Order must be in STOCK_RESERVED status to confirm"
        }

        val event =
            OrderConfirmed
                .newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setOrderId(order.id.toString())
                .setUserId(order.userId.toString())
                .setTotalAmount(order.totalAmount.toBigDecimalBytes())
                .setConfirmedAt(order.updatedAt.toEpochMilli())
                .build()

        logger.info { "Publishing OrderConfirmed event: orderId=${order.id}" }

        return kafkaTemplate.send(topicConfig.orderConfirmed, order.id.toString(), event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) { "Failed to publish OrderConfirmed event: orderId=${order.id}" }
                } else {
                    logger.info {
                        "Successfully published OrderConfirmed event: orderId=${order.id}"
                    }
                }
            }
    }

    /**
     * 주문 취소 이벤트 발행
     *
     * @param order 취소된 주문
     */
    fun publishOrderCancelled(order: Order): CompletableFuture<SendResult<String, Any>> {
        require(order.status.isCancelled()) {
            "Order must be in cancelled status"
        }

        val cancellationReason =
            when (order.status) {
                OrderStatus.PAYMENT_TIMEOUT -> CancellationReason.PAYMENT_TIMEOUT
                OrderStatus.ORDER_CANCELLED -> CancellationReason.USER_REQUESTED
                else -> CancellationReason.SYSTEM_ERROR
            }

        val event =
            OrderCancelled
                .newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setOrderId(order.id.toString())
                .setUserId(order.userId.toString())
                .setItems(
                    order.items.map { item ->
                        CancelledOrderItem
                            .newBuilder()
                            .setProductId(item.productId.toString())
                            .setQuantity(item.quantity)
                            .build()
                    },
                ).setCancellationReason(cancellationReason)
                .setCancelledAt(order.updatedAt.toEpochMilli())
                .build()

        logger.info { "Publishing OrderCancelled event: orderId=${order.id}, reason=$cancellationReason" }

        return kafkaTemplate.send(topicConfig.orderCancelled, order.id.toString(), event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) { "Failed to publish OrderCancelled event: orderId=${order.id}" }
                } else {
                    logger.info {
                        "Successfully published OrderCancelled event: orderId=${order.id}"
                    }
                }
            }
    }

    /**
     * BigDecimal을 Avro의 decimal(10, 2) ByteBuffer로 변환
     */
    private fun BigDecimal.toBigDecimalBytes(): ByteBuffer {
        val unscaledValue = this.setScale(2, BigDecimal.ROUND_HALF_UP).unscaledValue()
        return ByteBuffer.wrap(unscaledValue.toByteArray())
    }
}
