package com.groom.order.adapter.inbound.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.groom.order.adapter.inbound.web.dto.CancelOrderRequest
import com.groom.order.adapter.inbound.web.dto.CreateOrderRequest
import com.groom.order.adapter.inbound.web.dto.RefundOrderRequest
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.common.util.IstioHeaderExtractor
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
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
 * OrderCommandController 비즈니스 로직 통합 테스트
 *
 * TODO: 이벤트 기반 아키텍처에 맞게 재작성 필요
 *
 * 재작성 방향:
 * 1. 주문 생성 테스트
 *    - 주문 생성 API 호출 → 주문이 ORDER_CREATED 상태로 DB에 저장됨
 *    - order.created 이벤트가 Kafka로 발행됨 (KafkaTestHelper로 검증)
 *
 * 2. 외부 이벤트 수신 테스트 (KafkaTestHelper 사용)
 *    - stock.reserved 이벤트 수신 → 주문 상태가 ORDER_CONFIRMED로 변경
 *    - payment.completed 이벤트 수신 → 주문 상태가 PAYMENT_COMPLETED로 변경
 *
 * 3. 주문 취소/환불 테스트
 *    - 취소/환불 API 호출 → 상태 변경 + order.cancelled 이벤트 발행
 *
 * Note: Store 검증은 TestStoreClient로 처리됨 (HTTP 호출 Mock)
 *
 * @see com.groom.order.common.kafka.KafkaTestHelper 외부 이벤트 시뮬레이션
 * @see com.groom.order.adapter.outbound.client.TestStoreClient Store 서비스 Mock
 */
@Disabled("이벤트 기반 아키텍처에 맞게 재작성 필요")
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
@DisplayName("주문 명령(Command) 컨트롤러 비즈니스 로직 통합 테스트")
class OrderCommandControllerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    @Autowired
    private lateinit var entityManager: EntityManager

    companion object {
        // Test Users
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val CUSTOMER_USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")

        // Test Stores
        private val STORE_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val STORE_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000002")

        // Test Products
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        private val PRODUCT_KEYBOARD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")
        private val PRODUCT_LOW_STOCK = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003")

        // Test Orders (will be created dynamically)
        private val ORDER_STOCK_RESERVED = UUID.randomUUID()
        private val ORDER_PAYMENT_COMPLETED = UUID.randomUUID()
        private val ORDER_DELIVERED = UUID.randomUUID()
        private val ORDER_SHIPPED = UUID.randomUUID()
        private val ORDER_USER2 = UUID.randomUUID()
    }

    @BeforeEach
    fun setUp() {
        // 테스트용 주문 생성 (각기 다른 상태로 설정)
        createTestOrders()
    }

    /**
     * 테스트용 주문을 생성하고 각기 다른 상태로 설정합니다.
     * - ORDER_STOCK_RESERVED: STOCK_RESERVED 상태 (ORD-TEST-001)
     * - ORDER_PAYMENT_COMPLETED: PAYMENT_COMPLETED 상태 (ORD-TEST-002)
     * - ORDER_DELIVERED: DELIVERED 상태 (ORD-TEST-003)
     * - ORDER_SHIPPED: SHIPPED 상태 (ORD-TEST-004)
     * - ORDER_USER2: STOCK_RESERVED 상태, customer2 소유 (ORD-TEST-005)
     */
    private fun createTestOrders() {
        transactionApplier.applyPrimaryTransaction {
            val now = LocalDateTime.now()

            // 1. ORDER_STOCK_RESERVED (customer1, STOCK_RESERVED)
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                         reservation_id, expires_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'ORD-TEST-001', 'STOCK_RESERVED',
                            '{}',
                            '[{"status":"STOCK_RESERVED","timestamp":"$now","description":"재고 예약됨"}]',
                            '취소 테스트용', ?, NOW() + INTERVAL '10 minutes', NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, ORDER_STOCK_RESERVED)
                .setParameter(2, CUSTOMER_USER_1)
                .setParameter(3, STORE_1)
                .setParameter(4, UUID.randomUUID().toString())
                .executeUpdate()

            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                              created_at, updated_at)
                    VALUES (?, ?, ?, '무선 마우스', 50000, 2, NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, ORDER_STOCK_RESERVED)
                .setParameter(3, PRODUCT_MOUSE)
                .executeUpdate()

            // 2. ORDER_PAYMENT_COMPLETED (customer1, PAYMENT_COMPLETED)
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                         payment_id, created_at, updated_at)
                    VALUES (?, ?, ?, 'ORD-TEST-002', 'PAYMENT_COMPLETED',
                            '{}',
                            '[{"status":"PAYMENT_COMPLETED","timestamp":"$now","description":"결제 완료"}]',
                            '취소 테스트용', ?, NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, ORDER_PAYMENT_COMPLETED)
                .setParameter(2, CUSTOMER_USER_1)
                .setParameter(3, STORE_1)
                .setParameter(4, UUID.randomUUID())
                .executeUpdate()

            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                              created_at, updated_at)
                    VALUES (?, ?, ?, '기계식 키보드', 120000, 1, NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, ORDER_PAYMENT_COMPLETED)
                .setParameter(3, PRODUCT_KEYBOARD)
                .executeUpdate()

            // 3. ORDER_DELIVERED (customer1, DELIVERED)
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                         payment_id, confirmed_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'ORD-TEST-003', 'DELIVERED',
                            '{}',
                            '[{"status":"DELIVERED","timestamp":"$now","description":"배송 완료"}]',
                            '환불 테스트용', ?, NOW() - INTERVAL '3 days', NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, ORDER_DELIVERED)
                .setParameter(2, CUSTOMER_USER_1)
                .setParameter(3, STORE_1)
                .setParameter(4, UUID.randomUUID())
                .executeUpdate()

            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                              created_at, updated_at)
                    VALUES (?, ?, ?, '무선 마우스', 50000, 2, NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, ORDER_DELIVERED)
                .setParameter(3, PRODUCT_MOUSE)
                .executeUpdate()

            // 4. ORDER_SHIPPED (customer1, SHIPPED)
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                         payment_id, confirmed_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'ORD-TEST-004', 'SHIPPED',
                            '{}',
                            '[{"status":"SHIPPED","timestamp":"$now","description":"배송 중"}]',
                            '취소 실패 테스트용', ?, NOW() - INTERVAL '2 days', NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, ORDER_SHIPPED)
                .setParameter(2, CUSTOMER_USER_1)
                .setParameter(3, STORE_1)
                .setParameter(4, UUID.randomUUID())
                .executeUpdate()

            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                              created_at, updated_at)
                    VALUES (?, ?, ?, '무선 마우스', 50000, 1, NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, ORDER_SHIPPED)
                .setParameter(3, PRODUCT_MOUSE)
                .executeUpdate()

            // 5. ORDER_USER2 (customer2, STOCK_RESERVED) - 권한 테스트용
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                         reservation_id, expires_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'ORD-TEST-005', 'STOCK_RESERVED',
                            '{}',
                            '[{"status":"STOCK_RESERVED","timestamp":"$now","description":"재고 예약됨"}]',
                            '권한 테스트용', ?, NOW() + INTERVAL '10 minutes', NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, ORDER_USER2)
                .setParameter(2, CUSTOMER_USER_2)
                .setParameter(3, STORE_1)
                .setParameter(4, UUID.randomUUID().toString())
                .executeUpdate()

            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                              created_at, updated_at)
                    VALUES (?, ?, ?, '기계식 키보드', 120000, 1, NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, ORDER_USER2)
                .setParameter(3, PRODUCT_KEYBOARD)
                .executeUpdate()

            entityManager.flush()
        }
    }

    @AfterEach
    fun tearDown() {
        // 테스트 데이터 정리는 SQL 스크립트에서 처리
    }

    // ========== POST /api/v1/orders (주문 생성) ==========

    @Test
    @DisplayName("POST /api/v1/orders - 단일 상품 주문 생성 성공")
    fun createOrder_withSingleProduct_shouldSucceed() {
        // given
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 2,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-single-${UUID.randomUUID()}",
                note = "빠른 배송 부탁드립니다",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.orderNumber").exists())
            .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
            .andExpect(jsonPath("$.totalAmount").value(58000.00)) // 29000 * 2
            .andExpect(jsonPath("$.reservationId").exists())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_MOUSE.toString()))
            .andExpect(jsonPath("$.items[0].productName").value("Gaming Mouse"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(29000.00))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
    }

    @Test
    @DisplayName("POST /api/v1/orders - 여러 상품 주문 생성 성공")
    fun createOrder_withMultipleProducts_shouldSucceed() {
        // given
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
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
                note = null,
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
            .andExpect(jsonPath("$.totalAmount").value(118000.00)) // 29000 + 89000
            .andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    @DisplayName("POST /api/v1/orders - 멱등성 키로 중복 주문 방지 확인")
    fun createOrder_withDuplicateIdempotencyKey_shouldReturnExistingOrder() {
        // given
        val idempotencyKey = "test-key-idempotent-${UUID.randomUUID()}"
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
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
                        .header(
                            IstioHeaderExtractor.USER_ID_HEADER,
                            CUSTOMER_USER_1.toString(),
                        ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andReturn()

        val firstOrderId = objectMapper.readTree(firstResponse.response.contentAsString).get("orderId").asText()

        // when & then: 같은 멱등성 키로 두 번째 주문 시도
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").value(firstOrderId)) // 같은 주문 ID 반환
    }

    @Test
    @DisplayName("POST /api/v1/orders - 존재하지 않는 상점으로 주문 시도 시 실패")
    fun createOrder_withNonExistentStore_shouldFail() {
        // given
        val nonExistentStoreId = UUID.randomUUID()
        val request =
            CreateOrderRequest(
                storeId = nonExistentStoreId,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 1,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-invalid-store-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("POST /api/v1/orders - 존재하지 않는 상품으로 주문 시도 시 실패")
    fun createOrder_withNonExistentProduct_shouldFail() {
        // given
        val nonExistentProductId = UUID.randomUUID()
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = nonExistentProductId,
                            productName = "Unknown Product",
                            quantity = 1,
                            unitPrice = BigDecimal("10000"),
                        ),
                    ),
                idempotencyKey = "test-key-invalid-product-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("POST /api/v1/orders - 재고 부족 상품 주문 시도 시 실패")
    fun createOrder_withInsufficientStock_shouldFail() {
        // given: 재고가 2개인 상품을 3개 주문 시도
        // PRODUCT_LOW_STOCK은 STORE_2에 속해 있음 (TestProductClient 참조)
        val request =
            CreateOrderRequest(
                storeId = STORE_2,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_LOW_STOCK,
                            productName = "Low Stock Item",
                            quantity = 3,
                            unitPrice = BigDecimal("15000"),
                        ),
                    ),
                idempotencyKey = "test-key-low-stock-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isConflict) // 409 Conflict
    }

    // ========== PATCH /api/v1/orders/{orderId}/cancel (주문 취소) ==========

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - STOCK_RESERVED 상태 주문 취소 성공")
    fun cancelOrder_withStockReservedStatus_shouldSucceed() {
        // given
        val request = CancelOrderRequest(cancelReason = "상품이 마음에 들지 않아요")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_STOCK_RESERVED/cancel")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(ORDER_STOCK_RESERVED.toString()))
            .andExpect(jsonPath("$.orderNumber").value("ORD-TEST-001"))
            .andExpect(jsonPath("$.status").value("ORDER_CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists())
            .andExpect(jsonPath("$.cancelReason").value("상품이 마음에 들지 않아요"))
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - PAYMENT_COMPLETED 상태 주문 취소 시도 시 실패")
    fun cancelOrder_withPaymentCompletedStatus_shouldFail() {
        // given: PAYMENT_COMPLETED 이후에는 취소 불가능 (반품/환불만 가능)
        val request = CancelOrderRequest(cancelReason = "단순 변심")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/cancel")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isConflict) // 409 Conflict - 리소스 상태 충돌
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 배송 중인 주문(SHIPPED) 취소 시도 시 실패")
    fun cancelOrder_withShippedStatus_shouldFail() {
        // given
        val request = CancelOrderRequest(cancelReason = "취소하고 싶어요")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_SHIPPED/cancel")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isConflict) // 409 Conflict - 리소스 상태 충돌
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 존재하지 않는 주문 취소 시도 시 실패")
    fun cancelOrder_withNonExistentOrder_shouldFail() {
        // given
        val nonExistentOrderId = UUID.randomUUID()
        val request = CancelOrderRequest(cancelReason = "취소 사유")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$nonExistentOrderId/cancel")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 다른 사용자의 주문 취소 시도 시 실패")
    fun cancelOrder_withOtherUsersOrder_shouldFail() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 취소 시도
        val request = CancelOrderRequest(cancelReason = "취소")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_USER2/cancel")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isForbidden) // 403 Forbidden
    }

    // ========== PATCH /api/v1/orders/{orderId}/refund (주문 환불) ==========

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - DELIVERED 상태 주문 환불 성공")
    fun refundOrder_withDeliveredStatus_shouldSucceed() {
        // given
        val request = RefundOrderRequest(refundReason = "상품이 설명과 달라요")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_DELIVERED/refund")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(ORDER_DELIVERED.toString()))
            .andExpect(jsonPath("$.orderNumber").value("ORD-TEST-003"))
            .andExpect(jsonPath("$.status").value("REFUND_COMPLETED"))
            .andExpect(jsonPath("$.refundId").exists())
            .andExpect(jsonPath("$.refundAmount").value(100000.00)) // 50000 * 2
            .andExpect(jsonPath("$.refundReason").value("상품이 설명과 달라요"))
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - PAYMENT_COMPLETED 상태 주문 환불 시도 시 실패")
    fun refundOrder_withPaymentCompletedStatus_shouldFail() {
        // given
        val request = RefundOrderRequest(refundReason = "환불 요청")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/refund")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest) // 400 Bad Request
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 존재하지 않는 주문 환불 시도 시 실패")
    fun refundOrder_withNonExistentOrder_shouldFail() {
        // given
        val nonExistentOrderId = UUID.randomUUID()
        val request = RefundOrderRequest(refundReason = "환불 사유")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$nonExistentOrderId/refund")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 다른 사용자의 주문 환불 시도 시 실패")
    fun refundOrder_withOtherUsersOrder_shouldFail() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 환불 시도
        val request = RefundOrderRequest(refundReason = "환불")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_USER2/refund")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isForbidden) // 403 Forbidden
    }

    // ========== 추가 분기 테스트 ==========

    @Test
    @DisplayName("POST /api/v1/orders - 다른 스토어의 상품 주문 시도 시 실패")
    fun createOrder_withProductFromDifferentStore_shouldFail() {
        // given: STORE_2의 상품을 STORE_1에서 주문 시도
        // 이 테스트는 PRODUCT_MOUSE가 STORE_1에 속해있는 경우,
        // STORE_2에서 해당 상품을 주문하려고 할 때 실패해야 함
        val request =
            CreateOrderRequest(
                storeId = STORE_2,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE, // STORE_1의 상품
                            productName = "Gaming Mouse",
                            quantity = 1,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-different-store-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest) // 400 Bad Request - 상품이 해당 스토어에 속하지 않음
    }

    @Test
    @DisplayName("POST /api/v1/orders - 빈 상품 목록으로 주문 시도 시 실패")
    fun createOrder_withEmptyItems_shouldFail() {
        // given
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items = emptyList(),
                idempotencyKey = "test-key-empty-items-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest) // 400 Bad Request
    }

    @Test
    @DisplayName("POST /api/v1/orders - 수량이 0인 상품 주문 시도 시 실패")
    fun createOrder_withZeroQuantity_shouldFail() {
        // given
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 0,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-zero-quantity-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest) // 400 Bad Request
    }

    @Test
    @DisplayName("POST /api/v1/orders - 음수 수량 주문 시도 시 실패")
    fun createOrder_withNegativeQuantity_shouldFail() {
        // given
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = -1,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-negative-quantity-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest) // 400 Bad Request
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 취소 사유 없이 주문 취소 성공")
    fun cancelOrder_withoutCancelReason_shouldSucceed() {
        // given
        val request = CancelOrderRequest(cancelReason = null)

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_STOCK_RESERVED/cancel")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ORDER_CANCELLED"))
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 환불 사유 없이 환불 성공")
    fun refundOrder_withoutRefundReason_shouldSucceed() {
        // given
        val request = RefundOrderRequest(refundReason = null)

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_DELIVERED/refund")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REFUND_COMPLETED"))
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 배송 중(SHIPPED) 주문 환불 시도 시 실패")
    fun refundOrder_withShippedStatus_shouldFail() {
        // given: 배송 중인 주문은 환불 불가 (배송 완료만 가능)
        val request = RefundOrderRequest(refundReason = "환불 요청")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_SHIPPED/refund")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest) // 400 Bad Request - 환불 불가능한 상태
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - PREPARING 상태 주문 취소 성공")
    fun cancelOrder_withPreparingStatus_shouldSucceed() {
        // given: PREPARING 상태의 주문 생성
        val orderPreparing = UUID.randomUUID()
        transactionApplier.applyPrimaryTransaction {
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note,
                                         payment_id, confirmed_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'ORD-TEST-PREP', 'PREPARING',
                            '{}',
                            '[{"status":"PREPARING","timestamp":"${LocalDateTime.now()}","description":"준비 중"}]',
                            '준비 중 취소 테스트', ?, NOW(), NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, orderPreparing)
                .setParameter(2, CUSTOMER_USER_1)
                .setParameter(3, STORE_1)
                .setParameter(4, UUID.randomUUID())
                .executeUpdate()

            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity,
                                              created_at, updated_at)
                    VALUES (?, ?, ?, '무선 마우스', 50000, 1, NOW(), NOW())
                    """.trimIndent(),
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, orderPreparing)
                .setParameter(3, PRODUCT_MOUSE)
                .executeUpdate()

            entityManager.flush()
        }

        val request = CancelOrderRequest(cancelReason = "준비 중 취소")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$orderPreparing/cancel")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ORDER_CANCELLED"))
    }

    @Test
    @DisplayName("POST /api/v1/orders - 메모가 있는 주문 생성 성공")
    fun createOrder_withNote_shouldSucceed() {
        // given
        val noteText = "경비실에 맡겨주세요. 부재 시 문 앞에 놔주세요."
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 1,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-with-note-${UUID.randomUUID()}",
                note = noteText,
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.note").value(noteText))
    }

    @Test
    @DisplayName("POST /api/v1/orders - 메모 없이 주문 생성 성공")
    fun createOrder_withoutNote_shouldSucceed() {
        // given
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 1,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-without-note-${UUID.randomUUID()}",
                note = null,
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(
                        IstioHeaderExtractor.USER_ID_HEADER,
                        CUSTOMER_USER_1.toString(),
                    ).header(IstioHeaderExtractor.USER_ROLE_HEADER, "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
    }
}
