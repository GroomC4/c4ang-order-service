package com.groom.order.application.service

import com.groom.order.application.dto.RefundOrderCommand
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
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID

/**
 * RefundOrderService 통합 테스트
 *
 * 주문 환불 서비스의 전체 플로우를 검증합니다.
 * - 환불 가능 상태 검증 (DELIVERED만 가능)
 * - 환불 금액 계산
 * - 접근 권한 검증
 */
@DisplayName("RefundOrderService 통합 테스트")
class RefundOrderServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var refundOrderService: RefundOrderService

    @Autowired
    private lateinit var loadOrderPort: LoadOrderPort

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

    private var orderDelivered: UUID = UUID.randomUUID()
    private var orderDeliveredMultiItem: UUID = UUID.randomUUID()
    private var orderPaymentCompleted: UUID = UUID.randomUUID()
    private var orderPreparing: UUID = UUID.randomUUID()
    private var orderShipped: UUID = UUID.randomUUID()
    private var orderOtherUser: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        createTestOrders()
    }

    private fun createTestOrders() {
        transactionApplier.applyPrimaryTransaction {
            val now = LocalDateTime.now()

            // 1. DELIVERED 상태 주문 (단일 상품)
            orderDelivered = UUID.randomUUID()
            createOrder(orderDelivered, CUSTOMER_USER_1, "DELIVERED", "ORD-REFUND-001")
            createOrderItem(orderDelivered, PRODUCT_MOUSE, "무선 마우스", 50000, 2)

            // 2. DELIVERED 상태 주문 (다중 상품)
            orderDeliveredMultiItem = UUID.randomUUID()
            createOrder(orderDeliveredMultiItem, CUSTOMER_USER_1, "DELIVERED", "ORD-REFUND-002")
            createOrderItem(orderDeliveredMultiItem, PRODUCT_MOUSE, "무선 마우스", 50000, 1)
            createOrderItem(orderDeliveredMultiItem, PRODUCT_KEYBOARD, "기계식 키보드", 120000, 1)

            // 3. PAYMENT_COMPLETED 상태 주문
            orderPaymentCompleted = UUID.randomUUID()
            createOrder(orderPaymentCompleted, CUSTOMER_USER_1, "PAYMENT_COMPLETED", "ORD-REFUND-003")
            createOrderItem(orderPaymentCompleted, PRODUCT_MOUSE, "무선 마우스", 50000, 1)

            // 4. PREPARING 상태 주문
            orderPreparing = UUID.randomUUID()
            createOrder(orderPreparing, CUSTOMER_USER_1, "PREPARING", "ORD-REFUND-004")
            createOrderItem(orderPreparing, PRODUCT_MOUSE, "무선 마우스", 50000, 1)

            // 5. SHIPPED 상태 주문
            orderShipped = UUID.randomUUID()
            createOrder(orderShipped, CUSTOMER_USER_1, "SHIPPED", "ORD-REFUND-005")
            createOrderItem(orderShipped, PRODUCT_MOUSE, "무선 마우스", 50000, 1)

            // 6. 다른 사용자의 DELIVERED 주문
            orderOtherUser = UUID.randomUUID()
            createOrder(orderOtherUser, CUSTOMER_USER_2, "DELIVERED", "ORD-REFUND-006")
            createOrderItem(orderOtherUser, PRODUCT_MOUSE, "무선 마우스", 50000, 1)

            entityManager.flush()
        }
    }

    private fun createOrder(
        orderId: UUID,
        userId: UUID,
        status: String,
        orderNumber: String,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                     payment_id, confirmed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?,
                        '{}',
                        '[{"status":"$status","timestamp":"${LocalDateTime.now()}","description":"테스트"}]',
                        '환불 테스트용', ?, NOW() - INTERVAL '3 days', NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, orderId)
            .setParameter(2, userId)
            .setParameter(3, STORE_1)
            .setParameter(4, orderNumber)
            .setParameter(5, status)
            .setParameter(6, UUID.randomUUID())
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
            entityManager
                .createNativeQuery(
                    "DELETE FROM p_order_item WHERE order_id IN (SELECT id FROM p_order WHERE order_number LIKE 'ORD-REFUND-%')",
                ).executeUpdate()
            entityManager.createNativeQuery("DELETE FROM p_order WHERE order_number LIKE 'ORD-REFUND-%'").executeUpdate()
            entityManager.flush()
        }
    }

    @Test
    @DisplayName("DELIVERED 상태 주문 환불 성공")
    fun refundOrder_withDeliveredStatus_shouldSucceed() {
        // given
        val command =
            RefundOrderCommand(
                orderId = orderDelivered,
                requestUserId = CUSTOMER_USER_1,
                refundReason = "상품 불량",
            )

        // when
        val result = refundOrderService.refundOrder(command)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.REFUND_COMPLETED)
        assertThat(result.refundId).isNotNull()
        assertThat(result.refundId).startsWith("REFUND-")
        assertThat(result.refundAmount.toLong()).isEqualTo(100000L) // 50000 * 2
        assertThat(result.refundReason).isEqualTo("상품 불량")

        // DB 검증
        val refundedOrder = loadOrderPort.loadById(orderDelivered)
        assertThat(refundedOrder).isNotNull
        assertThat(refundedOrder!!.status).isEqualTo(OrderStatus.REFUND_COMPLETED)
        assertThat(refundedOrder.refundId).isNotNull()
    }

    @Test
    @DisplayName("다중 상품 주문 환불 시 총액 정확히 계산")
    fun refundOrder_withMultipleItems_shouldCalculateCorrectAmount() {
        // given
        val command =
            RefundOrderCommand(
                orderId = orderDeliveredMultiItem,
                requestUserId = CUSTOMER_USER_1,
                refundReason = "전체 환불",
            )

        // when
        val result = refundOrderService.refundOrder(command)

        // then
        assertThat(result.refundAmount.toLong()).isEqualTo(170000L) // 50000 + 120000
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 상태 주문 환불 시 실패")
    fun refundOrder_withPaymentCompletedStatus_shouldFail() {
        // given
        val command =
            RefundOrderCommand(
                orderId = orderPaymentCompleted,
                requestUserId = CUSTOMER_USER_1,
                refundReason = "환불 요청",
            )

        // when & then
        assertThatThrownBy { refundOrderService.refundOrder(command) }
            .isInstanceOf(OrderException.CannotRefundOrder::class.java)
    }

    @Test
    @DisplayName("PREPARING 상태 주문 환불 시 실패")
    fun refundOrder_withPreparingStatus_shouldFail() {
        // given
        val command =
            RefundOrderCommand(
                orderId = orderPreparing,
                requestUserId = CUSTOMER_USER_1,
                refundReason = "환불 요청",
            )

        // when & then
        assertThatThrownBy { refundOrderService.refundOrder(command) }
            .isInstanceOf(OrderException.CannotRefundOrder::class.java)
    }

    @Test
    @DisplayName("SHIPPED 상태 주문 환불 시 실패")
    fun refundOrder_withShippedStatus_shouldFail() {
        // given
        val command =
            RefundOrderCommand(
                orderId = orderShipped,
                requestUserId = CUSTOMER_USER_1,
                refundReason = "환불 요청",
            )

        // when & then
        assertThatThrownBy { refundOrderService.refundOrder(command) }
            .isInstanceOf(OrderException.CannotRefundOrder::class.java)
    }

    @Test
    @DisplayName("존재하지 않는 주문 환불 시 OrderNotFound 예외 발생")
    fun refundOrder_withNonExistentOrder_shouldThrowOrderNotFound() {
        // given
        val nonExistentOrderId = UUID.randomUUID()
        val command =
            RefundOrderCommand(
                orderId = nonExistentOrderId,
                requestUserId = CUSTOMER_USER_1,
                refundReason = "환불",
            )

        // when & then
        assertThatThrownBy { refundOrderService.refundOrder(command) }
            .isInstanceOf(OrderException.OrderNotFound::class.java)
    }

    @Test
    @DisplayName("다른 사용자의 주문 환불 시 OrderAccessDenied 예외 발생")
    fun refundOrder_withOtherUsersOrder_shouldThrowOrderAccessDenied() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 환불 시도
        val command =
            RefundOrderCommand(
                orderId = orderOtherUser,
                requestUserId = CUSTOMER_USER_1,
                refundReason = "환불",
            )

        // when & then
        assertThatThrownBy { refundOrderService.refundOrder(command) }
            .isInstanceOf(OrderException.OrderAccessDenied::class.java)
    }

    @Test
    @DisplayName("환불 사유 없이 환불 성공")
    fun refundOrder_withoutRefundReason_shouldSucceed() {
        // given
        val command =
            RefundOrderCommand(
                orderId = orderDelivered,
                requestUserId = CUSTOMER_USER_1,
                refundReason = null,
            )

        // when
        val result = refundOrderService.refundOrder(command)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.REFUND_COMPLETED)
        assertThat(result.refundReason).isNull()
    }
}
