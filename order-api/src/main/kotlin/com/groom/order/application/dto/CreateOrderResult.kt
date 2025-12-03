package com.groom.order.application.dto

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 생성 Result DTO
 *
 * 생성된 주문의 정보를 반환합니다.
 */
data class CreateOrderResult(
    val orderId: UUID,
    val orderNumber: String,
    val userExternalId: UUID,
    val storeId: UUID,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val reservationId: String?,
    val expiresAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val items: List<OrderItemInfo>,
    val note: String?,
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
        fun from(
            order: Order,
            now: LocalDateTime = LocalDateTime.now(),
        ): CreateOrderResult {
            val totalAmount =
                order.items.sumOf {
                    it.unitPrice.multiply(BigDecimal(it.quantity))
                }

            return CreateOrderResult(
                orderId = order.id,
                orderNumber = order.orderNumber,
                userExternalId = order.userExternalId,
                storeId = order.storeId,
                status = order.status,
                totalAmount = totalAmount,
                reservationId = order.reservationId,
                expiresAt = order.expiresAt,
                createdAt = order.createdAt ?: now,
                items = order.items.map(::OrderItemInfo),
                note = order.note,
            )
        }
    }
}
