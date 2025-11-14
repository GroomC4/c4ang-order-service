package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 결제 요청 이벤트
 *
 * 재고 예약 후 결제 프로세스가 시작될 때 발행 (PAYMENT_PENDING 상태)
 */
data class PaymentRequestedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val userExternalId: UUID,
    val paymentAmount: Long,
    val expiresAt: LocalDateTime,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
