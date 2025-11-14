package com.groom.order.common.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Profile

/**
 * 테스트 환경 전용 Redisson 설정
 *
 * TestDockerComposeContainer에서 실행된 Redis에 동적으로 연결합니다.
 *
 * 프로덕션/개발 환경에서는 RedissonConfig가 사용됩니다.
 */
@Profile("test")
@Configuration
class TestRedissonConfig {
    @Bean
    @DependsOn("TestDockerComposeContainer")
    fun redissonClient(): RedissonClient {
        val config = Config()

        val host = TestDockerComposeContainer.getRedisHost()
        val port = TestDockerComposeContainer.getRedisMappedPort()

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
