package com.groom.order.domain.model

/**
 * 주문 상태 (이벤트 기반 비동기 플로우)
 *
 * 상태 전이:
 * - ORDER_CREATED → ORDER_CONFIRMED (stock.reserved 이벤트)
 * - ORDER_CREATED → ORDER_CANCELLED (stock.reservation.failed 이벤트)
 * - ORDER_CONFIRMED → PAYMENT_PENDING (order.confirmed 이벤트 → Payment Service)
 */
enum class OrderStatus {
    // ===== 주문 접수 단계 =====

    /**
     * 주문 생성됨 (재고 확인 대기 중)
     * - order.created 이벤트 발행됨
     * - Product Service에서 재고 예약 진행 중
     */
    ORDER_CREATED,

    /**
     * 주문 확정됨 (재고 예약 완료)
     * - stock.reserved 이벤트 수신
     * - order.confirmed 이벤트 발행
     */
    ORDER_CONFIRMED,

    // ===== 결제 단계 =====

    /**
     * 결제 대기 중
     */
    PAYMENT_PENDING,

    /**
     * 결제 진행 중
     */
    PAYMENT_PROCESSING,

    /**
     * 결제 완료
     */
    PAYMENT_COMPLETED,

    // ===== 배송 단계 =====

    /**
     * 상품 준비 중
     */
    PREPARING,

    /**
     * 배송 중
     */
    SHIPPED,

    /**
     * 배송 완료
     */
    DELIVERED,

    // ===== 취소/실패 단계 =====

    /**
     * 결제 시간 초과
     */
    PAYMENT_TIMEOUT,

    /**
     * 주문 취소됨 (PAYMENT_COMPLETED 이전)
     */
    ORDER_CANCELLED,

    // ===== 반품/환불 단계 =====

    /**
     * 반품 요청
     */
    RETURN_REQUESTED,

    /**
     * 반품 승인
     */
    RETURN_APPROVED,

    /**
     * 반품 배송 중
     */
    RETURN_IN_TRANSIT,

    /**
     * 반품 완료
     */
    RETURN_COMPLETED,

    /**
     * 환불 처리 중
     */
    REFUND_PROCESSING,

    /**
     * 환불 완료
     */
    REFUND_COMPLETED,

    // ===== 예외 처리 =====

    /**
     * 처리 실패
     */
    FAILED,

    /**
     * 수동 개입 필요
     */
    REQUIRES_MANUAL_INTERVENTION,
}
