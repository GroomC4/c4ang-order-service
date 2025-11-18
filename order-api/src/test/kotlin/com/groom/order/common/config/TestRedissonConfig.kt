package com.groom.order.common.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import org.testcontainers.containers.GenericContainer

/**
 * 테스트용 Redisson 설정
 *
 * Testcontainer Redis에 연결하도록 Redisson 클라이언트를 구성합니다.
 */
@TestConfiguration
class TestRedissonConfig : TestExecutionListener {
    @Bean
    @Primary
    fun redisson(redisContainer: GenericContainer<*>): RedissonClient {
        val config = Config()
        config.useSingleServer()
            .setAddress("redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}")
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(5)

        val client = Redisson.create(config)
        println("✅ Redisson Client configured for tests: ${redisContainer.host}:${redisContainer.getMappedPort(6379)}")
        return client
    }
}
