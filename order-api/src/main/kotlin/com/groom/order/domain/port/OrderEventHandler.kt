package com.groom.order.domain.port

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 관련 외부 이벤트 핸들러 (Inbound Port)
 *
 * 다른 서비스에서 발행한 이벤트를 처리하는 인터페이스입니다.
 * Kafka Consumer가 이 인터페이스를 통해 도메인 로직을 호출합니다.
 *
 * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
 */
interface OrderEventHandler {
    /**
     * 재고 예약 완료 이벤트 처리
     *
     * Product Service에서 재고 예약이 완료되면 호출됩니다.
     * 주문 상태: ORDER_CREATED → ORDER_CONFIRMED → (OrderConfirmed 이벤트 발행)
     *
     * @param orderId 주문 ID
     * @param reservedItems 예약된 상품 목록
     * @param reservedAt 예약 완료 시각
     */
    fun handleStockReserved(
        orderId: UUID,
        reservedItems: List<ReservedItemInfo>,
        reservedAt: LocalDateTime,
    )

    /**
     * 재고 예약 실패 이벤트 처리
     *
     * Product Service에서 재고 예약이 실패하면 호출됩니다.
     * 주문 상태: ORDER_CREATED → ORDER_CANCELLED
     *
     * @param orderId 주문 ID
     * @param failedItems 예약 실패한 상품 목록
     * @param failureReason 실패 사유
     * @param failedAt 실패 시각
     */
    fun handleStockReservationFailed(
        orderId: UUID,
        failedItems: List<FailedItemInfo>,
        failureReason: String,
        failedAt: LocalDateTime,
    )

    /**
     * 결제 완료 이벤트 처리
     *
     * Payment Service에서 결제가 완료되면 호출됩니다.
     * 주문 상태: PAYMENT_PENDING → PAYMENT_COMPLETED → PREPARING
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param totalAmount 결제 금액
     * @param completedAt 결제 완료 시각
     */
    fun handlePaymentCompleted(
        orderId: UUID,
        paymentId: UUID,
        totalAmount: BigDecimal,
        completedAt: LocalDateTime,
    )

    /**
     * 결제 실패 이벤트 처리
     *
     * Payment Service에서 결제가 실패하면 호출됩니다.
     * 주문 상태: PAYMENT_PENDING → ORDER_CANCELLED
     * (재고 복원을 위해 OrderCancelled 이벤트 발행)
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param failureReason 실패 사유
     * @param failedAt 실패 시각
     */
    fun handlePaymentFailed(
        orderId: UUID,
        paymentId: UUID,
        failureReason: String,
        failedAt: LocalDateTime,
    )

    /**
     * 결제 취소 이벤트 처리
     *
     * Payment Service에서 결제가 취소되면 호출됩니다.
     * 주문 상태: → ORDER_CANCELLED
     * (재고 복원을 위해 OrderCancelled 이벤트 발행)
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param cancellationReason 취소 사유
     * @param cancelledAt 취소 시각
     */
    fun handlePaymentCancelled(
        orderId: UUID,
        paymentId: UUID,
        cancellationReason: String,
        cancelledAt: LocalDateTime,
    )

    /**
     * 결제 대기 생성 실패 이벤트 처리 (SAGA 보상)
     *
     * Payment Service에서 결제 대기 생성이 실패하면 호출됩니다.
     * 주문 상태: ORDER_CONFIRMED → ORDER_CANCELLED
     * (재고 복원을 위해 OrderCancelled 이벤트 발행)
     *
     * 토픽: saga.payment-initialization.failed
     *
     * @param orderId 주문 ID
     * @param failureReason 실패 사유
     * @param failedAt 실패 시각
     */
    fun handlePaymentInitializationFailed(
        orderId: UUID,
        failureReason: String,
        failedAt: LocalDateTime,
    )

    /**
     * 결제 완료 후 재고 확정 실패에 대한 보상 이벤트 처리 (SAGA 보상)
     *
     * Product Service에서 재고 확정이 실패하면 Payment Service가 환불 처리 후
     * 이 이벤트를 발행합니다. Order Service는 주문을 취소합니다.
     *
     * 주문 상태: PAYMENT_COMPLETED → ORDER_CANCELLED
     *
     * 토픽: saga.payment-completion.compensate
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param compensationReason 보상 사유 (재고 확정 실패)
     * @param compensatedAt 보상 처리 시각
     */
    fun handlePaymentCompletionCompensate(
        orderId: UUID,
        paymentId: UUID,
        compensationReason: String,
        compensatedAt: LocalDateTime,
    )

    /**
     * 예약된 상품 정보
     */
    data class ReservedItemInfo(
        val productId: UUID,
        val quantity: Int,
        val reservedStock: Int,
    )

    /**
     * 예약 실패한 상품 정보
     */
    data class FailedItemInfo(
        val productId: UUID,
        val requestedQuantity: Int,
        val availableStock: Int,
    )
}
