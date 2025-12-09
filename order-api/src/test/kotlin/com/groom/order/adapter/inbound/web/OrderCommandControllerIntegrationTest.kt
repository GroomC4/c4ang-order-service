package com.groom.order.adapter.inbound.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.groom.order.adapter.inbound.web.dto.CancelOrderRequest
import com.groom.order.adapter.inbound.web.dto.CreateOrderRequest
import com.groom.order.adapter.inbound.web.dto.RefundOrderRequest
import com.groom.order.adapter.outbound.client.TestStoreClient
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier

import com.groom.order.domain.model.OrderStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * OrderCommandController 비즈니스 로직 통합 테스트 (이벤트 기반 아키텍처)
 *
 * 테스트 범위:
 * 1. 주문 생성 API → ORDER_CREATED 상태로 저장
 * 2. 멱등성 검증
 * 3. 주문 취소 API (ORDER_CREATED, ORDER_CONFIRMED 상태에서만 가능)
 * 4. 주문 환불 API (DELIVERED 상태에서만 가능)
 *
 * Note:
 * - Store 검증: TestStoreClient (Test Bean)
 * - Product/재고 관리: Product Service 책임 (이벤트 기반)
 * - 인증/인가: OrderCommandControllerAuthorizationIntegrationTest에서 별도 검증
 * - Kafka Consumer 통합 테스트는 안정성 문제로 단위 테스트에서 검증
 */
@AutoConfigureMockMvc
@SqlGroup(
    Sql(
        scripts = ["/sql/cleanup-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/init-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    ),
    Sql(
        scripts = ["/sql/cleanup-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
    ),
)
@DisplayName("주문 명령(Command) 컨트롤러 비즈니스 로직 통합 테스트 (이벤트 기반)")
class OrderCommandControllerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    companion object {
        // Test Users
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val CUSTOMER_USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")

        // Test Products
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        private val PRODUCT_KEYBOARD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")

        // Test Orders (동적 생성)
        private var ORDER_CREATED_ID = UUID.randomUUID()
        private var ORDER_CONFIRMED_ID = UUID.randomUUID()
        private var ORDER_PAYMENT_COMPLETED_ID = UUID.randomUUID()
        private var ORDER_DELIVERED_ID = UUID.randomUUID()
        private var ORDER_SHIPPED_ID = UUID.randomUUID()
        private var ORDER_USER2_ID = UUID.randomUUID()
    }

    @BeforeEach
    fun setUp() {
        // 테스트용 주문 생성 (각기 다른 상태)
        ORDER_CREATED_ID = UUID.randomUUID()
        ORDER_CONFIRMED_ID = UUID.randomUUID()
        ORDER_PAYMENT_COMPLETED_ID = UUID.randomUUID()
        ORDER_DELIVERED_ID = UUID.randomUUID()
        ORDER_SHIPPED_ID = UUID.randomUUID()
        ORDER_USER2_ID = UUID.randomUUID()

        createTestOrders()
    }

    private fun createTestOrders() {
        transactionApplier.applyPrimaryTransaction {
            val now = LocalDateTime.now()

            // 1. ORDER_CREATED 상태 (재고 예약 대기 중)
            createOrder(
                id = ORDER_CREATED_ID,
                userId = CUSTOMER_USER_1,
                orderNumber = "ORD-TEST-001",
                status = "ORDER_CREATED",
                productId = PRODUCT_MOUSE,
            )

            // 2. ORDER_CONFIRMED 상태 (재고 예약 완료)
            createOrder(
                id = ORDER_CONFIRMED_ID,
                userId = CUSTOMER_USER_1,
                orderNumber = "ORD-TEST-002",
                status = "ORDER_CONFIRMED",
                productId = PRODUCT_MOUSE,
            )

            // 3. PAYMENT_COMPLETED 상태
            createOrder(
                id = ORDER_PAYMENT_COMPLETED_ID,
                userId = CUSTOMER_USER_1,
                orderNumber = "ORD-TEST-003",
                status = "PAYMENT_COMPLETED",
                productId = PRODUCT_KEYBOARD,
                paymentId = UUID.randomUUID(),
            )

            // 4. DELIVERED 상태 (환불 가능)
            createOrder(
                id = ORDER_DELIVERED_ID,
                userId = CUSTOMER_USER_1,
                orderNumber = "ORD-TEST-004",
                status = "DELIVERED",
                productId = PRODUCT_MOUSE,
                paymentId = UUID.randomUUID(),
                confirmedAt = now.minusDays(3),
            )

            // 5. SHIPPED 상태 (취소/환불 불가)
            createOrder(
                id = ORDER_SHIPPED_ID,
                userId = CUSTOMER_USER_1,
                orderNumber = "ORD-TEST-005",
                status = "SHIPPED",
                productId = PRODUCT_MOUSE,
                paymentId = UUID.randomUUID(),
                confirmedAt = now.minusDays(2),
            )

            // 6. 다른 사용자 소유 주문 (권한 테스트용)
            createOrder(
                id = ORDER_USER2_ID,
                userId = CUSTOMER_USER_2,
                orderNumber = "ORD-TEST-006",
                status = "ORDER_CREATED",
                productId = PRODUCT_KEYBOARD,
            )

            entityManager.flush()
        }
    }

    private fun createOrder(
        id: UUID,
        userId: UUID,
        orderNumber: String,
        status: String,
        productId: UUID,
        paymentId: UUID? = null,
        confirmedAt: LocalDateTime? = null,
    ) {
        val paymentIdValue = paymentId?.let { "?" } ?: "NULL"
        val confirmedAtValue = confirmedAt?.let { "?" } ?: "NULL"

        val query =
            entityManager
                .createNativeQuery(
                    """
                INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                     payment_id, confirmed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, '{}', '[]', '테스트 주문',
                        ${if (paymentId != null) "?" else "NULL"},
                        ${if (confirmedAt != null) "?" else "NULL"},
                        NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, id)
                .setParameter(2, userId)
                .setParameter(3, TestStoreClient.STORE_1)
                .setParameter(4, orderNumber)
                .setParameter(5, status)

        var paramIndex = 6
        if (paymentId != null) {
            query.setParameter(paramIndex++, paymentId)
        }
        if (confirmedAt != null) {
            query.setParameter(paramIndex, confirmedAt)
        }

        query.executeUpdate()

        entityManager
            .createNativeQuery(
                """
            INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                      created_at, updated_at)
            VALUES (?, ?, ?, '테스트 상품', 50000, 1, NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, UUID.randomUUID())
            .setParameter(2, id)
            .setParameter(3, productId)
            .executeUpdate()
    }

    @AfterEach
    fun tearDown() {
        // SQL 스크립트에서 정리
    }

    // ========== POST /api/v1/orders (주문 생성) ==========

    @Nested
    @DisplayName("POST /api/v1/orders - 주문 생성")
    inner class CreateOrderTest {
        @Test
        @DisplayName("단일 상품 주문 생성 시 ORDER_CREATED 상태로 응답한다")
        fun `should return ORDER_CREATED status when creating order`() {
            // given
            val request =
                CreateOrderRequest(
                    storeId = TestStoreClient.STORE_1,
                    items =
                        listOf(
                            CreateOrderRequest.OrderItemRequest(
                                productId = PRODUCT_MOUSE,
                                productName = "Gaming Mouse",
                                quantity = 2,
                                unitPrice = BigDecimal("29000"),
                            ),
                        ),
                    idempotencyKey = "test-key-${UUID.randomUUID()}",
                    note = "빠른 배송 부탁드립니다",
                )

            // when & then
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.status").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(58000.00)) // 29000 * 2
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_MOUSE.toString()))
        }

        @Test
        @DisplayName("여러 상품 주문 생성 시 총액이 정확히 계산된다")
        fun `should calculate total amount correctly for multiple items`() {
            // given
            val request =
                CreateOrderRequest(
                    storeId = TestStoreClient.STORE_1,
                    items =
                        listOf(
                            CreateOrderRequest.OrderItemRequest(
                                productId = PRODUCT_MOUSE,
                                productName = "Gaming Mouse",
                                quantity = 1,
                                unitPrice = BigDecimal("29000"),
                            ),
                            CreateOrderRequest.OrderItemRequest(
                                productId = PRODUCT_KEYBOARD,
                                productName = "Mechanical Keyboard",
                                quantity = 1,
                                unitPrice = BigDecimal("89000"),
                            ),
                        ),
                    idempotencyKey = "test-key-multi-${UUID.randomUUID()}",
                )

            // when & then
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(118000.00))
                .andExpect(jsonPath("$.items.length()").value(2))
        }

        @Test
        @DisplayName("동일한 멱등성 키로 중복 호출 시 기존 주문이 반환된다")
        fun `should return existing order for duplicate idempotency key`() {
            // given
            val idempotencyKey = "test-key-idempotent-${UUID.randomUUID()}"
            val request =
                CreateOrderRequest(
                    storeId = TestStoreClient.STORE_1,
                    items =
                        listOf(
                            CreateOrderRequest.OrderItemRequest(
                                productId = PRODUCT_MOUSE,
                                productName = "Gaming Mouse",
                                quantity = 1,
                                unitPrice = BigDecimal("29000"),
                            ),
                        ),
                    idempotencyKey = idempotencyKey,
                )

            // when: 첫 번째 주문 생성
            val firstResponse =
                mockMvc
                    .perform(
                        post("/api/v1/orders")
                            .header("X-User-Id", CUSTOMER_USER_1.toString())
                            .header("X-User-Role", "CUSTOMER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andExpect(status().isCreated)
                    .andReturn()

            val firstOrderId = objectMapper.readTree(firstResponse.response.contentAsString).get("orderId").asText()

            // when & then: 동일한 멱등성 키로 두 번째 주문 시도
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.orderId").value(firstOrderId))
        }

        // Note: 이벤트 기반 아키텍처에서 Order Service는 Store Service에 직접 접근하지 않습니다.
        // 스토어 검증은 주문 생성 시점에 수행되지 않으며, 후속 이벤트 처리에서 검증됩니다.

        @Test
        @DisplayName("빈 상품 목록으로 주문 시 400 BadRequest 응답")
        fun `should return 400 when items list is empty`() {
            // given
            val request =
                CreateOrderRequest(
                    storeId = TestStoreClient.STORE_1,
                    items = emptyList(),
                    idempotencyKey = "test-key-empty-${UUID.randomUUID()}",
                )

            // when & then
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isBadRequest)
        }
    }

    // ========== PATCH /api/v1/orders/{orderId}/cancel (주문 취소) ==========

    @Nested
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 주문 취소")
    inner class CancelOrderTest {
        @Test
        @DisplayName("ORDER_CREATED 상태 주문 취소 성공")
        fun `should cancel order with ORDER_CREATED status`() {
            // given
            val request = CancelOrderRequest(cancelReason = "단순 변심")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_CREATED_ID/cancel")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.orderId").value(ORDER_CREATED_ID.toString()))
                .andExpect(jsonPath("$.status").value("ORDER_CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists())
        }

        @Test
        @DisplayName("ORDER_CONFIRMED 상태 주문 취소 성공")
        fun `should cancel order with ORDER_CONFIRMED status`() {
            // given
            val request = CancelOrderRequest(cancelReason = "상품 변경")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_CONFIRMED_ID/cancel")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ORDER_CANCELLED"))
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED 상태 주문 취소 시 409 Conflict 응답")
        fun `should return 409 when cancelling PAYMENT_COMPLETED order`() {
            // given: PAYMENT_COMPLETED 이후에는 취소 불가 (환불만 가능)
            val request = CancelOrderRequest(cancelReason = "취소 요청")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED_ID/cancel")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isConflict)
        }

        @Test
        @DisplayName("SHIPPED 상태 주문 취소 시 409 Conflict 응답")
        fun `should return 409 when cancelling SHIPPED order`() {
            // given
            val request = CancelOrderRequest(cancelReason = "취소 요청")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_SHIPPED_ID/cancel")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isConflict)
        }

        @Test
        @DisplayName("다른 사용자의 주문 취소 시 403 Forbidden 응답")
        fun `should return 403 when cancelling other user order`() {
            // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 취소 시도
            val request = CancelOrderRequest(cancelReason = "취소")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_USER2_ID/cancel")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isForbidden)
        }

        @Test
        @DisplayName("존재하지 않는 주문 취소 시 404 NotFound 응답")
        fun `should return 404 when order not found`() {
            // given
            val nonExistentOrderId = UUID.randomUUID()
            val request = CancelOrderRequest(cancelReason = "취소")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$nonExistentOrderId/cancel")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isNotFound)
        }
    }

    // ========== PATCH /api/v1/orders/{orderId}/refund (주문 환불) ==========

    @Nested
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 주문 환불")
    inner class RefundOrderTest {
        @Test
        @DisplayName("DELIVERED 상태 주문 환불 성공")
        fun `should refund order with DELIVERED status`() {
            // given
            val request = RefundOrderRequest(refundReason = "상품 불량")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_DELIVERED_ID/refund")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.orderId").value(ORDER_DELIVERED_ID.toString()))
                .andExpect(jsonPath("$.status").value("REFUND_COMPLETED"))
                .andExpect(jsonPath("$.refundId").exists())
        }

        @Test
        @DisplayName("SHIPPED 상태 주문 환불 시 400 BadRequest 응답")
        fun `should return 400 when refunding SHIPPED order`() {
            // given: 배송 중인 주문은 환불 불가 (배송 완료 후에만 가능)
            val request = RefundOrderRequest(refundReason = "환불 요청")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_SHIPPED_ID/refund")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("다른 사용자의 주문 환불 시 403 Forbidden 응답")
        fun `should return 403 when refunding other user order`() {
            // given
            val request = RefundOrderRequest(refundReason = "환불")

            // when & then
            mockMvc
                .perform(
                    patch("/api/v1/orders/$ORDER_USER2_ID/refund")
                        .header("X-User-Id", CUSTOMER_USER_1.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andDo(print())
                .andExpect(status().isForbidden)
        }
    }

    // Note: Kafka Consumer 통합 테스트는 Testcontainers Kafka의 Consumer 그룹 재균형 및
    // 파티션 할당 지연으로 인해 안정적인 테스트가 어렵습니다.
    // Kafka Consumer의 메시지 처리 로직은 단위 테스트에서 검증됩니다:
    // - StockReservedKafkaListenerTest
    // - StockReservationFailedKafkaListenerTest
    // - PaymentCompletedKafkaListenerTest
    // - PaymentFailedKafkaListenerTest
}
