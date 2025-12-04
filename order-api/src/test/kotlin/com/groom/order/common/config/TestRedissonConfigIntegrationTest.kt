package com.groom.order.common.config

import com.groom.order.common.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.UUID

/**
 * TestRedissonConfig 통합 테스트
 *
 * Docker Compose 환경의 Redis를 사용하여
 * Redisson이 정상적으로 연결되고 작동하는지 검증합니다.
 */
@Disabled("구현확인을 위한 일회성 테스트지만 이후 필요할수도 있어 비활성화만 해둠")
class TestRedissonConfigIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var redissonClient: RedissonClient

    @Test
    fun `RedissonClient가 정상적으로 주입되어야 한다`() {
        assertThat(redissonClient).isNotNull
    }

    @Test
    fun `RAtomicLong을 사용한 원자적 연산이 작동해야 한다`() {
        // Given
        val key = "test:atomic:${UUID.randomUUID()}"
        val atomicLong = redissonClient.getAtomicLong(key)

        // When
        atomicLong.set(100)
        val decremented = atomicLong.addAndGet(-10)

        // Then
        assertThat(decremented).isEqualTo(90)
        assertThat(atomicLong.get()).isEqualTo(90)

        // Cleanup
        atomicLong.delete()
    }

    @Test
    fun `RBucket을 사용한 TTL 키-밸류 저장이 작동해야 한다`() {
        // Given
        val key = "test:bucket:${UUID.randomUUID()}"
        val bucket = redissonClient.getBucket<String>(key)

        // When
        bucket.set("test-value", Duration.ofSeconds(10))
        val value = bucket.get()

        // Then
        assertThat(value).isEqualTo("test-value")

        // Cleanup
        bucket.delete()
    }

    @Test
    fun `RScoredSortedSet을 사용한 정렬된 집합이 작동해야 한다`() {
        // Given
        val key = "test:zset:${UUID.randomUUID()}"
        val scoredSet = redissonClient.getScoredSortedSet<String>(key)

        // When
        scoredSet.add(100.0, "item1")
        scoredSet.add(200.0, "item2")
        scoredSet.add(150.0, "item3")

        // Then
        val items = scoredSet.valueRange(0.0, true, 200.0, true).toList()
        assertThat(items).hasSize(3)
        assertThat(items[0]).isEqualTo("item1") // score 100
        assertThat(items[1]).isEqualTo("item3") // score 150
        assertThat(items[2]).isEqualTo("item2") // score 200

        // Cleanup
        scoredSet.delete()
    }

    @Test
    fun `RBatch를 사용한 원자적 배치 실행이 작동해야 한다`() {
        // Given
        val key1 = "test:batch1:${UUID.randomUUID()}"
        val key2 = "test:batch2:${UUID.randomUUID()}"
        val batch = redissonClient.createBatch()

        // When
        batch.getAtomicLong(key1).setAsync(100)
        batch.getAtomicLong(key2).setAsync(200)
        val results = batch.execute()

        // Then
        assertThat(results.responses).hasSize(2)

        val atomicLong1 = redissonClient.getAtomicLong(key1)
        val atomicLong2 = redissonClient.getAtomicLong(key2)
        assertThat(atomicLong1.get()).isEqualTo(100)
        assertThat(atomicLong2.get()).isEqualTo(200)

        // Cleanup
        atomicLong1.delete()
        atomicLong2.delete()
    }
}
