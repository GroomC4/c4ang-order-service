package com.groom.order.common.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 테스트 환경 전용 Redisson 설정
 *
 * testcontainers-starter에서 주입된 Redis 정보를 사용합니다.
 *
 * 프로덕션/개발 환경에서는 RedissonConfig가 사용됩니다.
 */
@Profile("test")
@Configuration
class TestRedissonConfig {
    @Value("\${spring.data.redis.host:localhost}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port:6379}")
    private var redisPort: Int = 6379

    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()

        config
            .useSingleServer()
            .setAddress("redis://$redisHost:$redisPort")
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(2)
            .setTimeout(3000) // 3초
            .setRetryAttempts(3)
            .setRetryInterval(1500) // 1.5초

        return Redisson.create(config)
    }
}
