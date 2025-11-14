package com.groom.order.presentation.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.groom.order.common.TransactionApplier
import com.groom.order.common.annotation.IntegrationTest
import com.groom.order.presentation.web.dto.CancelOrderRequest
import com.groom.order.presentation.web.dto.CreateOrderRequest
import com.groom.order.presentation.web.dto.RefundOrderRequest
import com.groom.ecommerce.user.application.dto.RegisterCustomerCommand
import com.groom.ecommerce.user.application.service.RegisterCustomerService
import com.groom.ecommerce.user.infrastructure.repository.RefreshTokenRepositoryImpl
import com.groom.ecommerce.user.infrastructure.repository.UserRepositoryImpl
import com.groom.ecommerce.user.presentation.web.dto.LoginRequest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

/**
 * OrderCommandController 비즈니스 로직 통합 테스트
 *
 * 주문 생성, 취소, 환불 API의 비즈니스 로직을 검증합니다.
 * - 인증/인가 테스트는 별도의 Authorization 테스트에서 수행
 * - Redis 재고 예약 로직 포함
 * - 실제 데이터베이스와 통합 테스트
 */
@IntegrationTest
@SpringBootTest
@AutoConfigureMockMvc
@SqlGroup(
    Sql(
        scripts = ["/sql/order/cleanup-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/order/init-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    ),
    Sql(
        scripts = ["/sql/order/cleanup-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
    ),
)
@DisplayName("주문 명령(Command) 컨트롤러 비즈니스 로직 통합 테스트")
class OrderCommandControllerIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var redissonClient: RedissonClient

    @Autowired
    private lateinit var registerCustomerService: RegisterCustomerService

    @Autowired
    private lateinit var userRepository: UserRepositoryImpl

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepositoryImpl

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    @Autowired
    private lateinit var entityManager: EntityManager

    companion object {
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

    private lateinit var customerToken: String
    private lateinit var customer2Token: String
    private lateinit var customer1Id: String
    private lateinit var customer2Id: String
    private val createdEmails = mutableListOf<String>()
    private val createdOrderIds = mutableListOf<UUID>()

    @BeforeEach
    fun setUp() {
        createdEmails.clear()
        createdOrderIds.clear()

        // CUSTOMER_USER_1 생성 및 로그인
        val customer1 =
            registerCustomerService.register(
                RegisterCustomerCommand(
                    username = "customer1",
                    email = "customer1-order-test@example.com",
                    rawPassword = "password123!",
                    defaultAddress = "서울시 강남구",
                    defaultPhoneNumber = "010-0000-3333",
                ),
            )
        createdEmails.add("customer1-order-test@example.com")
        customer1Id = customer1.userId
        customerToken = loginAndGetToken("customer1-order-test@example.com", "password123!")

        // CUSTOMER_USER_2 생성 및 로그인
        val customer2 =
            registerCustomerService.register(
                RegisterCustomerCommand(
                    username = "customer2",
                    email = "customer2-order-test@example.com",
                    rawPassword = "password123!",
                    defaultAddress = "서울시 서초구",
                    defaultPhoneNumber = "010-0000-4444",
                ),
            )
        createdEmails.add("customer2-order-test@example.com")
        customer2Id = customer2.userId
        customer2Token = loginAndGetToken("customer2-order-test@example.com", "password123!")

        // Redis 재고 초기화 (테스트 데이터와 동기화)
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").set(100)
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_KEYBOARD").set(50)
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_LOW_STOCK").set(2)

        // 테스트용 주문 생성 (각기 다른 상태로 설정)
        createTestOrders()
    }

    private fun loginAndGetToken(
        email: String,
        password: String,
    ): String {
        val loginRequest =
            LoginRequest(
                email = email,
                password = password,
            )

        val loginResponse =
            mockMvc
                .perform(
                    post("/api/v1/auth/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)),
                ).andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

        val loginResponseBody = objectMapper.readTree(loginResponse)
        return loginResponseBody.get("accessToken").asText()
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
            val customer1Uuid = UUID.fromString(customer1Id)
            val customer2Uuid = UUID.fromString(customer2Id)
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
                .setParameter(2, customer1Uuid)
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

            createdOrderIds.add(ORDER_STOCK_RESERVED)

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
                .setParameter(2, customer1Uuid)
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

            createdOrderIds.add(ORDER_PAYMENT_COMPLETED)

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
                .setParameter(2, customer1Uuid)
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

            createdOrderIds.add(ORDER_DELIVERED)

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
                .setParameter(2, customer1Uuid)
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

            createdOrderIds.add(ORDER_SHIPPED)

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
                .setParameter(2, customer2Uuid)
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

            createdOrderIds.add(ORDER_USER2)

            entityManager.flush()
        }
    }

    @AfterEach
    fun tearDown() {
        // Redis 키 정리
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").delete()
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_KEYBOARD").delete()
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_LOW_STOCK").delete()

        // 예약 관련 키 정리
        val expiryIndex = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
        expiryIndex.clear()

        // 동적으로 생성한 사용자 및 관련 데이터 정리
        transactionApplier.applyPrimaryTransaction {
            createdEmails.forEach { email ->
                userRepository.findByEmail(email).ifPresent { user ->
                    refreshTokenRepository.findByUserId(user.id!!).ifPresent { token ->
                        refreshTokenRepository.delete(token)
                    }
                    userRepository.delete(user)
                }
            }
        }
    }

    // ========== POST /api/v1/orders (주문 생성) ==========

    @Test
    @DisplayName("POST /api/v1/orders - 단일 상품 주문 생성 성공")
    fun createOrder_withSingleProduct_shouldSucceed() {
        // given
        val token = customerToken
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            quantity = 2,
                        ),
                    ),
                idempotencyKey = "test-key-single-${UUID.randomUUID()}",
                note = "빠른 배송 부탁드립니다",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.orderNumber").exists())
            .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
            .andExpect(jsonPath("$.totalAmount").value(100000.00)) // 50000 * 2
            .andExpect(jsonPath("$.reservationId").exists())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_MOUSE.toString()))
            .andExpect(jsonPath("$.items[0].productName").value("무선 마우스"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(50000.00))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
    }

    @Test
    @DisplayName("POST /api/v1/orders - 여러 상품 주문 생성 성공")
    fun createOrder_withMultipleProducts_shouldSucceed() {
        // given
        val token = customerToken
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            quantity = 1,
                        ),
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_KEYBOARD,
                            quantity = 1,
                        ),
                    ),
                idempotencyKey = "test-key-multi-${UUID.randomUUID()}",
                note = null,
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
            .andExpect(jsonPath("$.totalAmount").value(170000.00)) // 50000 + 120000
            .andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    @DisplayName("POST /api/v1/orders - 멱등성 키로 중복 주문 방지 확인")
    fun createOrder_withDuplicateIdempotencyKey_shouldReturnExistingOrder() {
        // given
        val token = customerToken
        val idempotencyKey = "test-key-idempotent-${UUID.randomUUID()}"
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            quantity = 1,
                        ),
                    ),
                idempotencyKey = idempotencyKey,
            )

        // when: 첫 번째 주문 생성
        val firstResponse =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andReturn()

        val firstOrderId = objectMapper.readTree(firstResponse.response.contentAsString).get("orderId").asText()

        // when & then: 같은 멱등성 키로 두 번째 주문 시도
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
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
        val token = customerToken
        val nonExistentStoreId = UUID.randomUUID()
        val request =
            CreateOrderRequest(
                storeId = nonExistentStoreId,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_MOUSE,
                            quantity = 1,
                        ),
                    ),
                idempotencyKey = "test-key-invalid-store-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("POST /api/v1/orders - 존재하지 않는 상품으로 주문 시도 시 실패")
    fun createOrder_withNonExistentProduct_shouldFail() {
        // given
        val token = customerToken
        val nonExistentProductId = UUID.randomUUID()
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = nonExistentProductId,
                            quantity = 1,
                        ),
                    ),
                idempotencyKey = "test-key-invalid-product-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("POST /api/v1/orders - 재고 부족 상품 주문 시도 시 실패")
    fun createOrder_withInsufficientStock_shouldFail() {
        // given: 재고가 2개인 상품을 3개 주문 시도
        val token = customerToken
        val request =
            CreateOrderRequest(
                storeId = STORE_1,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = PRODUCT_LOW_STOCK,
                            quantity = 3,
                        ),
                    ),
                idempotencyKey = "test-key-low-stock-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
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
        val token = customerToken
        val request = CancelOrderRequest(cancelReason = "상품이 마음에 들지 않아요")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_STOCK_RESERVED/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
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
        val token = customerToken
        val request = CancelOrderRequest(cancelReason = "단순 변심")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isConflict) // 409 Conflict - 리소스 상태 충돌
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 배송 중인 주문(SHIPPED) 취소 시도 시 실패")
    fun cancelOrder_withShippedStatus_shouldFail() {
        // given
        val token = customerToken
        val request = CancelOrderRequest(cancelReason = "취소하고 싶어요")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_SHIPPED/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isConflict) // 409 Conflict - 리소스 상태 충돌
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 존재하지 않는 주문 취소 시도 시 실패")
    fun cancelOrder_withNonExistentOrder_shouldFail() {
        // given
        val token = customerToken
        val nonExistentOrderId = UUID.randomUUID()
        val request = CancelOrderRequest(cancelReason = "취소 사유")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$nonExistentOrderId/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 다른 사용자의 주문 취소 시도 시 실패")
    fun cancelOrder_withOtherUsersOrder_shouldFail() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 취소 시도
        val token = customerToken
        val request = CancelOrderRequest(cancelReason = "취소")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_USER2/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
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
        val token = customerToken
        val request = RefundOrderRequest(refundReason = "상품이 설명과 달라요")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_DELIVERED/refund")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
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
        val token = customerToken
        val request = RefundOrderRequest(refundReason = "환불 요청")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/refund")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest) // 400 Bad Request
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 존재하지 않는 주문 환불 시도 시 실패")
    fun refundOrder_withNonExistentOrder_shouldFail() {
        // given
        val token = customerToken
        val nonExistentOrderId = UUID.randomUUID()
        val request = RefundOrderRequest(refundReason = "환불 사유")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$nonExistentOrderId/refund")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 다른 사용자의 주문 환불 시도 시 실패")
    fun refundOrder_withOtherUsersOrder_shouldFail() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문 환불 시도
        val token = customerToken
        val request = RefundOrderRequest(refundReason = "환불")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_USER2/refund")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isForbidden) // 403 Forbidden
    }
}
