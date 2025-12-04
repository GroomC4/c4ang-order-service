package com.groom.order.domain.port

import com.groom.order.domain.model.Order

/**
 * 주문 이벤트 발행 포트
 *
 * Order Service에서 Kafka로 발행하는 이벤트:
 * - order.created: 주문 생성 이벤트 (Product Service가 재고 예약 처리)
 * - order.confirmed: 주문 확정 이벤트 (Payment Service가 결제 대기 생성)
 * - order.cancelled: 주문 취소 이벤트 (Product Service가 재고 복원 처리)
 *
 * @see com.groom.order.adapter.outbound.messaging.KafkaOrderEventPublisher
 */
interface OrderEventPublisher {
    /**
     * 주문 생성 이벤트 발행
     *
     * @param order 생성된 주문
     */
    fun publishOrderCreated(order: Order)

    /**
     * 주문 확정 이벤트 발행
     *
     * @param order 확정된 주문
     */
    fun publishOrderConfirmed(order: Order)

    /**
     * 주문 취소 이벤트 발행
     *
     * @param order 취소된 주문
     * @param cancellationReason 취소 사유
     */
    fun publishOrderCancelled(
        order: Order,
        cancellationReason: String?,
    )
}
