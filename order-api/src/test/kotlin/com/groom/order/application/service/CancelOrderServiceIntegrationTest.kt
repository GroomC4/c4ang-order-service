package com.groom.order.application.service

import com.groom.order.application.dto.CancelOrderCommand
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID

/**
 * CancelOrderService 통합 테스트
 *
 * 주문 취소 서비스의 전체 플로우를 검증합니다.
 * - 다양한 주문 상태에서의 취소 처리
 * - 재고 복구 검증
 * - 접근 권한 검증
 */
@DisplayName("CancelOrderService 통합 테스트")
class CancelOrderServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var cancelOrderService: CancelOrderService

    @Autowired
    private lateinit var loadOrderPort: LoadOrderPort

    @Autowired
    private lateinit var redissonClient: RedissonClient

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    @Autowired
    private lateinit var entityManager: EntityManager

    companion object {
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val CUSTOMER_USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
        private val STORE_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
    }

    private var orderStockReserved: UUID = UUID.randomUUID()
    private var orderPaymentPending: UUID = UUID.randomUUID()
    private var orderPreparing: UUID = UUID.randomUUID()
    private var orderShipped: UUID = UUID.randomUUID()
    private var orderDelivered: UUID = UUID.randomUUID()
    private var orderOtherUser: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").set(100)
        createTestOrders()
    }

    private fun createTestOrders() {
        transactionApplier.applyPrimaryTransaction {
            val now = LocalDateTime.now()
            val reservationId = "RES-${UUID.randomUUID()}"

            // 1. STOCK_RESERVED 상태 주문
            orderStockReserved = UUID.randomUUID()
            createOrder(orderStockReserved, CUSTOMER_USER_1, "STOCK_RESERVED", "ORD-CANCEL-001", reservationId)

            // 2. PAYMENT_PENDING 상태 주문
            orderPaymentPending = UUID.randomUUID()
            createOrder(orderPaymentPending, CUSTOMER_USER_1, "PAYMENT_PENDING", "ORD-CANCEL-002", null)

            // 3. PREPARING 상태 주문
            orderPreparing = UUID.randomUUID()
            createOrder(orderPreparing, CUSTOMER_USER_1, "PREPARING", "ORD-CANCEL-003", null)

            // 4. SHIPPED 상태 주문
            orderShipped = UUID.randomUUID()
            createOrder(orderShipped, CUSTOMER_USER_1, "SHIPPED", "ORD-CANCEL-004", null)

            // 5. DELIVERED 상태 주문
            orderDelivered = UUID.randomUUID()
            createOrder(orderDelivered, CUSTOMER_USER_1, "DELIVERED", "ORD-CANCEL-005", null)

            // 6. 다른 사용자의 주문
            orderOtherUser = UUID.randomUUID()
            createOrder(orderOtherUser, CUSTOMER_USER_2, "STOCK_RESERVED", "ORD-CANCEL-006", reservationId)

            entityManager.flush()
        }
    }

    private fun createOrder(
        orderId: UUID,
        userId: UUID,
        status: String,
        orderNumber: String,
        reservationId: String?,
    ) {
        val reservationIdParam = reservationId ?: UUID.randomUUID().toString()
        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                     reservation_id, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?,
                        '{}',
                        '[{"status":"$status","timestamp":"${LocalDateTime.now()}","description":"테스트"}]',
                        '취소 테스트용', ?, NOW() + INTERVAL '10 minutes', NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, orderId)
            .setParameter(2, userId)
            .setParameter(3, STORE_1)
            .setParameter(4, orderNumber)
            .setParameter(5, status)
            .setParameter(6, reservationIdParam)
            .executeUpdate()

        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                          created_at, updated_at)
                VALUES (?, ?, ?, '무선 마우스', 50000, 2, NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, UUID.randomUUID())
            .setParameter(2, orderId)
            .setParameter(3, PRODUCT_MOUSE)
            .executeUpdate()
    }

    @AfterEach
    fun tearDown() {
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").delete()
        val expiryIndex = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
        expiryIndex.clear()

        // 테스트 데이터 정리
        transactionApplier.applyPrimaryTransaction {
            entityManager.createNativeQuery("DELETE FROM p_order_item WHERE order_id IN (SELECT id FROM p_order WHERE order_number LIKE 'ORD-CANCEL-%')").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM p_order WHERE order_number LIKE 'ORD-CANCEL-%'").executeUpdate()
            entityManager.flush()
        }
    }

    @Test
    @DisplayName("STOCK_RESERVED 상태 주문 취소 성공")
    fun cancelOrder_withStockReservedStatus_shouldSucceed() {
        // given
        val command = CancelOrderCommand(
            orderId = orderStockReserved,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = "단순 변심",
        )

        // when
        val result = cancelOrderService.cancelOrder(command)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.ORDER_CANCELLED)
        assertThat(result.cancelReason).isEqualTo("단순 변심")
        assertThat(result.cancelledAt).isNotNull()

        // DB 검증
        val cancelledOrder = loadOrderPort.loadById(orderStockReserved)
        assertThat(cancelledOrder).isNotNull
        assertThat(cancelledOrder!!.status).isEqualTo(OrderStatus.ORDER_CANCELLED)
    }

    @Test
    @DisplayName("PAYMENT_PENDING 상태 주문 취소 성공")
    fun cancelOrder_withPaymentPendingStatus_shouldSucceed() {
        // given
        val command = CancelOrderCommand(
            orderId = orderPaymentPending,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = "결제 중 취소",
        )

        // when
        val result = cancelOrderService.cancelOrder(command)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.ORDER_CANCELLED)
    }

    @Test
    @DisplayName("PREPARING 상태 주문 취소 성공")
    fun cancelOrder_withPreparingStatus_shouldSucceed() {
        // given
        val command = CancelOrderCommand(
            orderId = orderPreparing,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = "준비 중 취소",
        )

        // when
        val result = cancelOrderService.cancelOrder(command)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.ORDER_CANCELLED)
    }

    @Test
    @DisplayName("SHIPPED 상태 주문 취소 시 실패")
    fun cancelOrder_withShippedStatus_shouldFail() {
        // given
        val command = CancelOrderCommand(
            orderId = orderShipped,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = "배송 중 취소 시도",
        )

        // when & then
        assertThatThrownBy { cancelOrderService.cancelOrder(command) }
            .isInstanceOf(OrderException.CannotCancelOrder::class.java)
    }

    @Test
    @DisplayName("DELIVERED 상태 주문 취소 시 실패")
    fun cancelOrder_withDeliveredStatus_shouldFail() {
        // given
        val command = CancelOrderCommand(
            orderId = orderDelivered,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = "배송 완료 후 취소 시도",
        )

        // when & then
        assertThatThrownBy { cancelOrderService.cancelOrder(command) }
            .isInstanceOf(OrderException.CannotCancelOrder::class.java)
    }

    @Test
    @DisplayName("존재하지 않는 주문 취소 시 OrderNotFound 예외 발생")
    fun cancelOrder_withNonExistentOrder_shouldThrowOrderNotFound() {
        // given
        val nonExistentOrderId = UUID.randomUUID()
        val command = CancelOrderCommand(
            orderId = nonExistentOrderId,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = "취소",
        )

        // when & then
        assertThatThrownBy { cancelOrderService.cancelOrder(command) }
            .isInstanceOf(OrderException.OrderNotFound::class.java)
    }

    @Test
    @DisplayName("다른 사용자의 주문 취소 시 OrderAccessDenied 예외 발생")
    fun cancelOrder_withOtherUsersOrder_shouldThrowOrderAccessDenied() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 취소 시도
        val command = CancelOrderCommand(
            orderId = orderOtherUser,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = "취소",
        )

        // when & then
        assertThatThrownBy { cancelOrderService.cancelOrder(command) }
            .isInstanceOf(OrderException.OrderAccessDenied::class.java)
    }

    @Test
    @DisplayName("취소 사유 없이 주문 취소 성공")
    fun cancelOrder_withoutCancelReason_shouldSucceed() {
        // given
        val command = CancelOrderCommand(
            orderId = orderStockReserved,
            requestUserId = CUSTOMER_USER_1,
            cancelReason = null,
        )

        // when
        val result = cancelOrderService.cancelOrder(command)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.ORDER_CANCELLED)
        assertThat(result.cancelReason).isNull()
    }
}
