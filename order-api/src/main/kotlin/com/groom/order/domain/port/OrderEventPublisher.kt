package com.groom.order.domain.port

import com.groom.order.domain.model.Order
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 이벤트 발행 포트
 *
 * Order Service에서 Kafka로 발행하는 이벤트:
 * - order.created: 주문 생성 이벤트 (Product Service가 재고 예약 처리)
 * - order.confirmed: 주문 확정 이벤트 (Payment Service가 결제 대기 생성)
 * - order.cancelled: 주문 취소 이벤트 (Product Service가 재고 복원 처리)
 * - order.expiration.notification: 주문 만료 알림 (Notification Service가 고객 알림 발송)
 * - analytics.daily.statistics: 일일 통계 (Analytics Service가 리포트 생성)
 * - order.stock.confirmed: 재고 확정 성공 (Payment Saga 완료)
 * - saga.stock-confirmation.failed: 재고 확정 실패 (Payment Service가 보상 트랜잭션 실행)
 *
 * @see com.groom.order.adapter.outbound.messaging.KafkaOrderEventPublisher
 * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
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

    /**
     * 주문 만료 알림 이벤트 발행
     *
     * 결제 시간 초과로 주문이 만료될 때 Notification Service에 알림 요청
     *
     * @param orderId 만료된 주문 ID
     * @param userId 알림 수신 대상 사용자 ID
     * @param expirationReason 만료 사유
     * @param expiredAt 만료 시각
     */
    fun publishOrderExpirationNotification(
        orderId: UUID,
        userId: UUID,
        expirationReason: String,
        expiredAt: LocalDateTime,
    )

    /**
     * 일일 통계 이벤트 발행
     *
     * 매일 자정에 전일 주문 통계를 집계하여 Analytics Service에 전달
     *
     * @param statistics 일일 통계 데이터
     */
    fun publishDailyStatistics(statistics: DailyStatisticsData)

    /**
     * 재고 확정 성공 이벤트 발행 (Payment Saga 완료)
     *
     * payment.completed 이벤트를 받아 재고를 확정한 후 발행합니다.
     * Payment Service가 이 이벤트를 수신하여 Saga를 완료 처리합니다.
     *
     * 토픽: order.stock.confirmed
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param confirmedItems 확정된 상품 목록
     * @param confirmedAt 확정 시각
     */
    fun publishStockConfirmed(
        orderId: UUID,
        paymentId: UUID,
        confirmedItems: List<ConfirmedItemInfo>,
        confirmedAt: LocalDateTime,
    )

    /**
     * 재고 확정 실패 이벤트 발행 (SAGA 보상 트랜잭션 트리거)
     *
     * payment.completed 이벤트를 받아 재고 확정 시 실패하면 발행합니다.
     * Payment Service가 이 이벤트를 수신하여 결제를 취소합니다.
     *
     * 토픽: saga.stock-confirmation.failed
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param failureReason 실패 사유
     * @param failedAt 실패 시각
     */
    fun publishStockConfirmationFailed(
        orderId: UUID,
        paymentId: UUID,
        failureReason: String,
        failedAt: LocalDateTime,
    )

    /**
     * 확정된 상품 정보
     */
    data class ConfirmedItemInfo(
        val productId: UUID,
        val quantity: Int,
    )

    /**
     * 일일 통계 데이터
     */
    data class DailyStatisticsData(
        val date: LocalDate,
        val totalOrders: Int,
        val totalSales: BigDecimal,
        val avgOrderAmount: BigDecimal,
        val topProducts: List<TopProductData>,
    )

    /**
     * 인기 상품 데이터
     */
    data class TopProductData(
        val productId: UUID,
        val productName: String,
        val totalSold: Int,
    )
}
