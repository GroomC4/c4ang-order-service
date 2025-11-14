package com.groom.order.presentation.web.dto

import com.groom.order.application.dto.CancelOrderResult
import com.groom.order.domain.model.OrderStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 취소 응답 DTO
 */
@Schema(description = "주문 취소 응답")
data class CancelOrderResponse(
    @Schema(description = "주문 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    val orderId: UUID,
    @Schema(description = "주문 번호", example = "ORD-20250129-0001")
    val orderNumber: String,
    @Schema(description = "주문 상태", example = "ORDER_CANCELLED")
    val status: OrderStatus,
    @Schema(description = "취소 사유", example = "상품이 마음에 들지 않아서", nullable = true)
    val cancelReason: String?,
    @Schema(description = "취소 일시", example = "2025-01-29T12:00:00", nullable = true)
    val cancelledAt: LocalDateTime?,
) {
    companion object {
        fun from(result: CancelOrderResult): CancelOrderResponse =
            CancelOrderResponse(
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                status = result.status,
                cancelReason = result.cancelReason,
                cancelledAt = result.cancelledAt,
            )
    }
}
