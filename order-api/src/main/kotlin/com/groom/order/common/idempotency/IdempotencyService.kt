package com.groom.order.common.idempotency

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 멱등성 보장 서비스
 *
 * 중복 요청 방지를 위한 idempotency key 관리
 */
@Service
class IdempotencyService(
    private val redisTemplate: StringRedisTemplate,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 멱등성 확인 및 키 등록
     *
     * @param key idempotency key
     * @param ttl 키 유지 시간 (기본 24시간)
     * @return true: 처음 요청 (처리 가능), false: 중복 요청 (무시)
     */
    fun ensureIdempotency(
        key: String,
        ttl: Duration = Duration.ofHours(24),
    ): Boolean {
        val idempotencyKey = "idempotency:$key"

        return try {
            // SET NX EX: key가 없을 때만 설정 (원자적 연산)
            val result =
                redisTemplate.opsForValue().setIfAbsent(
                    idempotencyKey,
                    "1",
                    ttl,
                )

            if (result == false) {
                logger.info { "Duplicate request detected: $key" }
            }

            result ?: false
        } catch (e: Exception) {
            logger.error(e) { "Failed to check idempotency: $key" }
            // 예외 발생 시 안전하게 true 반환 (요청 허용)
            true
        }
    }

    /**
     * 멱등성 키에 주문 ID 저장 (Stripe 방식 - 기존 결과 캐싱)
     *
     * @param key idempotency key
     * @param orderId 생성된 주문 ID
     * @param ttl 키 유지 시간 (기본 24시간)
     */
    fun storeOrderId(
        key: String,
        orderId: String,
        ttl: Duration = Duration.ofHours(24),
    ) {
        val idempotencyKey = "idempotency:$key"
        try {
            redisTemplate.opsForValue().set(idempotencyKey, orderId, ttl)
            logger.debug { "Order ID stored for idempotency key: $key -> $orderId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to store order ID for idempotency key: $key" }
        }
    }

    /**
     * 멱등성 키로 저장된 주문 ID 조회
     *
     * @param key idempotency key
     * @return 저장된 주문 ID (없으면 null)
     */
    fun getOrderId(key: String): String? {
        val idempotencyKey = "idempotency:$key"
        return try {
            redisTemplate.opsForValue().get(idempotencyKey)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get order ID for idempotency key: $key" }
            null
        }
    }

    /**
     * idempotency key 삭제 (재시도 허용)
     */
    fun release(key: String) {
        val idempotencyKey = "idempotency:$key"
        try {
            redisTemplate.delete(idempotencyKey)
            logger.debug { "Idempotency key released: $key" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to release idempotency key: $key" }
        }
    }

    /**
     * idempotency key 존재 여부 확인
     */
    fun exists(key: String): Boolean {
        val idempotencyKey = "idempotency:$key"
        return redisTemplate.hasKey(idempotencyKey)
    }
}
