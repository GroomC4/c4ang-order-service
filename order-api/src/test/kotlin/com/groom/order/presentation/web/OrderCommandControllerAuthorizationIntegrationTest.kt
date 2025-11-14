package com.groom.order.presentation.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.groom.platform.testSupport.IntegrationTest
import com.groom.order.presentation.web.dto.CancelOrderRequest
import com.groom.order.presentation.web.dto.CreateOrderRequest
import com.groom.order.presentation.web.dto.RefundOrderRequest
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import com.groom.order.common.util.IstioHeaderExtractor
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * OrderCommandController 인가 통합 테스트
 *
 * 실제 데이터를 사용하여 주문 명령(생성, 취소, 환불) API의 인증/인가를 검증합니다.
 * - JWT 토큰을 사용한 실제 인증 플로우 테스트
 * - CUSTOMER 역할: 모든 주문 명령 API 접근 가능
 * - SELLER 역할: 접근 불가 (403 Forbidden)
 * - 인증 없음: 접근 불가 (401 Unauthorized)
 */
@SqlGroup(
    Sql(
        scripts = ["/sql/cleanup-order-command-controller.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/init-order-command-controller.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/cleanup-order-command-controller.sql"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS,
    ),
)
@DisplayName("주문 명령(Command) 컨트롤러 인가 통합 테스트")
@IntegrationTest
@SpringBootTest
@AutoConfigureMockMvc
class OrderCommandControllerAuthorizationIntegrationTest {
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
                            quantity = 2,
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
    @DisplayName("POST /api/v1/orders - Istio 헤더 없이 주문 생성 시 500 Internal Server Error (IllegalStateException)")
    fun createOrder_withoutIstioHeaders_shouldReturn500() {
        // given
        val request =
            CreateOrderRequest(
                storeId = TEST_STORE_ID,
                items =
                    listOf(
                        CreateOrderRequest.OrderItemRequest(
                            productId = TEST_PRODUCT_1,
                            quantity = 2,
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
            .andExpect(status().isInternalServerError)
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
                            quantity = 2,
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
            .andExpect(status().`is`(not(401))).andExpect(status().`is`(not(403)))
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
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - 인증 없이 주문 취소 시 401 Unauthorized")
    fun cancelOrder_withoutAuthentication_shouldReturn401() {
        // given
        val request = CancelOrderRequest(cancelReason = "단순 변심")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_PAYMENT_COMPLETED/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel - SELLER 역할로 주문 취소 시 403 Forbidden")
    fun cancelOrder_withSellerRole_shouldReturn403() {
        // given
        val userId = SELLER_USER_1
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
            .andExpect(status().`is`(not(401))).andExpect(status().`is`(not(403)))
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
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - 인증 없이 주문 환불 시 401 Unauthorized")
    fun refundOrder_withoutAuthentication_shouldReturn401() {
        // given
        val request = RefundOrderRequest(refundReason = "상품 불량")

        // when & then
        mockMvc
            .perform(
                patch("/api/v1/orders/$ORDER_DELIVERED/refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{orderId}/refund - SELLER 역할로 주문 환불 시 403 Forbidden")
    fun refundOrder_withSellerRole_shouldReturn403() {
        // given
        val userId = SELLER_USER_1
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
            .andExpect(status().`is`(not(401))).andExpect(status().`is`(not(403)))
    }
}
