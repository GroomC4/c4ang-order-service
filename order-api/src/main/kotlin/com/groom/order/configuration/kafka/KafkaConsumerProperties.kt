package com.groom.order.configuration.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * Kafka Consumer 설정 프로퍼티
 *
 * Order Service의 Kafka Consumer 관련 설정을 관리합니다.
 *
 * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
 *
 * application.yml 예시:
 * ```yaml
 * kafka:
 *   bootstrap-servers: localhost:9092
 *   schema-registry:
 *     url: http://localhost:8081
 *   consumer:
 *     group-id: order-service
 *     saga-group-id: order-service-saga
 *     auto-offset-reset: earliest
 *     enable-auto-commit: false
 *     max-poll-records: 500
 * ```
 */
@ConfigurationProperties(prefix = "kafka")
data class KafkaConsumerProperties(
    @DefaultValue("localhost:9092")
    val bootstrapServers: String = "localhost:9092",

    val schemaRegistry: SchemaRegistryProperties = SchemaRegistryProperties(),

    val consumer: ConsumerProperties = ConsumerProperties(),
) {
    /**
     * Schema Registry 설정
     */
    data class SchemaRegistryProperties(
        @DefaultValue("http://localhost:8081")
        val url: String = "http://localhost:8081",
    )

    /**
     * Consumer 설정
     */
    data class ConsumerProperties(
        @DefaultValue("order-service")
        val groupId: String = "order-service",

        @DefaultValue("order-service-saga")
        val sagaGroupId: String = "order-service-saga",

        @DefaultValue("earliest")
        val autoOffsetReset: String = "earliest",

        @DefaultValue("false")
        val enableAutoCommit: Boolean = false,

        @DefaultValue("500")
        val maxPollRecords: Int = 500,
    )
}
