package com.groom.order.application.service

import com.groom.order.application.dto.ListOrdersQuery
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.domain.model.OrderStatus
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID

/**
 * ListOrdersService 통합 테스트
 *
 * 주문 목록 조회 서비스의 전체 플로우를 검증합니다.
 * - 사용자별 주문 목록 조회
 * - 상태별 필터링
 * - 정렬 확인
 */
@DisplayName("ListOrdersService 통합 테스트")
class ListOrdersServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var listOrdersService: ListOrdersService

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    @Autowired
    private lateinit var entityManager: EntityManager

    companion object {
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val CUSTOMER_USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
        private val CUSTOMER_USER_NO_ORDERS = UUID.fromString("99999999-9999-9999-9999-999999999999")
        private val STORE_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
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

            // CUSTOMER_USER_1의 주문들 (다양한 상태와 시간)
            // 1. STOCK_RESERVED (가장 최근)
            orderStockReserved = UUID.randomUUID()
            createOrder(orderStockReserved, CUSTOMER_USER_1, "STOCK_RESERVED", "ORD-LIST-001", now)
            createOrderItem(orderStockReserved, 50000, 2)

            // 2. PAYMENT_COMPLETED (1일 전)
            orderPaymentCompleted = UUID.randomUUID()
            createOrder(orderPaymentCompleted, CUSTOMER_USER_1, "PAYMENT_COMPLETED", "ORD-LIST-002", now.minusDays(1))
            createOrderItem(orderPaymentCompleted, 120000, 1)

            // 3. DELIVERED (5일 전)
            orderDelivered = UUID.randomUUID()
            createOrder(orderDelivered, CUSTOMER_USER_1, "DELIVERED", "ORD-LIST-003", now.minusDays(5))
            createOrderItem(orderDelivered, 50000, 1)

            // 4. ORDER_CANCELLED (3일 전)
            orderCancelled = UUID.randomUUID()
            createOrder(orderCancelled, CUSTOMER_USER_1, "ORDER_CANCELLED", "ORD-LIST-004", now.minusDays(3))
            createOrderItem(orderCancelled, 50000, 1)

            // CUSTOMER_USER_2의 주문
            orderOtherUser = UUID.randomUUID()
            createOrder(orderOtherUser, CUSTOMER_USER_2, "STOCK_RESERVED", "ORD-LIST-005", now)
            createOrderItem(orderOtherUser, 50000, 1)

            entityManager.flush()
        }
    }

    private fun createOrder(
        orderId: UUID,
        userId: UUID,
        status: String,
        orderNumber: String,
        createdAt: LocalDateTime,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?,
                        '{}',
                        '[{"status":"$status","timestamp":"$createdAt","description":"테스트"}]',
                        '목록 조회 테스트', '$createdAt', NOW())
                """.trimIndent(),
            ).setParameter(1, orderId)
            .setParameter(2, userId)
            .setParameter(3, STORE_1)
            .setParameter(4, orderNumber)
            .setParameter(5, status)
            .executeUpdate()
    }

    private fun createOrderItem(
        orderId: UUID,
        unitPrice: Int,
        quantity: Int,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                          created_at, updated_at)
                VALUES (?, ?, ?, '테스트 상품', ?, ?, NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, UUID.randomUUID())
            .setParameter(2, orderId)
            .setParameter(3, PRODUCT_MOUSE)
            .setParameter(4, unitPrice)
            .setParameter(5, quantity)
            .executeUpdate()
    }

    @AfterEach
    fun tearDown() {
        transactionApplier.applyPrimaryTransaction {
            entityManager.createNativeQuery("DELETE FROM p_order_item WHERE order_id IN (SELECT id FROM p_order WHERE order_number LIKE 'ORD-LIST-%')").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM p_order WHERE order_number LIKE 'ORD-LIST-%'").executeUpdate()
            entityManager.flush()
        }
    }

    @Test
    @DisplayName("사용자의 전체 주문 목록 조회 성공")
    fun listOrders_shouldReturnAllOrdersForUser() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = null,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).hasSize(4)
    }

    @Test
    @DisplayName("주문 목록이 최신순으로 정렬되는지 확인")
    fun listOrders_shouldReturnOrdersSortedByCreatedAtDesc() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = null,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).hasSize(4)
        assertThat(result.orders[0].orderNumber).isEqualTo("ORD-LIST-001") // 가장 최근
        assertThat(result.orders[1].orderNumber).isEqualTo("ORD-LIST-002") // 1일 전
        assertThat(result.orders[2].orderNumber).isEqualTo("ORD-LIST-004") // 3일 전
        assertThat(result.orders[3].orderNumber).isEqualTo("ORD-LIST-003") // 5일 전
    }

    @Test
    @DisplayName("STOCK_RESERVED 상태로 필터링 조회")
    fun listOrders_withStockReservedFilter_shouldReturnFilteredOrders() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = OrderStatus.STOCK_RESERVED,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).hasSize(1)
        assertThat(result.orders[0].status).isEqualTo(OrderStatus.STOCK_RESERVED)
        assertThat(result.orders[0].orderNumber).isEqualTo("ORD-LIST-001")
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 상태로 필터링 조회")
    fun listOrders_withPaymentCompletedFilter_shouldReturnFilteredOrders() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = OrderStatus.PAYMENT_COMPLETED,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).hasSize(1)
        assertThat(result.orders[0].status).isEqualTo(OrderStatus.PAYMENT_COMPLETED)
    }

    @Test
    @DisplayName("DELIVERED 상태로 필터링 조회")
    fun listOrders_withDeliveredFilter_shouldReturnFilteredOrders() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = OrderStatus.DELIVERED,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).hasSize(1)
        assertThat(result.orders[0].status).isEqualTo(OrderStatus.DELIVERED)
    }

    @Test
    @DisplayName("ORDER_CANCELLED 상태로 필터링 조회")
    fun listOrders_withCancelledFilter_shouldReturnFilteredOrders() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = OrderStatus.ORDER_CANCELLED,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).hasSize(1)
        assertThat(result.orders[0].status).isEqualTo(OrderStatus.ORDER_CANCELLED)
    }

    @Test
    @DisplayName("해당 상태의 주문이 없을 때 빈 목록 반환")
    fun listOrders_withNoMatchingStatus_shouldReturnEmptyList() {
        // given: PREPARING 상태 주문이 없음
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = OrderStatus.PREPARING,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).isEmpty()
    }

    @Test
    @DisplayName("주문이 없는 사용자의 목록 조회 - 빈 목록 반환")
    fun listOrders_withUserHavingNoOrders_shouldReturnEmptyList() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_NO_ORDERS,
            status = null,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).isEmpty()
    }

    @Test
    @DisplayName("다른 사용자의 주문은 조회되지 않음")
    fun listOrders_shouldNotReturnOtherUsersOrders() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = null,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then: CUSTOMER_USER_2의 주문은 포함되지 않음
        assertThat(result.orders).hasSize(4)
        assertThat(result.orders.map { it.orderNumber }).doesNotContain("ORD-LIST-005")
    }

    @Test
    @DisplayName("각 주문에 필수 필드가 포함되는지 확인")
    fun listOrders_shouldContainRequiredFields() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = null,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).isNotEmpty()
        val firstOrder = result.orders[0]
        assertThat(firstOrder.orderId).isNotNull()
        assertThat(firstOrder.orderNumber).isNotNull()
        assertThat(firstOrder.status).isNotNull()
        assertThat(firstOrder.totalAmount).isNotNull()
        assertThat(firstOrder.itemCount).isGreaterThan(0)
        assertThat(firstOrder.createdAt).isNotNull()
    }

    @Test
    @DisplayName("주문 항목 개수가 정확한지 확인")
    fun listOrders_shouldReturnCorrectItemCount() {
        // given
        val query = ListOrdersQuery(
            requestUserId = CUSTOMER_USER_1,
            status = OrderStatus.STOCK_RESERVED,
        )

        // when
        val result = listOrdersService.listOrders(query)

        // then
        assertThat(result.orders).hasSize(1)
        assertThat(result.orders[0].itemCount).isEqualTo(1) // 1개의 OrderItem
    }
}
