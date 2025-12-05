package com.groom.order.configuration.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * Kafka 토픽 설정
 *
 * Order Service에서 발행/소비하는 토픽 목록
 *
 * application.yml 예시:
 * ```yaml
 * kafka:
 *   topics:
 *     # Producer Topics
 *     order-created: order.created
 *     order-confirmed: order.confirmed
 *     order-cancelled: order.cancelled
 *     order-expiration-notification: order.expiration.notification
 *     daily-statistics: daily.statistics
 *     # Consumer Topics
 *     stock-reserved: stock.reserved
 *     payment-completed: payment.completed
 *     payment-failed: payment.failed
 *     payment-cancelled: payment.cancelled
 * ```
 */
@ConfigurationProperties(prefix = "kafka.topics")
data class KafkaTopicProperties(
    // ===== Producer Topics =====
    @DefaultValue("order.created")
    val orderCreated: String = "order.created",
    @DefaultValue("order.confirmed")
    val orderConfirmed: String = "order.confirmed",
    @DefaultValue("order.cancelled")
    val orderCancelled: String = "order.cancelled",
    @DefaultValue("order.expiration.notification")
    val orderExpirationNotification: String = "order.expiration.notification",
    @DefaultValue("daily.statistics")
    val dailyStatistics: String = "daily.statistics",
    // ===== Consumer Topics =====
    @DefaultValue("stock.reserved")
    val stockReserved: String = "stock.reserved",
    @DefaultValue("payment.completed")
    val paymentCompleted: String = "payment.completed",
    @DefaultValue("payment.failed")
    val paymentFailed: String = "payment.failed",
    @DefaultValue("payment.cancelled")
    val paymentCancelled: String = "payment.cancelled",
)
