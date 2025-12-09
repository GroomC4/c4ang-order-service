package com.groom.order.adapter.inbound.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.groom.order.adapter.inbound.web.dto.CancelOrderRequest
import com.groom.order.adapter.inbound.web.dto.CreateOrderRequest
import com.groom.order.adapter.inbound.web.dto.RefundOrderRequest
import com.groom.order.common.IntegrationTestBase

import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

/**
 * OrderCommandController 인가 통합 테스트
 *
 * 실제 데이터를 사용하여 주문 명령(생성, 취소, 환불) API의 인증/인가를 검증합니다.
 * - Istio 헤더를 사용한 인증 플로우 테스트
 * - CUSTOMER 역할: 모든 주문 명령 API 접근 가능
 * - SELLER 역할: 본인 주문만 접근 가능 (다른 사용자 주문 시 403 Forbidden)
 * - 인증 없음: 접근 불가 (400 Bad Request - @RequestHeader required 헤더 누락)
 *
 * Note: 이 테스트는 인증/인가만 검증합니다. 비즈니스 로직(주문 생성, 이벤트 발행 등)은
 *       별도의 비즈니스 로직 통합 테스트에서 검증합니다.
 */
@SqlGroup(
    Sql(
        scripts = ["/sql/cleanup-order-command-controller.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/init-order-command-controller.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    ),
    Sql(
        scripts = ["/sql/cleanup-order-command-controller.sql"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
    ),
)
@DisplayName("주문 명령(Command) 컨트롤러 인가 통합 테스트")
@AutoConfigureMockMvc
class OrderCommandControllerAuthorizationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        // Istio Headers
        private const val ISTIO_USER_ID_HEADER = "X-User-Id"
        private const val ISTIO_USER_ROLE_HEADER = "X-User-Role"

        // Test Users
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val SELLER_USER_1 = UUID.fromString("11111111-1111-1111-1111-111111111111")

        // Test Store and Products
        private val TEST_STORE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val TEST_PRODUCT_1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        private val TEST_PRODUCT_2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")

        // Test Orders
        private val ORDER_PAYMENT_COMPLETED = UUID.fromString("11111111-1111-1111-1111-000000000002")
        private val ORDER_DELIVERED = UUID.fromString("11111111-1111-1111-1111-000000000003")
    }

    // ========== POST /api/v1/orders (주문 생성) ==========

    @Test
    @DisplayName("POST /api/v1/orders - CUSTOMER 역할로 주문 생성 시 인증 성공")
    fun createOrder_withCustomerRole_shouldSucceed() {
        // given
        val userId = CUSTOMER_USER_1
        val request =
            CreateOrderRequest(
                storeId = TEST_STORE_ID,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = TEST_PRODUCT_1,
                            productName = "Gaming Mouse",
                            quantity = 2,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-${UUID.randomUUID()}",
                note = "배송 시 문 앞에 놔주세요",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(ISTIO_USER_ID_HEADER, userId.toString())
                    .header(ISTIO_USER_ROLE_HEADER, if (userId == SELLER_USER_1) "SELLER" else "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            // 인증은 통과하므로 401/403이 아닌 다른 상태 코드 확인
            .andExpect(
                status().`is`(not(401)),
            ).andExpect(status().`is`(not(403)))
    }

    @Test
    @DisplayName("POST /api/v1/orders - Istio 헤더 없이 주문 생성 시 400 Bad Request (@RequestHeader required)")
    fun createOrder_withoutIstioHeaders_shouldReturn400() {
        // given
        val request =
            CreateOrderRequest(
                storeId = TEST_STORE_ID,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = TEST_PRODUCT_1,
                            productName = "Gaming Mouse",
                            quantity = 2,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("POST /api/v1/orders - SELLER 역할로 주문 생성 시 비즈니스 로직 검증 (Istio가 인가 처리)")
    fun createOrder_withSellerRole_shouldPassAuthentication() {
        // given
        val userId = SELLER_USER_1
        val request =
            CreateOrderRequest(
                storeId = TEST_STORE_ID,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = TEST_PRODUCT_1,
                            productName = "Gaming Mouse",
                            quantity = 2,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                idempotencyKey = "test-key-${UUID.randomUUID()}",
            )

        // when & then
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(ISTIO_USER_ID_HEADER, userId.toString())
                    .header(ISTIO_USER_ROLE_HEADER, if (userId == SELLER_USER_1) "SELLER" else "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().`is`(not(401)))
            .andExpect(status().`is`(not(403)))
    }

    // ========== PATCH /api/v1/orders/{orderId}/cancel (주문 취소) ==========

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - CUSTOMER 역할로 주문 취소 시 인증 성공")
    fun cancelOrder_withCustomerRole_shouldSucceed() {
        // given
        val userId = CUSTOMER_USER_1
        val request = CancelOrderRequest(cancelReason = "단순 변심")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/cancel")
                    .header(ISTIO_USER_ID_HEADER, userId.toString())
                    .header(ISTIO_USER_ROLE_HEADER, if (userId == SELLER_USER_1) "SELLER" else "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            // 인증은 통과하므로 401/403이 아닌 다른 상태 코드 확인
            .andExpect(
                status().`is`(not(401)),
            ).andExpect(status().`is`(not(403)))
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - Istio 헤더 없이 주문 취소 시 400 Bad Request (@RequestHeader required)")
    fun cancelOrder_withoutIstioHeaders_shouldReturn400() {
        // given
        val request = CancelOrderRequest(cancelReason = "단순 변심")

        // when & then
        // @RequestHeader(required=true) 누락 시 Spring이 자동으로 400 Bad Request 반환
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 다른 사용자의 주문 취소 시도 시 403 Forbidden")
    fun cancelOrder_withOtherUsersOrder_shouldReturn403() {
        // given: SELLER_USER_1이 CUSTOMER_USER_1 소유의 주문을 취소 시도
        val userId = SELLER_USER_1
        val request = CancelOrderRequest(cancelReason = "단순 변심")

        // when & then
        // 본인의 주문이 아니므로 OrderAccessDenied 예외 발생 → 403 Forbidden
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/cancel")
                    .header(ISTIO_USER_ID_HEADER, userId.toString())
                    .header(ISTIO_USER_ROLE_HEADER, "SELLER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isForbidden)
    }

    // ========== PATCH /api/v1/orders/{orderId}/refund (주문 환불) ==========

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - CUSTOMER 역할로 주문 환불 시 인증 성공")
    fun refundOrder_withCustomerRole_shouldSucceed() {
        // given
        val userId = CUSTOMER_USER_1
        val request = RefundOrderRequest(refundReason = "상품 불량")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_DELIVERED/refund")
                    .header(ISTIO_USER_ID_HEADER, userId.toString())
                    .header(ISTIO_USER_ROLE_HEADER, if (userId == SELLER_USER_1) "SELLER" else "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            // 인증은 통과하므로 401/403이 아닌 다른 상태 코드 확인
            .andExpect(
                status().`is`(not(401)),
            ).andExpect(status().`is`(not(403)))
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - Istio 헤더 없이 주문 환불 시 400 Bad Request (@RequestHeader required)")
    fun refundOrder_withoutIstioHeaders_shouldReturn400() {
        // given
        val request = RefundOrderRequest(refundReason = "상품 불량")

        // when & then
        // @RequestHeader(required=true) 누락 시 Spring이 자동으로 400 Bad Request 반환
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_DELIVERED/refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 다른 사용자의 주문 환불 시도 시 403 Forbidden")
    fun refundOrder_withOtherUsersOrder_shouldReturn403() {
        // given: SELLER_USER_1이 CUSTOMER_USER_1 소유의 주문을 환불 시도
        val userId = SELLER_USER_1
        val request = RefundOrderRequest(refundReason = "상품 불량")

        // when & then
        // 본인의 주문이 아니므로 OrderAccessDenied 예외 발생 → 403 Forbidden
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_DELIVERED/refund")
                    .header(ISTIO_USER_ID_HEADER, userId.toString())
                    .header(ISTIO_USER_ROLE_HEADER, "SELLER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isForbidden)
    }
}
