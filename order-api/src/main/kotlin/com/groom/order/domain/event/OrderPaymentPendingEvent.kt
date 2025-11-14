package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 결제 대기 이벤트
 *
 * Payment 엔티티가 생성되고 Order와 연결되었을 때 발행 (PAYMENT_PENDING 상태)
 */
data class OrderPaymentPendingEvent(
    val orderId: UUID,
    val paymentId: UUID,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
