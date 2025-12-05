package com.groom.order.adapter.outbound.scheduler

import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * OrderTimeoutScheduler 통합 테스트
 *
 * 실제 DB를 사용하여 만료된 주문 타임아웃 처리를 검증합니다.
 *
 * 이벤트 기반 플로우:
 * 1. SQL 스크립트로 만료된/유효한 주문 생성
 * 2. 스케줄러 수동 실행
 * 3. 만료된 주문이 PAYMENT_TIMEOUT으로 변경되었는지 검증
 * 4. OrderTimeoutEvent 이벤트 발행 (재고 복구는 Product Service 책임)
 *
 * Note: 재고 예약 취소는 더 이상 Order Service에서 직접 수행하지 않습니다.
 *       Product Service가 order.timeout 이벤트를 소비하여 재고를 복구합니다.
 */
@SqlGroup(
    Sql(
        scripts = ["/sql/scheduler/cleanup-order-timeout-scheduler-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/scheduler/init-order-timeout-scheduler-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    ),
    Sql(
        scripts = ["/sql/scheduler/cleanup-order-timeout-scheduler-test.sql"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
    ),
)
@DisplayName("주문 타임아웃 스케줄러 통합 테스트")
class OrderTimeoutSchedulerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var orderTimeoutScheduler: OrderTimeoutScheduler

    @Autowired
    private lateinit var loadOrderPort: LoadOrderPort

    @Autowired
    private lateinit var saveOrderPort: SaveOrderPort

    @Autowired
    private lateinit var redissonClient: RedissonClient

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    companion object {
        // SQL 스크립트에서 생성하는 주문 ID (만료된 주문)
        private val EXPIRED_ORDER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-111111111111")
        private const val EXPIRED_RESERVATION_ID = "RES-EXPIRED-001"

        // SQL 스크립트에서 생성하는 주문 ID (유효한 주문)
        private val ACTIVE_ORDER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-222222222222")
        private const val ACTIVE_RESERVATION_ID = "RES-ACTIVE-002"

        // 테스트 제품 ID
        private val TEST_PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
    }

    @BeforeEach
    fun setUp() {
        // 테스트 전 Redis 정리 (다른 테스트와 격리)
        redissonClient.keys.deleteByPattern("product:*")

        // ShedLock 정리 (스케줄러 락 해제)
        redissonClient.keys.deleteByPattern("job-lock:*")

        // Redis 재고 예약 설정
        setupReservation(EXPIRED_RESERVATION_ID, 10) // 만료된 주문의 예약
        setupReservation(ACTIVE_RESERVATION_ID, 5) // 유효한 주문의 예약

        // 초기 재고 설정
        redissonClient.getAtomicLong("product:remaining-stock:$TEST_PRODUCT_ID").set(100)
    }

    private fun setupReservation(
        reservationId: String,
        quantity: Int,
    ) {
        val reservationData = "$TEST_PRODUCT_ID:$quantity"

        // 1. 예약 버킷 생성 (TTL 10분)
        val bucket = redissonClient.getBucket<String>("product:reservation-stock:$reservationId")
        bucket.set(reservationData, Duration.ofMinutes(10))

        // 2. 만료 인덱스에도 등록 (서비스의 tryReserve()와 동일한 방식)
        val expiryTimestamp =
            Instant
                .now()
                .plusSeconds(600)
                .epochSecond
                .toDouble()
        val expirySet = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
        expirySet.add(expiryTimestamp, "$reservationId:$reservationData")
    }

    @Test
    @DisplayName("만료된 주문을 PAYMENT_TIMEOUT으로 변경하고 OrderTimeoutEvent를 발행한다")
    fun `should timeout expired orders and publish OrderTimeoutEvent`() {
        // given: SQL 스크립트로 만료된 주문 생성됨
        val expiredOrder = loadOrderPort.loadById(EXPIRED_ORDER_ID)!!
        assertEquals(OrderStatus.PAYMENT_PENDING, expiredOrder.status, "초기 상태는 PAYMENT_PENDING")
        assertEquals(EXPIRED_RESERVATION_ID, expiredOrder.reservationId, "예약 ID가 설정되어 있어야 함")

        // when: 스케줄러 실행
        orderTimeoutScheduler.processExpiredOrders()

        // then: 주문 상태가 PAYMENT_TIMEOUT으로 변경
        val updatedOrder =
            transactionApplier
                .applyPrimaryTransaction {
                    loadOrderPort.loadById(EXPIRED_ORDER_ID)
                }!!

        assertEquals(OrderStatus.PAYMENT_TIMEOUT, updatedOrder.status, "만료된 주문은 PAYMENT_TIMEOUT으로 변경")
        assertNotNull(updatedOrder.failureReason, "실패 사유가 기록되어야 함")

        // Note: 재고 예약 취소는 Product Service에서 order.timeout 이벤트를 소비하여 처리합니다.
        // 이벤트 발행 검증은 단위 테스트에서 수행합니다.
    }

    @Test
    @DisplayName("만료되지 않은 주문은 그대로 유지한다")
    fun `should keep active orders unchanged`() {
        // given: SQL 스크립트로 유효한 주문 생성됨
        val activeOrder =
            transactionApplier
                .applyPrimaryTransaction {
                    loadOrderPort.loadById(ACTIVE_ORDER_ID)
                }!!
        assertEquals(OrderStatus.PAYMENT_PENDING, activeOrder.status, "초기 상태는 PAYMENT_PENDING")

        // when: 스케줄러 실행
        orderTimeoutScheduler.processExpiredOrders()

        // then: 유효한 주문은 상태 유지
        val updatedOrder =
            transactionApplier
                .applyPrimaryTransaction {
                    loadOrderPort.loadById(ACTIVE_ORDER_ID)
                }!!
        assertEquals(OrderStatus.PAYMENT_PENDING, updatedOrder.status, "유효한 주문은 상태 유지")

        // Redis 예약도 유지
        val reservationAfter =
            redissonClient.getBucket<String>("product:reservation-stock:$ACTIVE_RESERVATION_ID").get()
        assertNotNull(reservationAfter, "유효한 주문의 예약은 유지되어야 함")
    }

    @Test
    @DisplayName("만료된 주문이 없는 경우 스케줄러가 정상 종료된다")
    fun `should complete successfully when no expired orders exist`() {
        // given: 모든 주문을 삭제하여 만료된 주문이 없도록 함
        // Note: deleteAll은 Port 인터페이스에 없으므로 스케줄러가 빈 리스트를 반환하도록 만료된 주문만 삭제
        val expiredOrder = loadOrderPort.loadById(EXPIRED_ORDER_ID)
        if (expiredOrder != null) {
            // 만료된 주문을 타임아웃 처리하여 스케줄러가 조회하지 않도록 함
            expiredOrder.timeout()
            saveOrderPort.save(expiredOrder)
        }

        // when & then: 스케줄러 실행 (예외 없이 정상 종료)
        orderTimeoutScheduler.processExpiredOrders()
    }

    @Test
    @DisplayName("이미 타임아웃된 주문은 재처리하지 않는다")
    fun `should not reprocess already timed out orders`() {
        // given: 만료된 주문을 수동으로 타임아웃 처리
        val expiredOrder = transactionApplier.applyPrimaryTransaction { loadOrderPort.loadById(EXPIRED_ORDER_ID) }!!
        expiredOrder.timeout()
        saveOrderPort.save(expiredOrder)

        assertEquals(OrderStatus.PAYMENT_TIMEOUT, expiredOrder.status, "이미 타임아웃 상태")

        // when: 스케줄러 재실행
        orderTimeoutScheduler.processExpiredOrders()

        // then: 상태 유지 (재처리되지 않음)
        val unchangedOrder = transactionApplier.applyPrimaryTransaction { loadOrderPort.loadById(EXPIRED_ORDER_ID) }!!
        assertEquals(OrderStatus.PAYMENT_TIMEOUT, unchangedOrder.status, "타임아웃 상태 유지")
    }
}
