package com.groom.order.configuration.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka Consumer 설정
 *
 * Order Service에서 소비하는 이벤트:
 * - stock.reserved: 재고 예약 완료 (Product Service) → 주문 확정 처리
 * - payment.completed: 결제 완료 (Payment Service) → 재고 확정 요청
 * - payment.failed: 결제 실패 (Payment Service) → 주문 취소 처리
 * - saga.stock-reservation.failed: 재고 예약 실패 (Product Service) → 주문 취소 처리
 * - saga.payment-initialization.failed: 결제 대기 생성 실패 (Payment Service) → 주문 취소 처리
 * - saga.payment-completion.compensate: 결제 보상 (Payment Service) → 주문 취소 처리
 *
 * Avro 역직렬화를 사용하며, Schema Registry와 연동됩니다.
 *
 * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
 *
 * TODO: Consumer Group 분리 검토 (docs/TODO_KAFKA_EVENT_ALIGNMENT.md Phase 4 참고)
 *   - order-service-saga-compensation: SAGA 보상 이벤트 처리
 *   - order-service-saga-payment: Payment Saga 이벤트 처리
 */
@Configuration
@EnableKafka
class KafkaConsumerConfig(
    @Value("\${kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${kafka.schema-registry.url}") private val schemaRegistryUrl: String,
    @Value("\${kafka.consumer.group-id:order-service}") private val groupId: String,
    @Value("\${kafka.consumer.auto-offset-reset:earliest}") private val autoOffsetReset: String,
    @Value("\${kafka.consumer.enable-auto-commit:false}") private val enableAutoCommit: Boolean,
    @Value("\${kafka.consumer.max-poll-records:500}") private val maxPollRecords: Int,
) {
    @Bean
    fun consumerFactory(): ConsumerFactory<String, SpecificRecord> {
        val configProps =
            mutableMapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to enableAutoCommit,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecords,
                // Schema Registry 설정
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryUrl,
                // SpecificRecord로 역직렬화 (GenericRecord 대신)
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
            )
        return DefaultKafkaConsumerFactory(configProps)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, SpecificRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, SpecificRecord>()
        factory.consumerFactory = consumerFactory()

        // 수동 커밋 모드 (AckMode.MANUAL_IMMEDIATE)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE

        // 에러 핸들러 설정 (3회 재시도, 1초 간격)
        factory.setCommonErrorHandler(
            DefaultErrorHandler(
                FixedBackOff(1000L, 3L),
            ),
        )

        // 동시성 설정 (파티션 수에 맞게 조정)
        factory.setConcurrency(3)

        return factory
    }
}
