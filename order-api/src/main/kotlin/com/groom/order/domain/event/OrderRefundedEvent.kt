package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 환불 이벤트
 *
 * 반품/환불이 완료되었을 때 발행 (REFUND_COMPLETED 상태)
 */
data class OrderRefundedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val userExternalId: UUID,
    val refundAmount: Long,
    val refundId: String,
    val refundReason: String?,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
