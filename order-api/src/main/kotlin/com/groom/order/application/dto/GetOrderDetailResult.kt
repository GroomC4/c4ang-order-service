package com.groom.order.application.dto

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 상세 조회 Result DTO
 */
data class GetOrderDetailResult(
    val orderId: UUID,
    val orderNumber: String,
    val userExternalId: UUID,
    val storeId: UUID,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val reservationId: String?,
    val paymentId: UUID?,
    val expiresAt: LocalDateTime?,
    val confirmedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val failureReason: String?,
    val refundId: String?,
    val note: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val items: List<OrderItemInfo>,
) {
    data class OrderItemInfo(
        val productId: UUID,
        val productName: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val subtotal: BigDecimal,
    ) {
        constructor(orderItem: OrderItem) : this(
            productId = orderItem.productId,
            productName = orderItem.productName,
            quantity = orderItem.quantity,
            unitPrice = orderItem.unitPrice,
            subtotal = orderItem.unitPrice.multiply(BigDecimal(orderItem.quantity)),
        )
    }

    companion object {
        fun from(order: Order): GetOrderDetailResult {
            val totalAmount =
                order.items.sumOf {
                    it.unitPrice.multiply(BigDecimal(it.quantity))
                }

            return GetOrderDetailResult(
                orderId = order.id,
                orderNumber = order.orderNumber,
                userExternalId = order.userExternalId,
                storeId = order.storeId,
                status = order.status,
                totalAmount = totalAmount,
                reservationId = order.reservationId,
                paymentId = order.paymentId,
                expiresAt = order.expiresAt,
                confirmedAt = order.confirmedAt,
                cancelledAt = order.cancelledAt,
                failureReason = order.failureReason,
                refundId = order.refundId,
                note = order.note,
                createdAt = order.createdAt!!,
                updatedAt = order.updatedAt!!,
                items = order.items.map(::OrderItemInfo),
            )
        }
    }
}
