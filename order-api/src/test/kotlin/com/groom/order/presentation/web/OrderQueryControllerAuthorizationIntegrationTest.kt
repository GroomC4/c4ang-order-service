package com.groom.order.presentation.web

import com.groom.ecommerce.common.annotation.IntegrationTest
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * OrderQueryController 통합 테스트
 *
 * 주문 조회(목록, 상세) API의 인증/인가를 검증합니다.
 * - CUSTOMER 역할: 모든 주문 조회 API 접근 가능
 * - SELLER 역할: 접근 불가 (403 Forbidden)
 * - 인증 없음: 접근 불가 (401 Unauthorized)
 */
@DisplayName("주문 조회(Query) 컨트롤러 통합 테스트 - 인증/인가")
@IntegrationTest
@SpringBootTest
@AutoConfigureMockMvc
class OrderQueryControllerAuthorizationIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    companion object {
        private val CUSTOMER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val SELLER_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        private val TEST_ORDER_ID = UUID.randomUUID()
    }

    // ========== GET /api/v1/orders (주문 목록 조회) ==========

    @Test
    @DisplayName("GET /api/v1/orders - CUSTOMER 역할로 주문 목록 조회 시 인증 성공")
    fun listOrders_withCustomerRole_shouldSucceed() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .with(SecurityMockMvcRequestPostProcessors.user(CUSTOMER_USER_ID.toString()).roles("CUSTOMER")),
            ).andDo(print())
            // 인증은 통과하므로 401/403이 아닌 다른 상태 코드 확인
            .andExpect(
                status().`is`(not(401)),
            ).andExpect(status().`is`(not(403)))
    }

    @Test
    @DisplayName("GET /api/v1/orders - 인증 없이 주문 목록 조회 시 401 Unauthorized")
    fun listOrders_withoutAuthentication_shouldReturn401() {
        // when & then
        mockMvc
            .perform(get("/api/v1/orders"))
            .andDo(print())
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("GET /api/v1/orders - SELLER 역할로 주문 목록 조회 시 403 Forbidden")
    fun listOrders_withSellerRole_shouldReturn403() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .with(SecurityMockMvcRequestPostProcessors.user(SELLER_USER_ID.toString()).roles("SELLER")),
            ).andDo(print())
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("GET /api/v1/orders?status=PENDING - CUSTOMER 역할로 상태 필터링 조회 시 인증 성공")
    fun listOrders_withStatusFilter_withCustomerRole_shouldSucceed() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("status", "PENDING")
                    .with(SecurityMockMvcRequestPostProcessors.user(CUSTOMER_USER_ID.toString()).roles("CUSTOMER")),
            ).andDo(print())
            // 인증은 통과하므로 401/403이 아닌 다른 상태 코드 확인
            .andExpect(
                status().`is`(not(401)),
            ).andExpect(status().`is`(not(403)))
    }

    // ========== GET /api/v1/orders/{orderId} (주문 상세 조회) ==========

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - CUSTOMER 역할로 주문 상세 조회 시 인증 성공")
    fun getOrderDetail_withCustomerRole_shouldSucceed() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$TEST_ORDER_ID")
                    .with(SecurityMockMvcRequestPostProcessors.user(CUSTOMER_USER_ID.toString()).roles("CUSTOMER")),
            ).andDo(print())
            // 실제 주문이 없으므로 404 또는 다른 비즈니스 예외가 발생할 수 있음
            // 인증은 통과하므로 401/403이 아닌 다른 에러 코드 확인
            .andExpect(
                status().`is`(not(401)),
            ).andExpect(status().`is`(not(403)))
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 인증 없이 주문 상세 조회 시 401 Unauthorized")
    fun getOrderDetail_withoutAuthentication_shouldReturn401() {
        // when & then
        mockMvc
            .perform(get("/api/v1/orders/$TEST_ORDER_ID"))
            .andDo(print())
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - SELLER 역할로 주문 상세 조회 시 403 Forbidden")
    fun getOrderDetail_withSellerRole_shouldReturn403() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$TEST_ORDER_ID")
                    .with(SecurityMockMvcRequestPostProcessors.user(SELLER_USER_ID.toString()).roles("SELLER")),
            ).andDo(print())
            .andExpect(status().isForbidden)
    }
}
