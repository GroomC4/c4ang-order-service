package com.groom.order.adapter.inbound.web.dto

import com.groom.order.application.dto.ListOrdersResult
import com.groom.order.domain.model.OrderStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 리스트 조회 응답 DTO
 */
@Schema(description = "주문 목록 조회 응답")
data class ListOrdersResponse(
    @Schema(description = "주문 목록")
    val orders: List<OrderSummary>,
) {
    @Schema(description = "주문 요약 정보")
    data class OrderSummary(
        @Schema(description = "주문 ID", example = "123e4567-e89b-12d3-a456-426614174000")
        val orderId: UUID,
        @Schema(description = "주문 번호", example = "ORD-20250129-0001")
        val orderNumber: String,
        @Schema(description = "상점 ID", example = "123e4567-e89b-12d3-a456-426614174003")
        val storeId: UUID,
        @Schema(description = "주문 상태", example = "STOCK_RESERVED")
        val status: OrderStatus,
        @Schema(description = "총 금액", example = "50000")
        val totalAmount: BigDecimal,
        @Schema(description = "상품 개수", example = "3")
        val itemCount: Int,
        @Schema(description = "생성 일시", example = "2025-01-29T11:50:00")
        val createdAt: LocalDateTime,
    )

    companion object {
        fun from(result: ListOrdersResult): ListOrdersResponse =
            ListOrdersResponse(
                orders =
                    result.orders.map {
                        OrderSummary(
                            orderId = it.orderId,
                            orderNumber = it.orderNumber,
                            storeId = it.storeId,
                            status = it.status,
                            totalAmount = it.totalAmount,
                            itemCount = it.itemCount,
                            createdAt = it.createdAt,
                        )
                    },
            )
    }
}
