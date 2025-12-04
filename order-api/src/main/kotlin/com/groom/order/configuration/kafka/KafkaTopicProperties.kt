package com.groom.order.configuration.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * Kafka 토픽 설정
 *
 * Order Service에서 발행하는 토픽 목록
 *
 * application.yml 예시:
 * ```yaml
 * kafka:
 *   topics:
 *     order-created: order.created
 *     order-confirmed: order.confirmed
 *     order-cancelled: order.cancelled
 * ```
 */
@ConfigurationProperties(prefix = "kafka.topics")
data class KafkaTopicProperties(
    @DefaultValue("order.created")
    val orderCreated: String = "order.created",
    @DefaultValue("order.confirmed")
    val orderConfirmed: String = "order.confirmed",
    @DefaultValue("order.cancelled")
    val orderCancelled: String = "order.cancelled",
)
