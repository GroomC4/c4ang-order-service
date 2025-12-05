package com.groom.order.adapter.inbound.web.internal.dto

import com.groom.order.application.dto.CheckHasPaymentResult
import com.groom.order.application.dto.GetInternalOrderResult
import com.groom.order.application.dto.MarkPaymentPendingResult
import java.util.UUID

/**
 * Internal API - 주문 조회 응답
 *
 * Payment Service에서 결제 요청 시 주문 정보 검증에 사용합니다.
 */
data class GetOrderResponse(
    val orderId: UUID,
    val userId: UUID,
    val orderNumber: String,
    val status: String,
    val totalAmount: Long,
    val items: List<OrderItemDto>,
) {
    data class OrderItemDto(
        val productId: UUID,
        val productName: String,
        val quantity: Int,
        val unitPrice: Long,
    )

    companion object {
        fun from(result: GetInternalOrderResult): GetOrderResponse =
            GetOrderResponse(
                orderId = result.orderId,
                userId = result.userId,
                orderNumber = result.orderNumber,
                status = result.status.name,
                totalAmount = result.totalAmount.toLong(),
                items = result.items.map { item ->
                    OrderItemDto(
                        productId = item.productId,
                        productName = item.productName,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice.toLong(),
                    )
                },
            )
    }
}

/**
 * Internal API - 결제 대기 상태 변경 요청
 */
data class MarkPaymentPendingRequest(
    val paymentId: UUID,
)

/**
 * Internal API - 결제 대기 상태 변경 응답
 */
data class MarkPaymentPendingResponse(
    val orderId: UUID,
    val status: String,
    val paymentId: UUID,
) {
    companion object {
        fun from(result: MarkPaymentPendingResult): MarkPaymentPendingResponse =
            MarkPaymentPendingResponse(
                orderId = result.orderId,
                status = result.status.name,
                paymentId = result.paymentId,
            )
    }
}

/**
 * Internal API - 결제 존재 여부 확인 응답
 */
data class HasPaymentResponse(
    val hasPayment: Boolean,
) {
    companion object {
        fun from(result: CheckHasPaymentResult): HasPaymentResponse =
            HasPaymentResponse(
                hasPayment = result.hasPayment,
            )
    }
}
