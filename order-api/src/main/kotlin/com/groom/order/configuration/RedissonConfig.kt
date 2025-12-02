package com.groom.order.configuration

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Redisson 설정 (프로덕션/개발 환경)
 *
 * 테스트 환경에서는 TestRedissonConfig가 사용됩니다.
 *
 * Redisson은 Redis 기반의 고수준 클라이언트로, 원자적 연산을 Java/Kotlin API로 제공합니다.
 *
 * 주요 기능:
 * - RAtomicLong: 원자적 숫자 연산
 * - RBucket: TTL 지원 Key-Value
 * - RScoredSortedSet: Sorted Set
 * - RBatch: 여러 명령어를 원자적으로 실행 (내부적으로 Lua 스크립트 사용)
 *
 * 재고 예약 시스템:
 * - stock:{storeId}:{productId} → RAtomicLong
 * - reservation:{reservationId} → RBucket
 * - reservation:expiry → RScoredSortedSet
 */
@Profile("!test")
@Configuration
class RedissonConfig {
    @Bean
    fun redissonClient(
        @Value("\${spring.redis.host:localhost}") host: String,
        @Value("\${spring.redis.port:6379}") port: Int,
    ): RedissonClient {
        val config = Config()

        config
            .useSingleServer()
            .setAddress("redis://$host:$port")
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(2)
            .setTimeout(3000) // 3초
            .setRetryAttempts(3)
            .setRetryInterval(1500) // 1.5초

        return Redisson.create(config)
    }
}
