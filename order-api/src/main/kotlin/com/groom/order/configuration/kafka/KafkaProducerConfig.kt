package com.groom.order.configuration.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

/**
 * Kafka Producer 설정
 *
 * Order Service에서 발행하는 이벤트:
 * - order.created: 주문 생성 이벤트 (Product Service가 재고 예약 처리)
 * - order.confirmed: 주문 확정 이벤트 (Payment Service가 결제 대기 생성)
 * - order.cancelled: 주문 취소 이벤트 (Product Service가 재고 복원 처리)
 *
 * Avro 직렬화를 사용하며, Schema Registry와 연동됩니다.
 */
@Configuration
class KafkaProducerConfig(
    @Value("\${kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${kafka.schema-registry.url}") private val schemaRegistryUrl: String,
    @Value("\${kafka.producer.acks:all}") private val acks: String,
    @Value("\${kafka.producer.retries:3}") private val retries: Int,
    @Value("\${kafka.producer.enable-idempotence:true}") private val enableIdempotence: Boolean,
) {
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps =
            mutableMapOf<String, Any>(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to acks,
                ProducerConfig.RETRIES_CONFIG to retries,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to enableIdempotence,
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryUrl,
                // Schema Registry에 스키마 자동 등록
                KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS to true,
            )
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(producerFactory())
}
