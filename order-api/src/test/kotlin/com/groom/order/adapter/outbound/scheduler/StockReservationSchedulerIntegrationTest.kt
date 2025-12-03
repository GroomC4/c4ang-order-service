package com.groom.order.adapter.outbound.scheduler

import com.groom.order.common.IntegrationTestBase
import com.groom.order.adapter.outbound.stock.StockReservationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.UUID

/**
 * StockReservationScheduler 통합 테스트
 *
 * 실제 Redis를 사용하여 만료된 재고 예약 정리를 검증합니다.
 *
 * 테스트 시나리오:
 * 1. Redis에 재고 예약 데이터 생성
 * 2. 스케줄러 수동 실행
 * 3. 만료된 예약이 정리되었는지 검증
 */
@DisplayName("재고 예약 스케줄러 통합 테스트")
class StockReservationSchedulerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var stockReservationScheduler: StockReservationScheduler

    @Autowired
    private lateinit var stockReservationService: StockReservationService

    @Autowired
    private lateinit var redissonClient: RedissonClient

    private val testProductId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
    private val testReservationId = "TEST-RESERVATION-001"

    @BeforeEach
    fun setUp() {
        // 테스트 전 Redis 정리
        redissonClient.keys.deleteByPattern("product:*")

        // ShedLock 정리 (스케줄러 락 해제)
        redissonClient.keys.deleteByPattern("job-lock:*")

        // 초기 재고 설정
        redissonClient.getAtomicLong("product:remaining-stock:$testProductId").set(100)
    }

    @Test
    @DisplayName("만료된 재고 예약을 자동으로 복구한다")
    fun `should restore expired stock reservations`() {
        // given: 만료된 재고 예약 생성
        val reservationKey = "product:reservation-stock:$testReservationId"
        val reservationData = "$testProductId:10" // 제품 ID:예약 수량

        // TTL을 1ms로 설정하여 즉시 만료되도록 함
        val bucket = redissonClient.getBucket<String>(reservationKey)
        bucket.set(reservationData, Duration.ofMillis(1))

        // 만료 인덱스에도 과거 시간으로 등록 (TTL 만료 후에도 재고 복구 가능하도록)
        val pastTimestamp =
            java.time.Instant
                .now()
                .minusSeconds(60)
                .epochSecond
                .toDouble()
        val expirySet = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
        expirySet.add(pastTimestamp, "$testReservationId:$reservationData")

        // 재고 차감 (예약 시뮬레이션)
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$testProductId")
        val originalStock = remainingStock.get()
        remainingStock.addAndGet(-10)

        // 만료 대기 (버킷 TTL 만료)
        Thread.sleep(100)

        val stockBeforeScheduler = remainingStock.get()
        assertEquals(originalStock - 10, stockBeforeScheduler, "재고가 10 감소해야 함")

        // when: 스케줄러 실행
        stockReservationScheduler.processExpiredReservations()

        // then: 재고가 복구되어야 함
        val stockAfterScheduler = remainingStock.get()
        assertEquals(originalStock, stockAfterScheduler, "재고가 원래대로 복구되어야 함")

        // 예약 정보가 삭제되어야 함
        val reservationAfter = bucket.get()
        assertNull(reservationAfter, "만료된 예약 정보가 삭제되어야 함")
    }

    @Test
    @DisplayName("만료되지 않은 예약은 그대로 유지한다")
    fun `should keep active reservations`() {
        // given: 유효한 재고 예약 생성
        val reservationKey = "product:reservation-stock:$testReservationId"
        val reservationData = "$testProductId:10"

        // TTL 10분으로 설정 (아직 만료되지 않음)
        val bucket = redissonClient.getBucket<String>(reservationKey)
        bucket.set(reservationData, Duration.ofMinutes(10))

        // 만료 인덱스에 미래 시간으로 등록 (아직 만료되지 않음)
        val futureTimestamp =
            java.time.Instant
                .now()
                .plusSeconds(600)
                .epochSecond
                .toDouble()
        val expirySet = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
        expirySet.add(futureTimestamp, "$testReservationId:$reservationData")

        // 재고 차감
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$testProductId")
        val originalStock = remainingStock.get()
        remainingStock.addAndGet(-10)

        // when: 스케줄러 실행
        stockReservationScheduler.processExpiredReservations()

        // then: 재고가 그대로 유지되어야 함
        val stockAfterScheduler = remainingStock.get()
        assertEquals(originalStock - 10, stockAfterScheduler, "유효한 예약의 재고는 변경되지 않아야 함")

        // 예약 정보가 유지되어야 함
        val reservationAfter = bucket.get()
        assertEquals(reservationData, reservationAfter, "유효한 예약 정보가 유지되어야 함")
    }

    @Test
    @DisplayName("예약이 없는 경우 스케줄러가 정상 종료된다")
    fun `should complete successfully when no reservations exist`() {
        // given: 예약 없음

        // when & then: 스케줄러 실행 (예외 없이 정상 종료)
        stockReservationScheduler.processExpiredReservations()
    }
}
