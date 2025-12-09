package com.groom.order.adapter.inbound.web

import com.groom.order.adapter.inbound.web.dto.GetOrderDetailResponse
import com.groom.order.adapter.inbound.web.dto.ListOrdersResponse
import com.groom.order.application.dto.GetOrderDetailQuery
import com.groom.order.application.dto.ListOrdersQuery
import com.groom.order.application.service.GetOrderDetailService
import com.groom.order.application.service.ListOrdersService
import com.groom.order.domain.model.OrderStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 주문 조회(Query) 컨트롤러
 *
 * 주문 목록 조회, 주문 상세 조회 등 읽기 전용 API를 제공합니다.
 * 모든 API는 CUSTOMER 역할을 가진 사용자만 호출할 수 있습니다.
 */
@Tag(name = "Order Query", description = "주문 조회 API")
@RestController
@RequestMapping("/api/v1/orders")
class OrderQueryController(
    private val getOrderDetailService: GetOrderDetailService,
    private val listOrdersService: ListOrdersService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 목록 조회
     *
     * GET /api/v1/orders?status={status}
     *
     * 현재 사용자의 주문 목록을 조회합니다.
     * - status 파라미터로 특정 상태의 주문만 필터링 가능
     * - 최신 주문이 먼저 표시되도록 정렬
     *
     * @param status 주문 상태 필터 (선택 사항)
     * @return 주문 목록 (주문번호, 상태, 총액, 상품 개수, 생성일시 포함)
     */
    // Istio Gateway handles authorization
    @Operation(summary = "주문 목록 조회", description = "현재 사용자의 주문 목록을 조회합니다. 상태별 필터링이 가능합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "주문 목록 조회 성공"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(responseCode = "403", description = "권한 없음"),
        ],
    )
    @GetMapping
    fun listOrders(
        @RequestHeader("X-User-Id") userId: UUID,
        @RequestParam(required = false) status: OrderStatus?,
    ): ListOrdersResponse {
        logger.info { "Listing orders for user: $userId, status filter: $status" }

        val query =
            ListOrdersQuery(
                requestUserId = userId,
                status = status,
            )

        val result = listOrdersService.listOrders(query)

        logger.info { "Found ${result.orders.size} orders for user: $userId" }

        return ListOrdersResponse.from(result)
    }

    /**
     * 주문 상세 조회
     *
     * GET /api/v1/orders/{orderId}
     *
     * 주문 상세 정보를 조회합니다.
     * - 접근 권한 검증 (본인 주문만 조회 가능)
     * - 주문 항목(OrderItem) 정보 포함
     * - 재고 예약, 결제, 환불 정보 포함
     *
     * @param orderId 주문 ID
     * @return 주문 상세 정보 (주문 항목, 재고 예약 ID, 결제 ID, 환불 ID, 만료 시각 등 포함)
     */
    // Istio Gateway handles authorization
    @Operation(summary = "주문 상세 조회", description = "주문의 상세 정보를 조회합니다. 주문 항목, 결제, 환불 정보가 포함됩니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "주문 상세 조회 성공"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(responseCode = "403", description = "권한 없음"),
            ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
        ],
    )
    @GetMapping("/{orderId}")
    fun getOrderDetail(
        @RequestHeader("X-User-Id") userId: UUID,
        @PathVariable orderId: UUID,
    ): GetOrderDetailResponse {
        logger.info { "Retrieving order detail: orderId=$orderId, user=$userId" }

        val query =
            GetOrderDetailQuery(
                orderId = orderId,
                requestUserId = userId,
            )

        val result = getOrderDetailService.getOrderDetail(query)

        logger.info { "Order detail retrieved: ${result.orderNumber}" }

        return GetOrderDetailResponse.from(result)
    }
}
