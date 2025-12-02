package com.groom.order.application.service

import com.groom.order.application.dto.GetOrderDetailQuery
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderStatus
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID

/**
 * GetOrderDetailService 통합 테스트
 *
 * 주문 상세 조회 서비스의 전체 플로우를 검증합니다.
 * - 주문 상세 정보 조회
 * - 주문 항목 포함 검증
 * - 접근 권한 검증
 */
@DisplayName("GetOrderDetailService 통합 테스트")
class GetOrderDetailServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var getOrderDetailService: GetOrderDetailService

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    @Autowired
    private lateinit var entityManager: EntityManager

    companion object {
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val CUSTOMER_USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
        private val STORE_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        private val PRODUCT_KEYBOARD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")
    }

    private var orderStockReserved: UUID = UUID.randomUUID()
    private var orderPaymentCompleted: UUID = UUID.randomUUID()
    private var orderDelivered: UUID = UUID.randomUUID()
    private var orderCancelled: UUID = UUID.randomUUID()
    private var orderOtherUser: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        createTestOrders()
    }

    private fun createTestOrders() {
        transactionApplier.applyPrimaryTransaction {
            val now = LocalDateTime.now()

            // 1. STOCK_RESERVED 상태 주문 (단일 상품)
            orderStockReserved = UUID.randomUUID()
            createOrder(
                orderStockReserved,
                CUSTOMER_USER_1,
                "STOCK_RESERVED",
                "ORD-DETAIL-001",
                reservationId = "RES-001",
                expiresAt = now.plusMinutes(10),
            )
            createOrderItem(orderStockReserved, PRODUCT_MOUSE, "무선 마우스", 50000, 2)

            // 2. PAYMENT_COMPLETED 상태 주문 (다중 상품)
            orderPaymentCompleted = UUID.randomUUID()
            createOrder(
                orderPaymentCompleted,
                CUSTOMER_USER_1,
                "PAYMENT_COMPLETED",
                "ORD-DETAIL-002",
                paymentId = UUID.randomUUID(),
            )
            createOrderItem(orderPaymentCompleted, PRODUCT_MOUSE, "무선 마우스", 50000, 1)
            createOrderItem(orderPaymentCompleted, PRODUCT_KEYBOARD, "기계식 키보드", 120000, 1)

            // 3. DELIVERED 상태 주문
            orderDelivered = UUID.randomUUID()
            createOrder(
                orderDelivered,
                CUSTOMER_USER_1,
                "DELIVERED",
                "ORD-DETAIL-003",
                paymentId = UUID.randomUUID(),
                confirmedAt = now.minusDays(3),
            )
            createOrderItem(orderDelivered, PRODUCT_MOUSE, "무선 마우스", 50000, 1)

            // 4. ORDER_CANCELLED 상태 주문
            orderCancelled = UUID.randomUUID()
            createOrder(
                orderCancelled,
                CUSTOMER_USER_1,
                "ORDER_CANCELLED",
                "ORD-DETAIL-004",
                failureReason = "단순 변심",
                cancelledAt = now.minusDays(1),
            )
            createOrderItem(orderCancelled, PRODUCT_MOUSE, "무선 마우스", 50000, 1)

            // 5. 다른 사용자의 주문
            orderOtherUser = UUID.randomUUID()
            createOrder(orderOtherUser, CUSTOMER_USER_2, "STOCK_RESERVED", "ORD-DETAIL-005")
            createOrderItem(orderOtherUser, PRODUCT_MOUSE, "무선 마우스", 50000, 1)

            entityManager.flush()
        }
    }

    private fun createOrder(
        orderId: UUID,
        userId: UUID,
        status: String,
        orderNumber: String,
        reservationId: String? = null,
        expiresAt: LocalDateTime? = null,
        paymentId: UUID? = null,
        confirmedAt: LocalDateTime? = null,
        failureReason: String? = null,
        cancelledAt: LocalDateTime? = null,
    ) {
        val expiresAtStr = expiresAt?.let { "'$it'" } ?: "NULL"
        val confirmedAtStr = confirmedAt?.let { "'$it'" } ?: "NULL"
        val cancelledAtStr = cancelledAt?.let { "'$it'" } ?: "NULL"

        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                     reservation_id, expires_at, payment_id, confirmed_at, failure_reason, cancelled_at,
                                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?,
                        '{}',
                        '[{"status":"$status","timestamp":"${LocalDateTime.now()}","description":"테스트"}]',
                        '상세 조회 테스트', ?, $expiresAtStr, ?, $confirmedAtStr, ?, $cancelledAtStr,
                        NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, orderId)
            .setParameter(2, userId)
            .setParameter(3, STORE_1)
            .setParameter(4, orderNumber)
            .setParameter(5, status)
            .setParameter(6, reservationId)
            .setParameter(7, paymentId)
            .setParameter(8, failureReason)
            .executeUpdate()
    }

    private fun createOrderItem(
        orderId: UUID,
        productId: UUID,
        productName: String,
        unitPrice: Int,
        quantity: Int,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                          created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, UUID.randomUUID())
            .setParameter(2, orderId)
            .setParameter(3, productId)
            .setParameter(4, productName)
            .setParameter(5, unitPrice)
            .setParameter(6, quantity)
            .executeUpdate()
    }

    @AfterEach
    fun tearDown() {
        transactionApplier.applyPrimaryTransaction {
            entityManager.createNativeQuery("DELETE FROM p_order_item WHERE order_id IN (SELECT id FROM p_order WHERE order_number LIKE 'ORD-DETAIL-%')").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM p_order WHERE order_number LIKE 'ORD-DETAIL-%'").executeUpdate()
            entityManager.flush()
        }
    }

    @Test
    @DisplayName("STOCK_RESERVED 상태 주문 상세 조회 성공")
    fun getOrderDetail_withStockReservedStatus_shouldSucceed() {
        // given
        val query = GetOrderDetailQuery(
            orderId = orderStockReserved,
            requestUserId = CUSTOMER_USER_1,
        )

        // when
        val result = getOrderDetailService.getOrderDetail(query)

        // then
        assertThat(result.orderId).isEqualTo(orderStockReserved)
        assertThat(result.orderNumber).isEqualTo("ORD-DETAIL-001")
        assertThat(result.status).isEqualTo(OrderStatus.STOCK_RESERVED)
        assertThat(result.totalAmount.toLong()).isEqualTo(100000L) // 50000 * 2
        assertThat(result.reservationId).isEqualTo("RES-001")
        assertThat(result.expiresAt).isNotNull()
        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].productName).isEqualTo("무선 마우스")
        assertThat(result.items[0].quantity).isEqualTo(2)
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 상태 주문 상세 조회 - 다중 상품")
    fun getOrderDetail_withMultipleItems_shouldReturnAllItems() {
        // given
        val query = GetOrderDetailQuery(
            orderId = orderPaymentCompleted,
            requestUserId = CUSTOMER_USER_1,
        )

        // when
        val result = getOrderDetailService.getOrderDetail(query)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.PAYMENT_COMPLETED)
        assertThat(result.paymentId).isNotNull()
        assertThat(result.items).hasSize(2)
        assertThat(result.totalAmount.toLong()).isEqualTo(170000L) // 50000 + 120000
    }

    @Test
    @DisplayName("DELIVERED 상태 주문 상세 조회")
    fun getOrderDetail_withDeliveredStatus_shouldIncludeConfirmedAt() {
        // given
        val query = GetOrderDetailQuery(
            orderId = orderDelivered,
            requestUserId = CUSTOMER_USER_1,
        )

        // when
        val result = getOrderDetailService.getOrderDetail(query)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.DELIVERED)
        assertThat(result.confirmedAt).isNotNull()
    }

    @Test
    @DisplayName("ORDER_CANCELLED 상태 주문 상세 조회 - 취소 사유 포함")
    fun getOrderDetail_withCancelledStatus_shouldIncludeFailureReason() {
        // given
        val query = GetOrderDetailQuery(
            orderId = orderCancelled,
            requestUserId = CUSTOMER_USER_1,
        )

        // when
        val result = getOrderDetailService.getOrderDetail(query)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.ORDER_CANCELLED)
        assertThat(result.failureReason).isEqualTo("단순 변심")
        assertThat(result.cancelledAt).isNotNull()
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 시 OrderNotFound 예외 발생")
    fun getOrderDetail_withNonExistentOrder_shouldThrowOrderNotFound() {
        // given
        val nonExistentOrderId = UUID.randomUUID()
        val query = GetOrderDetailQuery(
            orderId = nonExistentOrderId,
            requestUserId = CUSTOMER_USER_1,
        )

        // when & then
        assertThatThrownBy { getOrderDetailService.getOrderDetail(query) }
            .isInstanceOf(OrderException.OrderNotFound::class.java)
    }

    @Test
    @DisplayName("다른 사용자의 주문 조회 시 OrderAccessDenied 예외 발생")
    fun getOrderDetail_withOtherUsersOrder_shouldThrowOrderAccessDenied() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 조회 시도
        val query = GetOrderDetailQuery(
            orderId = orderOtherUser,
            requestUserId = CUSTOMER_USER_1,
        )

        // when & then
        assertThatThrownBy { getOrderDetailService.getOrderDetail(query) }
            .isInstanceOf(OrderException.OrderAccessDenied::class.java)
    }

    @Test
    @DisplayName("주문 소유자가 본인 주문 조회 성공")
    fun getOrderDetail_withOwner_shouldSucceed() {
        // given: CUSTOMER_USER_2가 본인 주문 조회
        val query = GetOrderDetailQuery(
            orderId = orderOtherUser,
            requestUserId = CUSTOMER_USER_2,
        )

        // when
        val result = getOrderDetailService.getOrderDetail(query)

        // then
        assertThat(result.orderId).isEqualTo(orderOtherUser)
        assertThat(result.orderNumber).isEqualTo("ORD-DETAIL-005")
    }
}
