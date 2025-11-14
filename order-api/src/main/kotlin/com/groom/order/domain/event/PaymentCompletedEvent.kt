package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 결제 완료 이벤트
 *
 * PG사로부터 결제 승인을 받았을 때 발행 (PAYMENT_COMPLETED 상태)
 */
data class PaymentCompletedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val paymentId: UUID,
    val paymentAmount: Long,
    val pgTransactionId: String?,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
