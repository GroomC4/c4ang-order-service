package com.groom.order.common.config

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

/**
 * Kafka Consumer 설정
 *
 * Avro 역직렬화를 사용하여 타입 안전한 이벤트 소비를 지원합니다.
 */
@EnableKafka
@Configuration
class KafkaConsumerConfig(
    private val properties: KafkaProducerProperties,
) {
    /**
     * Avro 이벤트용 Consumer Factory
     */
    @Bean
    fun avroConsumerFactory(): ConsumerFactory<String, Any> {
        val config =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100,
                ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000,
                ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10000,
                "schema.registry.url" to properties.schemaRegistry.url,
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
            )

        return DefaultKafkaConsumerFactory(config)
    }

    /**
     * Avro 이벤트용 Listener Container Factory
     *
     * Manual Commit 모드로 설정하여 이벤트 처리 완료 후 명시적으로 커밋합니다.
     */
    @Bean
    fun avroKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = avroConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3) // Consumer Thread 수
        return factory
    }
}
