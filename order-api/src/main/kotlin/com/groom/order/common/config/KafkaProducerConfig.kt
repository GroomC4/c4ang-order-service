package com.groom.order.common.config

import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.stereotype.Component

/**
 * Kafka Producer 설정 프로퍼티
 */
@Component
@ConfigurationProperties(prefix = "kafka")
data class KafkaProducerProperties(
    var bootstrapServers: String = "localhost:9092",
    var producer: ProducerProps = ProducerProps(),
    var schemaRegistry: SchemaRegistryProps = SchemaRegistryProps(),
) {
    data class ProducerProps(
        var acks: String = "all",
        var retries: Int = 3,
        var batchSize: Int = 16384,
        var lingerMs: Int = 10,
        var bufferMemory: Long = 33554432,
        var maxInFlightRequestsPerConnection: Int = 5,
        var enableIdempotence: Boolean = true,
        var compressionType: String = "snappy",
        var keySerializer: String = "org.apache.kafka.common.serialization.StringSerializer",
        var valueSerializer: String = "io.confluent.kafka.serializers.KafkaAvroSerializer",
    )

    data class SchemaRegistryProps(
        var url: String = "http://localhost:8081",
    )
}

/**
 * Kafka Producer 설정
 *
 * Avro 직렬화를 사용하여 타입 안전한 이벤트 발행을 지원합니다.
 */
@Configuration
class KafkaProducerConfig(
    private val properties: KafkaProducerProperties,
) {
    /**
     * Avro 이벤트용 Producer Factory
     */
    @Bean
    fun <T> avroProducerFactory(): ProducerFactory<String, T> {
        val config =
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to properties.producer.acks,
                ProducerConfig.RETRIES_CONFIG to properties.producer.retries,
                ProducerConfig.BATCH_SIZE_CONFIG to properties.producer.batchSize,
                ProducerConfig.LINGER_MS_CONFIG to properties.producer.lingerMs,
                ProducerConfig.BUFFER_MEMORY_CONFIG to properties.producer.bufferMemory,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to properties.producer.maxInFlightRequestsPerConnection,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to properties.producer.enableIdempotence,
                ProducerConfig.COMPRESSION_TYPE_CONFIG to properties.producer.compressionType,
                "schema.registry.url" to properties.schemaRegistry.url,
            )

        return DefaultKafkaProducerFactory(config)
    }

    /**
     * Avro 이벤트용 KafkaTemplate
     */
    @Bean
    fun <T> avroKafkaTemplate(): KafkaTemplate<String, T> = KafkaTemplate(avroProducerFactory())
}
