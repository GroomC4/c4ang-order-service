package com.groom.order.adapter.inbound.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * 주문 생성 요청 DTO
 */
@Schema(description = "주문 생성 요청")
data class CreateOrderRequest(
    @field:NotBlank(message = "Store ID is required")
    @Schema(description = "상점 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    val storeId: UUID,
    @field:NotBlank(message = "Idempotency key is required")
    @field:Size(max = 100, message = "Idempotency key must be less than 100 characters")
    @Schema(description = "멱등성 키 (중복 주문 방지)", example = "order-20250129-user123-001")
    val idempotencyKey: String,
    @field:NotEmpty(message = "Order items cannot be empty")
    @Schema(description = "주문 항목 목록")
    val items: List<OrderItemRequest>,
    @field:Size(max = 500, message = "Note must be less than 500 characters")
    @Schema(description = "주문 메모", example = "배송 전 연락 부탁드립니다", nullable = true)
    val note: String? = null,
) {
    @Schema(description = "주문 항목")
    data class OrderItemRequest(
        @Schema(description = "상품 ID", example = "123e4567-e89b-12d3-a456-426614174001")
        val productId: UUID,
        @field:Min(value = 1, message = "Quantity must be at least 1")
        @Schema(description = "수량", example = "2", minimum = "1")
        val quantity: Int,
    )
}
