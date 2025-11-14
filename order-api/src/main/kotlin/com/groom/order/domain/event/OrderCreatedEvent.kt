package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 생성 이벤트
 *
 * 주문이 최초로 생성되었을 때 발행 (PENDING 상태)
 */
data class OrderCreatedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val userExternalId: UUID,
    val storeId: UUID,
    val totalAmount: BigDecimal,
    val status: OrderStatus = OrderStatus.PENDING,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
