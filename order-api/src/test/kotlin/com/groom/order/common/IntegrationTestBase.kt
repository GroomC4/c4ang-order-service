package com.groom.order.common

import com.groom.platform.testcontainers.annotation.IntegrationTest
import org.springframework.boot.test.context.SpringBootTest

/**
 * 통합 테스트 베이스 클래스
 *
 * **사용법:**
 * 1. 통합 테스트 클래스에서 이 클래스를 상속받기
 * 2. @SpringBootTest 어노테이션 제거 (중복 방지)
 * 3. 끝! Testcontainers 자동 시작됨
 *
 * **예시:**
 * ```kotlin
 * class OrderServiceIntegrationTest : IntegrationTestBase() {
 *     @Autowired
 *     private lateinit var orderRepository: OrderRepository
 *
 *     @Test
 *     fun `테스트`() { ... }
 * }
 * ```
 *
 * **platform-core testcontainers-starter 사용:**
 * - @IntegrationTest 어노테이션 중앙화
 * - PostgreSQL Primary/Replica, Redis, Kafka 자동 시작
 * - 동적 포트 주입 자동화
 */
@IntegrationTest
@SpringBootTest(
    properties = [
        // Spring Profile
        "spring.profiles.active=test",

        // PostgreSQL
        "testcontainers.postgres.enabled=true",
        "testcontainers.postgres.replica-enabled=true",
        "testcontainers.postgres.schema-location=project:sql/schema.sql",

        // Redis
        "testcontainers.redis.enabled=true",

        // Kafka
        "testcontainers.kafka.enabled=true",
        "testcontainers.kafka.auto-create-topics=true",

        // ========================================
        // Kafka Topics - Order Service (Producer)
        // ========================================
        "testcontainers.kafka.topics[0].name=order.created",
        "testcontainers.kafka.topics[0].partitions=3",
        "testcontainers.kafka.topics[0].replication-factor=1",

        "testcontainers.kafka.topics[1].name=order.confirmed",
        "testcontainers.kafka.topics[1].partitions=3",
        "testcontainers.kafka.topics[1].replication-factor=1",

        "testcontainers.kafka.topics[2].name=order.cancelled",
        "testcontainers.kafka.topics[2].partitions=3",
        "testcontainers.kafka.topics[2].replication-factor=1",

        // ========================================
        // Kafka Topics - Product Service (Consumer)
        // ========================================
        "testcontainers.kafka.topics[3].name=stock.reserved",
        "testcontainers.kafka.topics[3].partitions=3",
        "testcontainers.kafka.topics[3].replication-factor=1",

        "testcontainers.kafka.topics[4].name=stock.reservation.failed",
        "testcontainers.kafka.topics[4].partitions=1",
        "testcontainers.kafka.topics[4].replication-factor=1",

        "testcontainers.kafka.topics[5].name=stock.confirmed",
        "testcontainers.kafka.topics[5].partitions=3",
        "testcontainers.kafka.topics[5].replication-factor=1",

        "testcontainers.kafka.topics[6].name=stock.confirmation.failed",
        "testcontainers.kafka.topics[6].partitions=1",
        "testcontainers.kafka.topics[6].replication-factor=1",

        // ========================================
        // Kafka Topics - Payment Service (Consumer)
        // ========================================
        "testcontainers.kafka.topics[7].name=payment.completed",
        "testcontainers.kafka.topics[7].partitions=3",
        "testcontainers.kafka.topics[7].replication-factor=1",

        "testcontainers.kafka.topics[8].name=payment.failed",
        "testcontainers.kafka.topics[8].partitions=1",
        "testcontainers.kafka.topics[8].replication-factor=1",
    ],
)
abstract class IntegrationTestBase
