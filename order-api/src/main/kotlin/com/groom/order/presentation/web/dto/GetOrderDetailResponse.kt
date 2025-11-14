package com.groom.order.presentation.web.dto

import com.groom.order.application.dto.GetOrderDetailResult
import com.groom.order.domain.model.OrderStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 상세 조회 응답 DTO
 */
@Schema(description = "주문 상세 조회 응답")
data class GetOrderDetailResponse(
    @Schema(description = "주문 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    val orderId: UUID,
    @Schema(description = "주문 번호", example = "ORD-20250129-0001")
    val orderNumber: String,
    @Schema(description = "사용자 외부 ID", example = "123e4567-e89b-12d3-a456-426614174002")
    val userExternalId: UUID,
    @Schema(description = "상점 ID", example = "123e4567-e89b-12d3-a456-426614174003")
    val storeId: UUID,
    @Schema(description = "주문 상태", example = "STOCK_RESERVED")
    val status: OrderStatus,
    @Schema(description = "총 금액", example = "50000")
    val totalAmount: BigDecimal,
    @Schema(description = "재고 예약 ID", example = "RSV-20250129-0001", nullable = true)
    val reservationId: String?,
    @Schema(description = "결제 ID", example = "123e4567-e89b-12d3-a456-426614174004", nullable = true)
    val paymentId: UUID?,
    @Schema(description = "예약 만료 시각", example = "2025-01-29T12:00:00", nullable = true)
    val expiresAt: LocalDateTime?,
    @Schema(description = "확정 일시", example = "2025-01-29T12:00:00", nullable = true)
    val confirmedAt: LocalDateTime?,
    @Schema(description = "취소 일시", example = "2025-01-29T12:00:00", nullable = true)
    val cancelledAt: LocalDateTime?,
    @Schema(description = "실패 사유", example = "재고 부족", nullable = true)
    val failureReason: String?,
    @Schema(description = "환불 ID", example = "RFD-20250129-0001", nullable = true)
    val refundId: String?,
    @Schema(description = "주문 메모", example = "배송 전 연락 부탁드립니다", nullable = true)
    val note: String?,
    @Schema(description = "생성 일시", example = "2025-01-29T11:50:00")
    val createdAt: LocalDateTime,
    @Schema(description = "수정 일시", example = "2025-01-29T12:00:00")
    val updatedAt: LocalDateTime,
    @Schema(description = "주문 항목 목록")
    val items: List<OrderItemInfo>,
) {
    @Schema(description = "주문 항목 정보")
    data class OrderItemInfo(
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
        fun from(result: GetOrderDetailResult): GetOrderDetailResponse =
            GetOrderDetailResponse(
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                userExternalId = result.userExternalId,
                storeId = result.storeId,
                status = result.status,
                totalAmount = result.totalAmount,
                reservationId = result.reservationId,
                paymentId = result.paymentId,
                expiresAt = result.expiresAt,
                confirmedAt = result.confirmedAt,
                cancelledAt = result.cancelledAt,
                failureReason = result.failureReason,
                refundId = result.refundId,
                note = result.note,
                createdAt = result.createdAt,
                updatedAt = result.updatedAt,
                items =
                    result.items.map {
                        OrderItemInfo(
                            productId = it.productId,
                            productName = it.productName,
                            quantity = it.quantity,
                            unitPrice = it.unitPrice,
                            subtotal = it.subtotal,
                        )
                    },
            )
    }
}
