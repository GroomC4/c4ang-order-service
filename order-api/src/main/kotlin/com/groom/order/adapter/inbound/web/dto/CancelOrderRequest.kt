package com.groom.order.adapter.inbound.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

/**
 * 주문 취소 요청 DTO
 */
@Schema(description = "주문 취소 요청")
data class CancelOrderRequest(
    @field:Size(max = 500, message = "Cancel reason must be less than 500 characters")
    @Schema(description = "취소 사유", example = "상품이 마음에 들지 않아서", nullable = true)
    val cancelReason: String? = null,
)
