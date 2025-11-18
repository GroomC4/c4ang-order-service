package com.groom.order.infrastructure.kafka

import com.groom.ecommerce.store.event.avro.StoreDeleted
import com.groom.ecommerce.store.event.avro.StoreInfoUpdated
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Store 도메인 이벤트 리스너
 *
 * Store Service가 발행한 이벤트를 소비하여 주문 서비스의 비정규화된 데이터를 업데이트합니다.
 */
@Component
class StoreEventListener {
    /**
     * 스토어 정보 변경 이벤트 처리
     *
     * Store Service에서 스토어 정보가 변경되면 Order Service의 비정규화된 스토어 정보를 업데이트합니다.
     *
     * @param event StoreInfoUpdated 이벤트
     * @param acknowledgment Kafka Manual Commit
     */
    @KafkaListener(
        topics = ["\${kafka.topics.store-info-updated}"],
        groupId = "order-service",
        containerFactory = "avroKafkaListenerContainerFactory",
    )
    fun handleStoreInfoUpdated(
        @Payload event: StoreInfoUpdated,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.info {
                "Received StoreInfoUpdated event: storeId=${event.storeId}, " +
                    "storeName=${event.storeName}, updatedFields=${event.updatedFields}, " +
                    "partition=$partition, offset=$offset"
            }

            // TODO: 비정규화된 스토어 정보 업데이트 로직
            // 예: 주문 테이블의 스토어 정보를 업데이트하거나, 별도의 스토어 캐시를 갱신

            logger.info {
                "Successfully processed StoreInfoUpdated event: storeId=${event.storeId}"
            }

            // 성공적으로 처리 완료 후 Commit
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to process StoreInfoUpdated event: storeId=${event.storeId}, " +
                    "partition=$partition, offset=$offset"
            }
            // 에러 발생 시 재시도 또는 DLQ로 전송하는 로직 추가 가능
            throw e
        }
    }

    /**
     * 스토어 삭제 이벤트 처리
     *
     * Store Service에서 스토어가 삭제되면 관련 주문 데이터를 처리합니다.
     *
     * @param event StoreDeleted 이벤트
     * @param acknowledgment Kafka Manual Commit
     */
    @KafkaListener(
        topics = ["\${kafka.topics.store-deleted}"],
        groupId = "order-service",
        containerFactory = "avroKafkaListenerContainerFactory",
    )
    fun handleStoreDeleted(
        @Payload event: StoreDeleted,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.info {
                "Received StoreDeleted event: storeId=${event.storeId}, " +
                    "partition=$partition, offset=$offset"
            }

            // TODO: 스토어 삭제 처리 로직
            // 예:
            // 1. 해당 스토어의 진행 중인 주문 취소
            // 2. 비정규화된 스토어 정보 비활성화 처리
            // 3. 스토어 관련 캐시 무효화

            logger.info {
                "Successfully processed StoreDeleted event: storeId=${event.storeId}"
            }

            // 성공적으로 처리 완료 후 Commit
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to process StoreDeleted event: storeId=${event.storeId}, " +
                    "partition=$partition, offset=$offset"
            }
            throw e
        }
    }
}
