package com.groom.order.adapter.inbound.web.internal

import com.groom.order.adapter.inbound.web.internal.dto.GetOrderResponse
import com.groom.order.adapter.inbound.web.internal.dto.HasPaymentResponse
import com.groom.order.adapter.inbound.web.internal.dto.MarkPaymentPendingRequest
import com.groom.order.adapter.inbound.web.internal.dto.MarkPaymentPendingResponse
import com.groom.order.application.service.OrderInternalService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Internal API 컨트롤러
 *
 * 다른 마이크로서비스(Payment Service 등)에서 호출하는 내부 API입니다.
 * 외부에서는 접근할 수 없으며, Service Mesh(Istio) 레벨에서 인증/인가가 처리됩니다.
 *
 * 주의: 이 API는 Ingress/Gateway에서 외부로 노출되지 않아야 합니다.
 */
@Tag(name = "Order Internal", description = "내부 서비스용 주문 API")
@RestController
@RequestMapping("/internal/v1/orders")
class OrderInternalController(
    private val orderInternalService: OrderInternalService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 조회
     *
     * GET /internal/v1/orders/{orderId}
     *
     * Payment Service에서 결제 요청 시 주문 정보를 검증하기 위해 사용합니다.
     * - 주문 금액 검증
     * - 사용자 확인
     * - 주문 상태 확인
     *
     * @param orderId 주문 ID
     * @return 주문 정보
     */
    @Operation(summary = "주문 조회", description = "결제 요청 시 주문 정보 조회 (금액 검증, 사용자 확인)")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "주문 조회 성공"),
            ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
        ],
    )
    @GetMapping("/{orderId}")
    fun getOrder(
        @PathVariable orderId: UUID,
    ): GetOrderResponse {
        logger.info { "Internal API: Get order request for orderId=$orderId" }

        val result = orderInternalService.getOrder(orderId)

        return GetOrderResponse.from(result)
    }

    /**
     * 결제 대기 상태 변경
     *
     * POST /internal/v1/orders/{orderId}/payment-pending
     *
     * Payment 생성 시점에 Order와 Payment를 연결합니다.
     * ORDER_CONFIRMED → PAYMENT_PENDING 상태 전이가 발생합니다.
     *
     * 전제 조건:
     * - 주문 상태가 ORDER_CONFIRMED여야 함
     * - 이미 결제가 연결되어 있지 않아야 함
     *
     * @param orderId 주문 ID
     * @param request 결제 ID를 포함한 요청
     * @return 업데이트된 주문 정보
     */
    @Operation(summary = "결제 대기 상태 변경", description = "Payment 생성 시점에 Order와 Payment를 연결")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
            ApiResponse(responseCode = "409", description = "주문 상태 불일치 또는 이미 결제 존재"),
        ],
    )
    @PostMapping("/{orderId}/payment-pending")
    fun markPaymentPending(
        @PathVariable orderId: UUID,
        @RequestBody request: MarkPaymentPendingRequest,
    ): MarkPaymentPendingResponse {
        logger.info { "Internal API: Mark payment pending request for orderId=$orderId, paymentId=${request.paymentId}" }

        val result = orderInternalService.markPaymentPending(orderId, request.paymentId)

        return MarkPaymentPendingResponse.from(result)
    }

    /**
     * 결제 존재 여부 확인
     *
     * GET /internal/v1/orders/{orderId}/has-payment
     *
     * 주문당 결제 1개 제한 비즈니스 규칙을 검증하기 위해 사용합니다.
     * 중복 결제 방지에 활용됩니다.
     *
     * @param orderId 주문 ID
     * @return 결제 존재 여부
     */
    @Operation(summary = "결제 존재 여부 확인", description = "주문당 결제 1개 제한 검증 (중복 결제 방지)")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
        ],
    )
    @GetMapping("/{orderId}/has-payment")
    fun hasPayment(
        @PathVariable orderId: UUID,
    ): HasPaymentResponse {
        logger.debug { "Internal API: Check has payment request for orderId=$orderId" }

        val result = orderInternalService.checkHasPayment(orderId)

        return HasPaymentResponse.from(result)
    }
}
