package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 생성 이벤트
 *
 * 주문이 최초로 생성되었을 때 발행됩니다.
 * Product Service에서 이 이벤트를 소비하여 재고를 예약합니다.
 *
 * Kafka Topic: order.created
 * 파티션 키: orderId
 */
data class OrderCreatedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val userExternalId: UUID,
    val storeId: UUID,
    val totalAmount: BigDecimal,
    val status: OrderStatus = OrderStatus.ORDER_CREATED,
    val items: List<OrderItem> = emptyList(),
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent {
    /**
     * 주문 상품 정보
     *
     * Product Service에서 재고 예약 시 사용합니다.
     */
    data class OrderItem(
        val productId: UUID,
        val quantity: Int,
    )
}
