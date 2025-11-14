package com.groom.order.domain.event

import com.groom.ecommerce.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 타임아웃 이벤트
 *
 * 결제 시간이 초과되어 주문이 자동으로 취소될 때 발행 (PAYMENT_TIMEOUT 상태)
 */
data class OrderTimeoutEvent(
    val orderId: UUID,
    val orderNumber: String,
    val storeId: UUID,
    val reservationId: String?,
    val paymentId: UUID?,
    val timeoutAt: LocalDateTime,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
