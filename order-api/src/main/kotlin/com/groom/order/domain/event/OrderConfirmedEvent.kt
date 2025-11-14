package com.groom.order.domain.event

import com.groom.order.common.domain.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 확정 이벤트
 *
 * 결제 완료 후 주문이 확정되고 상품 준비 단계로 진입할 때 발행 (PREPARING 상태)
 */
data class OrderConfirmedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val storeId: UUID,
    val confirmedAt: LocalDateTime,
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent
