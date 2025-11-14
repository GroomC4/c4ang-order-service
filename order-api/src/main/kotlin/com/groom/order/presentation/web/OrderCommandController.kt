package com.groom.order.presentation.web

import com.groom.order.common.util.IstioHeaderExtractor
import com.groom.order.application.dto.CancelOrderCommand
import jakarta.servlet.http.HttpServletRequest
import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.application.dto.RefundOrderCommand
import com.groom.order.application.service.CancelOrderService
import com.groom.order.application.service.CreateOrderService
import com.groom.order.application.service.RefundOrderService
import com.groom.order.presentation.web.dto.CancelOrderRequest
import com.groom.order.presentation.web.dto.CancelOrderResponse
import com.groom.order.presentation.web.dto.CreateOrderRequest
import com.groom.order.presentation.web.dto.CreateOrderResponse
import com.groom.order.presentation.web.dto.RefundOrderRequest
import com.groom.order.presentation.web.dto.RefundOrderResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 주문 명령(Command) 컨트롤러
 *
 * 주문의 생성, 취소, 환불 등 상태를 변경하는 API를 제공합니다.
 * 모든 API는 CUSTOMER 역할을 가진 사용자만 호출할 수 있습니다.
 */
@Tag(name = "Order Command", description = "주문 명령 API")
@RestController
@RequestMapping("/api/v1/orders")
class OrderCommandController(
    private val createOrderService: CreateOrderService,
    private val cancelOrderService: CancelOrderService,
    private val refundOrderService: RefundOrderService,
    private val istioHeaderExtractor: IstioHeaderExtractor,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 생성
     *
     * POST /api/v1/orders
     *
     * 비동기 주문-결제 플로우:
     * 1. 멱등성 체크 (idempotencyKey)
     * 2. 상점 및 상품 검증
     * 3. Redis 재고 예약 (10분 TTL)
     * 4. 주문 생성 (STOCK_RESERVED 상태)
     * 5. 도메인 이벤트 발행
     *
     * @param request 주문 생성 요청
     * @return 생성된 주문 정보 (orderId, reservationId, expiresAt 포함)
     */
    @Operation(summary = "주문 생성", description = "새로운 주문을 생성하고 재고를 예약합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(responseCode = "403", description = "권한 없음"),
            ApiResponse(responseCode = "404", description = "상점 또는 상품을 찾을 수 없음"),
            ApiResponse(responseCode = "409", description = "재고 부족 또는 중복 주문"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // Istio Gateway handles authorization
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest,
        httpRequest: HttpServletRequest,
    ): CreateOrderResponse {
        val userId = istioHeaderExtractor.extractUserId(httpRequest)
        logger.info { "Creating order for user: $userId, idempotencyKey: ${request.idempotencyKey}" }

        val command =
            CreateOrderCommand(
                userExternalId = userId,
                storeId = request.storeId,
                idempotencyKey = request.idempotencyKey,
                items =
                    request.items.map { item ->
                        CreateOrderCommand.OrderItemDto(
                            productId = item.productId,
                            quantity = item.quantity,
                        )
                    },
                note = request.note,
            )

        val result = createOrderService.createOrder(command)

        logger.info { "Order created successfully: ${result.orderNumber}" }

        return CreateOrderResponse.from(result)
    }

    /**
     * 주문 취소
     *
     * PATCH /api/v1/orders/{orderId}/cancel
     *
     * 취소 가능한 상태:
     * - PENDING, STOCK_RESERVED, PAYMENT_PENDING, PAYMENT_PROCESSING, PREPARING
     *
     * 처리 프로세스:
     * 1. 주문 조회 및 접근 권한 검증
     * 2. 취소 가능 상태 확인
     * 3. Redis 재고 예약 복구
     * 4. 주문 상태 변경 (ORDER_CANCELLED)
     * 5. 도메인 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param request 취소 사유
     * @return 취소된 주문 정보
     */
    @Operation(summary = "주문 취소", description = "주문을 취소하고 재고 예약을 복구합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "주문 취소 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청 또는 취소 불가능한 상태"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(responseCode = "403", description = "권한 없음"),
            ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
        ],
    )
    @PatchMapping("/{orderId}/cancel")
    // Istio Gateway handles authorization
    fun cancelOrder(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: CancelOrderRequest,
        httpRequest: HttpServletRequest,
    ): CancelOrderResponse {
        val userId = istioHeaderExtractor.extractUserId(httpRequest)
        logger.info { "Cancelling order: $orderId, user: $userId" }

        val command =
            CancelOrderCommand(
                orderId = orderId,
                requestUserId = userId,
                cancelReason = request.cancelReason,
            )

        val result = cancelOrderService.cancelOrder(command)

        logger.info { "Order cancelled successfully: ${result.orderNumber}" }

        return CancelOrderResponse.from(result)
    }

    /**
     * 주문 환불
     *
     * PATCH /api/v1/orders/{orderId}/refund
     *
     * 환불 가능한 상태:
     * - DELIVERED (배송 완료된 주문만 환불 가능)
     *
     * 처리 프로세스:
     * 1. 주문 조회 및 접근 권한 검증
     * 2. 환불 가능 상태 확인 (DELIVERED)
     * 3. 환불 금액 계산
     * 4. PG사 환불 요청 (현재는 mock)
     * 5. 주문 상태 변경 (REFUND_COMPLETED)
     * 6. 도메인 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param request 환불 사유
     * @return 환불된 주문 정보 (refundId, refundAmount 포함)
     */
    @Operation(summary = "주문 환불", description = "배송 완료된 주문을 환불 처리합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "주문 환불 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청 또는 환불 불가능한 상태"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(responseCode = "403", description = "권한 없음"),
            ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
        ],
    )
    @PatchMapping("/{orderId}/refund")
    // Istio Gateway handles authorization
    fun refundOrder(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: RefundOrderRequest,
        httpRequest: HttpServletRequest,
    ): RefundOrderResponse {
        val userId = istioHeaderExtractor.extractUserId(httpRequest)
        logger.info { "Refunding order: $orderId, user: $userId" }

        val command =
            RefundOrderCommand(
                orderId = orderId,
                requestUserId = userId,
                refundReason = request.refundReason,
            )

        val result = refundOrderService.refundOrder(command)

        logger.info { "Order refunded successfully: ${result.orderNumber}, refundId: ${result.refundId}" }

        return RefundOrderResponse.from(result)
    }
}
