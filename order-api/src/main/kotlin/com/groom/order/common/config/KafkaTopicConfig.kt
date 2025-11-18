package com.groom.order.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Kafka Topic 설정
 *
 * application.yml에서 kafka.topics 하위 설정을 읽어옵니다.
 */
@Component
@ConfigurationProperties(prefix = "kafka.topics")
data class KafkaTopicConfig(
    // Order 이벤트 (Producer)
    var orderCreated: String = "order.created",
    var orderConfirmed: String = "order.confirmed",
    var orderCancelled: String = "order.cancelled",
    // Store 이벤트 (Consumer)
    var storeInfoUpdated: String = "store.info.updated",
    var storeDeleted: String = "store.deleted",
)
