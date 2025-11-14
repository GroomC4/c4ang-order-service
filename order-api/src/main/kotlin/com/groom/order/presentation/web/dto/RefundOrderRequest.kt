package com.groom.order.presentation.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

/**
 * 주문 환불 요청 DTO
 */
@Schema(description = "주문 환불 요청")
data class RefundOrderRequest(
    @field:Size(max = 500, message = "Refund reason must be less than 500 characters")
    @Schema(description = "환불 사유", example = "제품에 하자가 있음", nullable = true)
    val refundReason: String? = null,
)
