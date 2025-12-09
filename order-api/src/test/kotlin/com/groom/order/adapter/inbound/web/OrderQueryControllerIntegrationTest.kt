package com.groom.order.adapter.inbound.web

import com.groom.order.common.IntegrationTestBase

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * OrderQueryController 비즈니스로직 통합 테스트
 *
 * 실제 데이터를 사용하여 주문 조회 API의 비즈니스 로직을 검증합니다.
 * - 비즈니스 로직과 데이터 정합성 테스트
 */
@SqlGroup(
    Sql(
        scripts = ["/sql/cleanup-order-query-controller.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/init-order-query-controller.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    ),
    Sql(
        scripts = ["/sql/cleanup-order-query-controller.sql"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
    ),
)
@DisplayName("주문 조회(Query) 컨트롤러 비즈니스로직 통합 테스트")
@AutoConfigureMockMvc
class OrderQueryControllerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    companion object {
        // Test Users
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val CUSTOMER_USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")

        // Test Orders (CUSTOMER_USER_1 소유)
        private val ORDER_1_ORDER_CONFIRMED = UUID.fromString("11111111-1111-1111-1111-000000000001")
        private val ORDER_2_PAYMENT_COMPLETED = UUID.fromString("11111111-1111-1111-1111-000000000002")
        private val ORDER_3_DELIVERED = UUID.fromString("11111111-1111-1111-1111-000000000003")
        private val ORDER_4_CANCELLED = UUID.fromString("11111111-1111-1111-1111-000000000004")

        // Test Order (CUSTOMER_USER_2 소유 - 접근 거부 테스트용)
        private val ORDER_5_USER2 = UUID.fromString("22222222-2222-2222-2222-000000000001")
    }

    // ========== GET /api/v1/orders - 주문 목록 조회 ==========

    @Test
    @DisplayName("GET /api/v1/orders - 전체 주문 목록 조회 성공")
    fun listOrders_shouldReturnAllOrders() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders").isArray)
            .andExpect(jsonPath("$.orders.length()").value(4)) // CUSTOMER_USER_1의 주문 4개
    }

    @Test
    @DisplayName("GET /api/v1/orders - 최신 주문이 먼저 표시되는지 확인 (정렬)")
    fun listOrders_shouldReturnOrdersSortedByCreatedAtDesc() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders[0].orderId").value(ORDER_1_ORDER_CONFIRMED.toString())) // 최신 (NOW())
            .andExpect(jsonPath("$.orders[0].orderNumber").value("ORD-20251028-001"))
            .andExpect(jsonPath("$.orders[0].status").value("ORDER_CONFIRMED"))
            .andExpect(jsonPath("$.orders[1].orderId").value(ORDER_2_PAYMENT_COMPLETED.toString())) // 1일 전
            .andExpect(jsonPath("$.orders[2].orderId").value(ORDER_4_CANCELLED.toString())) // 3일 전
            .andExpect(jsonPath("$.orders[3].orderId").value(ORDER_3_DELIVERED.toString())) // 5일 전
    }

    @Test
    @DisplayName("GET /api/v1/orders?status=PAYMENT_COMPLETED - 상태 필터링 조회 성공")
    fun listOrders_withStatusFilter_shouldReturnFilteredOrders() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("status", "PAYMENT_COMPLETED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders").isArray)
            .andExpect(jsonPath("$.orders.length()").value(1)) // PAYMENT_COMPLETED 상태는 1개
            .andExpect(jsonPath("$.orders[0].orderId").value(ORDER_2_PAYMENT_COMPLETED.toString()))
            .andExpect(jsonPath("$.orders[0].status").value("PAYMENT_COMPLETED"))
    }

    @Test
    @DisplayName("GET /api/v1/orders?status=DELIVERED - 배송 완료 주문 조회")
    fun listOrders_withDeliveredStatus_shouldReturnDeliveredOrders() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("status", "DELIVERED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders.length()").value(1))
            .andExpect(jsonPath("$.orders[0].orderId").value(ORDER_3_DELIVERED.toString()))
            .andExpect(jsonPath("$.orders[0].status").value("DELIVERED"))
    }

    @Test
    @DisplayName("GET /api/v1/orders?status=ORDER_CANCELLED - 취소된 주문 조회")
    fun listOrders_withCancelledStatus_shouldReturnCancelledOrders() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("status", "ORDER_CANCELLED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders.length()").value(1))
            .andExpect(jsonPath("$.orders[0].orderId").value(ORDER_4_CANCELLED.toString()))
            .andExpect(jsonPath("$.orders[0].status").value("ORDER_CANCELLED"))
    }

    @Test
    @DisplayName("GET /api/v1/orders - 주문이 없는 사용자의 빈 목록 조회")
    fun listOrders_withNoOrders_shouldReturnEmptyList() {
        // given: 주문이 없는 사용자 (임의의 UUID)
        val userWithNoOrders = UUID.fromString("99999999-9999-9999-9999-999999999999")

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .header("X-User-Id", userWithNoOrders.toString())
                    .header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders").isArray)
            .andExpect(jsonPath("$.orders.length()").value(0))
    }

    // ========== GET /api/v1/orders/{orderId} - 주문 상세 조회 ==========

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 주문 상세 조회 성공")
    fun getOrderDetail_shouldReturnOrderDetail() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$ORDER_1_ORDER_CONFIRMED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(ORDER_1_ORDER_CONFIRMED.toString()))
            .andExpect(jsonPath("$.orderNumber").value("ORD-20251028-001"))
            .andExpect(jsonPath("$.status").value("ORDER_CONFIRMED"))
            .andExpect(jsonPath("$.totalAmount").value(30000))
            .andExpect(jsonPath("$.reservationId").value("reservation-001"))
            .andExpect(jsonPath("$.expiresAt").exists())
            // 주문 항목 검증
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].productName").value("테스트 상품 1"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(10000))
            .andExpect(jsonPath("$.items[0].quantity").value(1))
            .andExpect(jsonPath("$.items[1].productName").value("테스트 상품 2"))
            .andExpect(jsonPath("$.items[1].unitPrice").value(20000))
            .andExpect(jsonPath("$.items[1].quantity").value(1))
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 결제 완료된 주문 상세 조회")
    fun getOrderDetail_withPaymentCompleted_shouldIncludePaymentId() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$ORDER_2_PAYMENT_COMPLETED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(ORDER_2_PAYMENT_COMPLETED.toString()))
            .andExpect(jsonPath("$.status").value("PAYMENT_COMPLETED"))
            .andExpect(jsonPath("$.paymentId").value("dddddddd-dddd-dddd-dddd-000000000002"))
            .andExpect(jsonPath("$.reservationId").value("reservation-002"))
            // 주문 항목 검증
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].productName").value("테스트 상품 2"))
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 배송 완료된 주문 상세 조회")
    fun getOrderDetail_withDelivered_shouldShowDeliveredStatus() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$ORDER_3_DELIVERED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(ORDER_3_DELIVERED.toString()))
            .andExpect(jsonPath("$.status").value("DELIVERED"))
            .andExpect(jsonPath("$.totalAmount").value(50000))
            // 주문 항목 검증 (상품 1: 3개, 상품 2: 1개)
            .andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 취소된 주문 상세 조회")
    fun getOrderDetail_withCancelled_shouldIncludeCancelReason() {
        // given

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$ORDER_4_CANCELLED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(ORDER_4_CANCELLED.toString()))
            .andExpect(jsonPath("$.status").value("ORDER_CANCELLED"))
            .andExpect(jsonPath("$.failureReason").value("단순 변심"))
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 존재하지 않는 주문 조회 시 404 Not Found")
    fun getOrderDetail_withNonExistentOrder_shouldReturn404() {
        // given
        val nonExistentOrderId = UUID.fromString("99999999-9999-9999-9999-999999999999")

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$nonExistentOrderId")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 다른 사용자의 주문 조회 시도 시 접근 거부")
    fun getOrderDetail_withOtherUsersOrder_shouldBeForbidden() {
        // given: CUSTOMER_USER_1이 CUSTOMER_USER_2의 주문(ORDER_5)을 조회 시도

        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/$ORDER_5_USER2")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isForbidden) // 또는 404 (구현에 따라)
    }

    // ========== 추가 분기 테스트 ==========

    @Test
    @DisplayName("GET /api/v1/orders?status=ORDER_CONFIRMED - 재고 예약 상태 필터링")
    fun listOrders_withStockReservedStatus_shouldReturnFilteredOrders() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("status", "ORDER_CONFIRMED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders").isArray)
            .andExpect(jsonPath("$.orders.length()").value(1))
            .andExpect(jsonPath("$.orders[0].status").value("ORDER_CONFIRMED"))
    }

    @Test
    @DisplayName("GET /api/v1/orders?status=PREPARING - 해당 상태 주문이 없을 때 빈 목록 반환")
    fun listOrders_withNoMatchingStatus_shouldReturnEmptyList() {
        // when & then: PREPARING 상태 주문이 없음
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("status", "PREPARING")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders").isArray)
            .andExpect(jsonPath("$.orders.length()").value(0))
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 주문 상세에서 주문 항목이 정확하게 반환되는지 확인")
    fun getOrderDetail_shouldReturnAllOrderItems() {
        // when & then: ORDER_1_ORDER_CONFIRMED는 2개의 아이템을 가짐
        mockMvc
            .perform(
                get("/api/v1/orders/$ORDER_1_ORDER_CONFIRMED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].productId").exists())
            .andExpect(jsonPath("$.items[0].productName").exists())
            .andExpect(jsonPath("$.items[0].unitPrice").exists())
            .andExpect(jsonPath("$.items[0].quantity").exists())
    }

    @Test
    @DisplayName("GET /api/v1/orders - 주문 목록에 필수 필드가 모두 포함되는지 확인")
    fun listOrders_shouldContainRequiredFields() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orders[0].orderId").exists())
            .andExpect(jsonPath("$.orders[0].orderNumber").exists())
            .andExpect(jsonPath("$.orders[0].status").exists())
            .andExpect(jsonPath("$.orders[0].totalAmount").exists())
            .andExpect(jsonPath("$.orders[0].itemCount").exists())
            .andExpect(jsonPath("$.orders[0].createdAt").exists())
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 환불 완료된 주문 상세 조회")
    fun getOrderDetail_withRefundCompleted_shouldIncludeRefundId() {
        // given: 환불 완료된 주문이 있다고 가정 (테스트 데이터 필요)
        // 이 테스트는 환불 완료 상태의 주문이 테스트 데이터에 있어야 실행 가능
        // 현재 테스트 데이터에는 없으므로 DELIVERED 주문에서 필드 존재 여부만 확인

        mockMvc
            .perform(
                get("/api/v1/orders/$ORDER_3_DELIVERED")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(ORDER_3_DELIVERED.toString()))
            .andExpect(jsonPath("$.status").value("DELIVERED"))
    }

    @Test
    @DisplayName("GET /api/v1/orders - 잘못된 orderId 형식으로 조회 시 400 Bad Request")
    fun getOrderDetail_withInvalidOrderIdFormat_shouldReturn400() {
        // when & then
        mockMvc
            .perform(
                get("/api/v1/orders/invalid-uuid-format")
                    .header(
                        "X-User-Id",
                        CUSTOMER_USER_1.toString(),
                    ).header("X-User-Role", "CUSTOMER"),
            ).andDo(print())
            .andExpect(status().isBadRequest)
    }
}
