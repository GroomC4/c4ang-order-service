package com.groom.order.presentation.web.dto

import com.groom.order.application.dto.RefundOrderResult
import com.groom.order.domain.model.OrderStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

/**
 * 주문 환불 응답 DTO
 */
@Schema(description = "주문 환불 응답")
data class RefundOrderResponse(
    @Schema(description = "주문 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    val orderId: UUID,
    @Schema(description = "주문 번호", example = "ORD-20250129-0001")
    val orderNumber: String,
    @Schema(description = "주문 상태", example = "REFUND_COMPLETED")
    val status: OrderStatus,
    @Schema(description = "환불 ID", example = "RFD-20250129-0001")
    val refundId: String,
    @Schema(description = "환불 금액", example = "50000")
    val refundAmount: BigDecimal,
    @Schema(description = "환불 사유", example = "제품에 하자가 있음", nullable = true)
    val refundReason: String?,
) {
    companion object {
        fun from(result: RefundOrderResult): RefundOrderResponse =
            RefundOrderResponse(
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                status = result.status,
                refundId = result.refundId,
                refundAmount = result.refundAmount,
                refundReason = result.refundReason,
            )
    }
}
