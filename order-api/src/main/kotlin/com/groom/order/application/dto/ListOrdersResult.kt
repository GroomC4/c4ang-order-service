package com.groom.order.application.dto

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 리스트 조회 Result DTO
 */
data class ListOrdersResult(
    val orders: List<OrderSummary>,
) {
    data class OrderSummary(
        val orderId: UUID,
        val orderNumber: String,
        val storeId: UUID,
        val status: OrderStatus,
        val totalAmount: BigDecimal,
        val itemCount: Int,
        val createdAt: LocalDateTime,
    )

    companion object {
        fun from(orders: List<Order>): ListOrdersResult {
            val summaries =
                orders.map { order ->
                    val totalAmount =
                        order.items.sumOf {
                            it.unitPrice.multiply(BigDecimal(it.quantity))
                        }

                    OrderSummary(
                        orderId = order.id,
                        orderNumber = order.orderNumber,
                        storeId = order.storeId,
                        status = order.status,
                        totalAmount = totalAmount,
                        itemCount = order.items.size,
                        createdAt = order.createdAt ?: LocalDateTime.now(),
                    )
                }

            return ListOrdersResult(orders = summaries)
        }
    }
}
