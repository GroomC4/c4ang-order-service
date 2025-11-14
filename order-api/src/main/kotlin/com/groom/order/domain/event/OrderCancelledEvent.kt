package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 취소 이벤트
 *
 * 사용자가 주문을 취소했을 때 발행 (ORDER_CANCELLED 상태)
 */
data class OrderCancelledEvent(
    val orderId: UUID,
    val orderNumber: String,
    val userExternalId: UUID,
    val storeId: UUID,
    val cancelReason: String?,
    val cancelledAt: LocalDateTime,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
