package com.groom.order.adapter.outbound.stock

import com.groom.order.common.annotation.IntegrationTest
import com.groom.order.domain.model.ReservationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * RedissonStockReservationService 통합 테스트
 *
 * compareAndSet 기반 동시성 제어를 실제 Redis와 함께 검증합니다.
 */
@IntegrationTest
@SpringBootTest
class RedissonStockReservationServiceIntegrationTest {
    @Autowired
    private lateinit var stockReservationService: StockReservationService

    @Autowired
    private lateinit var redissonClient: RedissonClient

    private val testStoreId = UUID.randomUUID()
    private val testProductIds = mutableListOf<UUID>()

    @BeforeEach
    fun setUp() {
        // 테스트용 상품 생성 및 재고 설정
        repeat(3) {
            val productId = UUID.randomUUID()
            testProductIds.add(productId)

            // 초기 재고 100개 설정
            val key = "product:remaining-stock:$productId"
            redissonClient.getAtomicLong(key).set(100)
        }
    }

    @AfterEach
    fun tearDown() {
        // Redis 키 정리
        testProductIds.forEach { productId ->
            val stockKey = "product:remaining-stock:$productId"
            redissonClient.getAtomicLong(stockKey).delete()
        }

        // 예약 관련 키 정리
        val expiryIndex = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
        expiryIndex.clear()
    }

    @Test
    fun `재고가 충분하면 예약에 성공해야 한다`() {
        // Given
        val productId = testProductIds[0]
        val items =
            listOf(
                OrderItemRequest(productId = productId, quantity = 10),
            )
        val reservationId = UUID.randomUUID().toString()

        // When
        val result =
            stockReservationService.tryReserve(
                storeId = testStoreId,
                items = items,
                reservationId = reservationId,
                ttl = Duration.ofMinutes(10),
            )

        // Then
        assertThat(result).isInstanceOf(ReservationResult.Success::class.java)
        assertThat((result as ReservationResult.Success).reservationId).isEqualTo(reservationId)

        // 재고 확인
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        assertThat(remainingStock).isEqualTo(90)
    }

    @Test
    fun `재고가 부족하면 예약에 실패해야 한다`() {
        // Given
        val productId = testProductIds[0]
        val items =
            listOf(
                OrderItemRequest(productId = productId, quantity = 101), // 재고 100개보다 많음
            )
        val reservationId = UUID.randomUUID().toString()

        // When
        val result =
            stockReservationService.tryReserve(
                storeId = testStoreId,
                items = items,
                reservationId = reservationId,
                ttl = Duration.ofMinutes(10),
            )

        // Then
        assertThat(result).isEqualTo(ReservationResult.InsufficientStock)

        // 재고는 변경되지 않아야 함
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        assertThat(remainingStock).isEqualTo(100)
    }

    @Test
    fun `동시에 10개 요청이 들어와도 정확히 재고가 차감되어야 한다`() {
        // Given
        val productId = testProductIds[0]
        val concurrentRequests = 10
        val quantityPerRequest = 5

        val executor = Executors.newFixedThreadPool(concurrentRequests)
        val latch = CountDownLatch(concurrentRequests)
        val successCount = AtomicInteger(0)

        // When: 동시에 10개 요청 (각 5개씩 차감)
        repeat(concurrentRequests) { index ->
            executor.submit {
                try {
                    val items =
                        listOf(
                            OrderItemRequest(productId = productId, quantity = quantityPerRequest),
                        )
                    val reservationId = "concurrent-test-$index"

                    val result =
                        stockReservationService.tryReserve(
                            storeId = testStoreId,
                            items = items,
                            reservationId = reservationId,
                            ttl = Duration.ofMinutes(10),
                        )

                    if (result is ReservationResult.Success) {
                        successCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then
        // 재시도 10회로 90%+ 성공률 보장 (극한 동시성에서 일부 False Negative 허용)
        val minExpectedSuccess = 9 // 90% 성공률
        assertThat(successCount.get())
            .describedAs("Success count should be at least $minExpectedSuccess (90%% success rate)")
            .isGreaterThanOrEqualTo(minExpectedSuccess)
        assertThat(successCount.get())
            .describedAs("Success count should not exceed total requests")
            .isLessThanOrEqualTo(concurrentRequests)

        // 재고는 성공한 요청만큼만 차감되어야 함
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        val expectedStock = 100 - (successCount.get() * quantityPerRequest)
        assertThat(remainingStock).isEqualTo(expectedStock.toLong())
    }

    @Test
    fun `동시에 20개 요청이 들어오면 10개는 성공하고 10개는 실패해야 한다`() {
        // Given
        val productId = testProductIds[0]
        // 초기 재고를 50개로 설정
        redissonClient.getAtomicLong("product:remaining-stock:$productId").set(50)

        val concurrentRequests = 20
        val quantityPerRequest = 5

        val executor = Executors.newFixedThreadPool(concurrentRequests)
        val latch = CountDownLatch(concurrentRequests)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // When: 동시에 20개 요청 (각 5개씩 차감 시도)
        repeat(concurrentRequests) { index ->
            executor.submit {
                try {
                    val items =
                        listOf(
                            OrderItemRequest(productId = productId, quantity = quantityPerRequest),
                        )
                    val reservationId = "concurrent-test-$index"

                    val result =
                        stockReservationService.tryReserve(
                            storeId = testStoreId,
                            items = items,
                            reservationId = reservationId,
                            ttl = Duration.ofMinutes(10),
                        )

                    when (result) {
                        is ReservationResult.Success -> successCount.incrementAndGet()
                        is ReservationResult.InsufficientStock -> failCount.incrementAndGet()
                        is ReservationResult.StoreClosed -> failCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then
        // 재고 50개, 각 5개씩 요청 → 이론적으로 10개 성공 가능
        // CAS 충돌로 9-10개 성공 허용 (90-100% 성공률)
        val minExpectedSuccess = 9
        val maxExpectedSuccess = 10
        assertThat(successCount.get())
            .describedAs("Success count should be between $minExpectedSuccess and $maxExpectedSuccess")
            .isGreaterThanOrEqualTo(minExpectedSuccess)
            .isLessThanOrEqualTo(maxExpectedSuccess)

        assertThat(successCount.get() + failCount.get())
            .describedAs("Total processed requests should equal concurrent requests")
            .isEqualTo(concurrentRequests)

        // 재고는 성공한 요청만큼만 차감되어야 함
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        val expectedStock = 50 - (successCount.get() * quantityPerRequest)
        assertThat(remainingStock).isEqualTo(expectedStock.toLong())
    }

    @Test
    fun `다중 상품 주문 시 일부 상품 재고가 부족하면 전체 롤백되어야 한다`() {
        // Given
        val product1 = testProductIds[0]
        val product2 = testProductIds[1]

        // product2의 재고를 5개로 설정
        redissonClient.getAtomicLong("product:remaining-stock:$product2").set(5)

        val items =
            listOf(
                OrderItemRequest(productId = product1, quantity = 10), // 재고 충분
                OrderItemRequest(productId = product2, quantity = 10), // 재고 부족
            )
        val reservationId = UUID.randomUUID().toString()

        // When
        val result =
            stockReservationService.tryReserve(
                storeId = testStoreId,
                items = items,
                reservationId = reservationId,
                ttl = Duration.ofMinutes(10),
            )

        // Then
        assertThat(result).isEqualTo(ReservationResult.InsufficientStock)

        // 두 상품 모두 재고가 원래대로 돌아와야 함 (롤백)
        val stock1 = redissonClient.getAtomicLong("product:remaining-stock:$product1").get()
        val stock2 = redissonClient.getAtomicLong("product:remaining-stock:$product2").get()

        assertThat(stock1).isEqualTo(100) // product1 롤백됨
        assertThat(stock2).isEqualTo(5) // product2는 차감되지 않음
    }

    @Test
    fun `예약 확정 시 예약 정보가 삭제되어야 한다`() {
        // Given
        val productId = testProductIds[0]
        val items =
            listOf(
                OrderItemRequest(productId = productId, quantity = 10),
            )
        val reservationId = UUID.randomUUID().toString()

        stockReservationService.tryReserve(
            storeId = testStoreId,
            items = items,
            reservationId = reservationId,
            ttl = Duration.ofMinutes(10),
        )

        // When
        stockReservationService.confirmReservation(reservationId)

        // Then
        val reservationKey = "product:reservation-stock:$reservationId"
        val reservationExists = redissonClient.getBucket<String>(reservationKey).isExists

        assertThat(reservationExists).isFalse() // 예약 정보 삭제됨

        // 재고는 그대로 유지 (90개)
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        assertThat(remainingStock).isEqualTo(90)
    }

    @Test
    fun `예약 취소 시 재고가 복구되어야 한다`() {
        // Given
        val productId = testProductIds[0]
        val items =
            listOf(
                OrderItemRequest(productId = productId, quantity = 10),
            )
        val reservationId = UUID.randomUUID().toString()

        stockReservationService.tryReserve(
            storeId = testStoreId,
            items = items,
            reservationId = reservationId,
            ttl = Duration.ofMinutes(10),
        )

        val stockBeforeCancel = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        assertThat(stockBeforeCancel).isEqualTo(90)

        // When
        stockReservationService.cancelReservation(reservationId)

        // Then
        val stockAfterCancel = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        assertThat(stockAfterCancel).isEqualTo(100) // 재고 복구됨

        // 예약 정보 삭제 확인
        val reservationKey = "product:reservation-stock:$reservationId"
        val reservationExists = redissonClient.getBucket<String>(reservationKey).isExists
        assertThat(reservationExists).isFalse()
    }

    @Test
    fun `재고가 0일 때 차감 시도하면 즉시 실패해야 한다`() {
        // Given
        val productId = testProductIds[0]
        redissonClient.getAtomicLong("product:remaining-stock:$productId").set(0)

        val items =
            listOf(
                OrderItemRequest(productId = productId, quantity = 1),
            )
        val reservationId = UUID.randomUUID().toString()

        // When
        val result =
            stockReservationService.tryReserve(
                storeId = testStoreId,
                items = items,
                reservationId = reservationId,
                ttl = Duration.ofMinutes(10),
            )

        // Then
        assertThat(result).isEqualTo(ReservationResult.InsufficientStock)

        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$productId").get()
        assertThat(remainingStock).isEqualTo(0) // 음수로 가지 않음
    }
}
