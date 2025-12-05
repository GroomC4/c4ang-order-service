package com.groom.order.application.service

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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID

/**
 * OrderInternalService 통합 테스트
 *
 * Internal API 서비스의 전체 플로우를 검증합니다.
 * - 주문 조회
 * - 결제 대기 상태 변경
 * - 결제 존재 여부 확인
 */
@DisplayName("OrderInternalService 통합 테스트")
class OrderInternalServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var orderInternalService: OrderInternalService

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    @Autowired
    private lateinit var entityManager: EntityManager

    companion object {
        private val CUSTOMER_USER = UUID.fromString("11111111-1111-1111-1111-111111111111")
        private val STORE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        private val PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    }

    private var orderConfirmed: UUID = UUID.randomUUID()
    private var orderCreated: UUID = UUID.randomUUID()
    private var orderWithPayment: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        createTestOrders()
    }

    private fun createTestOrders() {
        transactionApplier.applyPrimaryTransaction {
            val now = LocalDateTime.now()

            // 1. ORDER_CONFIRMED 상태 주문 (결제 연결 가능)
            orderConfirmed = UUID.randomUUID()
            createOrder(
                orderId = orderConfirmed,
                userId = CUSTOMER_USER,
                status = "ORDER_CONFIRMED",
                orderNumber = "ORD-INT-001",
                reservationId = "RES-001",
                expiresAt = now.plusMinutes(10),
            )
            createOrderItem(orderConfirmed, PRODUCT_ID, "테스트 상품", 25000, 2)

            // 2. ORDER_CREATED 상태 주문 (결제 연결 불가)
            orderCreated = UUID.randomUUID()
            createOrder(
                orderId = orderCreated,
                userId = CUSTOMER_USER,
                status = "ORDER_CREATED",
                orderNumber = "ORD-INT-002",
            )
            createOrderItem(orderCreated, PRODUCT_ID, "테스트 상품", 25000, 1)

            // 3. ORDER_CONFIRMED 상태지만 이미 결제가 연결된 주문
            orderWithPayment = UUID.randomUUID()
            createOrder(
                orderId = orderWithPayment,
                userId = CUSTOMER_USER,
                status = "ORDER_CONFIRMED",
                orderNumber = "ORD-INT-003",
                paymentId = UUID.randomUUID(),
            )
            createOrderItem(orderWithPayment, PRODUCT_ID, "테스트 상품", 25000, 1)

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
    ) {
        val expiresAtStr = expiresAt?.let { "'$it'" } ?: "NULL"

        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                     reservation_id, expires_at, payment_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?,
                        '{}',
                        '[{"status":"$status","timestamp":"${LocalDateTime.now()}","description":"테스트"}]',
                        'Internal API 테스트', ?, $expiresAtStr, ?,
                        NOW(), NOW())
                """.trimIndent(),
            )
            .setParameter(1, orderId)
            .setParameter(2, userId)
            .setParameter(3, STORE_ID)
            .setParameter(4, orderNumber)
            .setParameter(5, status)
            .setParameter(6, reservationId)
            .setParameter(7, paymentId)
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
            )
            .setParameter(1, UUID.randomUUID())
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
                    "DELETE FROM p_order_item WHERE order_id IN (SELECT id FROM p_order WHERE order_number LIKE 'ORD-INT-%')",
                )
                .executeUpdate()
            entityManager.createNativeQuery("DELETE FROM p_order WHERE order_number LIKE 'ORD-INT-%'").executeUpdate()
            entityManager.flush()
        }
    }

    @Nested
    @DisplayName("getOrder")
    inner class GetOrderTest {
        @Test
        @DisplayName("주문 조회 성공")
        fun getOrder_shouldReturnOrderInfo() {
            // when
            val result = orderInternalService.getOrder(orderConfirmed)

            // then
            assertThat(result.orderId).isEqualTo(orderConfirmed)
            assertThat(result.userId).isEqualTo(CUSTOMER_USER)
            assertThat(result.orderNumber).isEqualTo("ORD-INT-001")
            assertThat(result.status).isEqualTo(OrderStatus.ORDER_CONFIRMED)
            assertThat(result.totalAmount.toLong()).isEqualTo(50000L) // 25000 * 2
            assertThat(result.items).hasSize(1)
            assertThat(result.items[0].productName).isEqualTo("테스트 상품")
            assertThat(result.items[0].quantity).isEqualTo(2)
            assertThat(result.items[0].unitPrice.toLong()).isEqualTo(25000L)
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회 시 OrderNotFound 예외 발생")
        fun getOrder_withNonExistentOrder_shouldThrowOrderNotFound() {
            // given
            val nonExistentOrderId = UUID.randomUUID()

            // when & then
            assertThatThrownBy { orderInternalService.getOrder(nonExistentOrderId) }
                .isInstanceOf(OrderException.OrderNotFound::class.java)
        }
    }

    @Nested
    @DisplayName("markPaymentPending")
    inner class MarkPaymentPendingTest {
        @Test
        @DisplayName("결제 대기 상태 변경 성공")
        fun markPaymentPending_shouldChangeStatusToPaymentPending() {
            // given
            val paymentId = UUID.randomUUID()

            // when
            val result = orderInternalService.markPaymentPending(orderConfirmed, paymentId)

            // then
            assertThat(result.orderId).isEqualTo(orderConfirmed)
            assertThat(result.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
            assertThat(result.paymentId).isEqualTo(paymentId)

            // DB 검증
            val dbResult = orderInternalService.getOrder(orderConfirmed)
            assertThat(dbResult.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        }

        @Test
        @DisplayName("ORDER_CONFIRMED가 아닌 상태에서 결제 연결 시 OrderStatusInvalid 예외 발생")
        fun markPaymentPending_withInvalidStatus_shouldThrowOrderStatusInvalid() {
            // given
            val paymentId = UUID.randomUUID()

            // when & then
            assertThatThrownBy { orderInternalService.markPaymentPending(orderCreated, paymentId) }
                .isInstanceOf(OrderException.OrderStatusInvalid::class.java)
        }

        @Test
        @DisplayName("이미 결제가 연결된 주문에 결제 연결 시 PaymentAlreadyExists 예외 발생")
        fun markPaymentPending_withExistingPayment_shouldThrowPaymentAlreadyExists() {
            // given
            val newPaymentId = UUID.randomUUID()

            // when & then
            assertThatThrownBy { orderInternalService.markPaymentPending(orderWithPayment, newPaymentId) }
                .isInstanceOf(OrderException.PaymentAlreadyExists::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 주문에 결제 연결 시 OrderNotFound 예외 발생")
        fun markPaymentPending_withNonExistentOrder_shouldThrowOrderNotFound() {
            // given
            val nonExistentOrderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()

            // when & then
            assertThatThrownBy { orderInternalService.markPaymentPending(nonExistentOrderId, paymentId) }
                .isInstanceOf(OrderException.OrderNotFound::class.java)
        }
    }

    @Nested
    @DisplayName("checkHasPayment")
    inner class CheckHasPaymentTest {
        @Test
        @DisplayName("결제가 없는 주문 확인")
        fun checkHasPayment_withNoPayment_shouldReturnFalse() {
            // when
            val result = orderInternalService.checkHasPayment(orderConfirmed)

            // then
            assertThat(result.orderId).isEqualTo(orderConfirmed)
            assertThat(result.hasPayment).isFalse()
        }

        @Test
        @DisplayName("결제가 있는 주문 확인")
        fun checkHasPayment_withPayment_shouldReturnTrue() {
            // when
            val result = orderInternalService.checkHasPayment(orderWithPayment)

            // then
            assertThat(result.orderId).isEqualTo(orderWithPayment)
            assertThat(result.hasPayment).isTrue()
        }

        @Test
        @DisplayName("존재하지 않는 주문 확인 시 OrderNotFound 예외 발생")
        fun checkHasPayment_withNonExistentOrder_shouldThrowOrderNotFound() {
            // given
            val nonExistentOrderId = UUID.randomUUID()

            // when & then
            assertThatThrownBy { orderInternalService.checkHasPayment(nonExistentOrderId) }
                .isInstanceOf(OrderException.OrderNotFound::class.java)
        }
    }
}
