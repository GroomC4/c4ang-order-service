package com.groom.order.common

import com.groom.order.common.config.TestRedissonConfig
import com.groom.platform.testcontainers.annotation.IntegrationTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * 통합 테스트 기본 클래스
 *
 * @IntegrationTest: Kafka/Schema Registry 동적 포트 자동 주입
 * @SpringBootTest properties: Testcontainers 설정
 *
 * 모든 통합 테스트는 이 클래스를 상속받아야 합니다.
 */
@IntegrationTest
@SpringBootTest(
    properties = [
        // PostgreSQL Primary/Replica 활성화
        "testcontainers.postgres.enabled=true",
        "testcontainers.postgres.replica-enabled=true",

        // 스키마 파일 위치 - project: 스킴 사용 (IntelliJ/Gradle 모두 지원)
        "testcontainers.postgres.schema-location=project:sql/schema.sql",

        // Redis 활성화
        "testcontainers.redis.enabled=true",

        // Kafka 활성화
        "testcontainers.kafka.enabled=true",
    ]
)
@Import(TestRedissonConfig::class)
abstract class IntegrationTestBase
