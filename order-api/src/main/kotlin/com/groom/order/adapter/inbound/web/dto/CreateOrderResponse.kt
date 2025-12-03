package com.groom.order.adapter.inbound.web.dto

import com.groom.order.application.dto.CreateOrderResult
import com.groom.order.domain.model.OrderStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 생성 응답 DTO
 */
@Schema(description = "주문 생성 응답")
data class CreateOrderResponse(
    @Schema(description = "주문 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    val orderId: UUID,
    @Schema(description = "주문 번호", example = "ORD-20250129-0001")
    val orderNumber: String,
    @Schema(description = "주문 상태", example = "STOCK_RESERVED")
    val status: OrderStatus,
    @Schema(description = "총 금액", example = "50000")
    val totalAmount: BigDecimal,
    @Schema(description = "재고 예약 ID", example = "RSV-20250129-0001", nullable = true)
    val reservationId: String?,
    @Schema(description = "예약 만료 시각", example = "2025-01-29T12:00:00", nullable = true)
    val expiresAt: LocalDateTime?,
    @Schema(description = "생성 일시", example = "2025-01-29T11:50:00")
    val createdAt: LocalDateTime,
    @Schema(description = "주문 항목 목록")
    val items: List<OrderItemResponse>,
    @Schema(description = "주문 메모", example = "빨리 배달해주세요", nullable = true)
    val note: String?,
) {
    @Schema(description = "주문 항목 정보")
    data class OrderItemResponse(
        @Schema(description = "상품 ID", example = "123e4567-e89b-12d3-a456-426614174001")
        val productId: UUID,
        @Schema(description = "상품명", example = "프리미엄 커피")
        val productName: String,
        @Schema(description = "수량", example = "2")
        val quantity: Int,
        @Schema(description = "단가", example = "15000")
        val unitPrice: BigDecimal,
        @Schema(description = "소계", example = "30000")
        val subtotal: BigDecimal,
    )

    companion object {
        fun from(result: CreateOrderResult): CreateOrderResponse =
            CreateOrderResponse(
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                status = result.status,
                totalAmount = result.totalAmount,
                reservationId = result.reservationId,
                expiresAt = result.expiresAt,
                createdAt = result.createdAt,
                items =
                    result.items.map { item ->
                        OrderItemResponse(
                            productId = item.productId,
                            productName = item.productName,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice,
                            subtotal = item.subtotal,
                        )
                    },
                note = result.note,
            )
    }
}
