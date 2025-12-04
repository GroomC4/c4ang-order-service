package com.groom.order.application.service

import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventHandler
import com.groom.order.domain.port.OrderEventPublisher
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.domain.service.OrderAuditRecorder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 외부 이벤트 핸들러 서비스 (Application Layer)
 *
 * Kafka Consumer로부터 받은 이벤트를 처리합니다.
 * 주문 상태를 업데이트하고 필요한 경우 후속 이벤트를 발행합니다.
 */
@Service
class OrderEventHandlerService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val orderEventPublisher: OrderEventPublisher,
    private val orderAuditRecorder: OrderAuditRecorder,
) : OrderEventHandler {

    private val logger = KotlinLogging.logger {}

    /**
     * 재고 예약 완료 이벤트 처리
     *
     * Product Service에서 재고 예약이 완료되면:
     * 1. 주문 상태를 STOCK_RESERVED로 변경
     * 2. OrderConfirmed 이벤트 발행 (Payment Service가 결제 대기 생성)
     */
    @Transactional
    override fun handleStockReserved(
        orderId: UUID,
        reservedItems: List<OrderEventHandler.ReservedItemInfo>,
        reservedAt: LocalDateTime,
    ) {
        logger.info { "Processing StockReserved event: orderId=$orderId, items=${reservedItems.size}" }

        val order = loadOrderPort.loadById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: PENDING → STOCK_RESERVED
        order.markStockReserved()
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.STOCK_RESERVED,
            changeSummary = "재고 예약 완료 (Kafka 이벤트)",
            actorUserId = null,
            metadata = mapOf(
                "reservedItems" to reservedItems.map { "${it.productId}:${it.quantity}" },
                "reservedAt" to reservedAt.toString(),
            ),
        )

        // OrderConfirmed 이벤트 발행 → Payment Service가 결제 대기 생성
        orderEventPublisher.publishOrderConfirmed(order)

        logger.info { "StockReserved processed: orderId=$orderId, newStatus=${order.status}" }
    }

    /**
     * 결제 완료 이벤트 처리
     *
     * Payment Service에서 결제가 완료되면:
     * 1. 주문 상태를 PAYMENT_COMPLETED로 변경
     * 2. 주문 확정 처리 (PREPARING 상태로 변경)
     */
    @Transactional
    override fun handlePaymentCompleted(
        orderId: UUID,
        paymentId: UUID,
        totalAmount: BigDecimal,
        completedAt: LocalDateTime,
    ) {
        logger.info { "Processing PaymentCompleted event: orderId=$orderId, paymentId=$paymentId" }

        val order = loadOrderPort.loadById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: PAYMENT_PENDING → PAYMENT_COMPLETED
        order.completePayment(paymentId)
        // 상태 전이: PAYMENT_COMPLETED → PREPARING (주문 확정)
        order.confirmOrder(completedAt)
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.PAYMENT_COMPLETED,
            changeSummary = "결제 완료 및 주문 확정 (Kafka 이벤트)",
            actorUserId = null,
            metadata = mapOf(
                "paymentId" to paymentId.toString(),
                "totalAmount" to totalAmount.toString(),
                "completedAt" to completedAt.toString(),
            ),
        )

        logger.info { "PaymentCompleted processed: orderId=$orderId, newStatus=${order.status}" }
    }

    /**
     * 결제 실패 이벤트 처리
     *
     * Payment Service에서 결제가 실패하면:
     * 1. 주문 상태를 ORDER_CANCELLED로 변경
     * 2. OrderCancelled 이벤트 발행 (Product Service가 재고 복원)
     */
    @Transactional
    override fun handlePaymentFailed(
        orderId: UUID,
        paymentId: UUID,
        failureReason: String,
        failedAt: LocalDateTime,
    ) {
        logger.info { "Processing PaymentFailed event: orderId=$orderId, reason=$failureReason" }

        val order = loadOrderPort.loadById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: → ORDER_CANCELLED
        order.cancel("결제 실패: $failureReason", failedAt)
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.ORDER_CANCELLED,
            changeSummary = "결제 실패로 인한 주문 취소",
            actorUserId = null,
            metadata = mapOf(
                "paymentId" to paymentId.toString(),
                "failureReason" to failureReason,
                "failedAt" to failedAt.toString(),
            ),
        )

        // OrderCancelled 이벤트 발행 → Product Service가 재고 복원
        orderEventPublisher.publishOrderCancelled(order, "PAYMENT_FAILED: $failureReason")

        logger.info { "PaymentFailed processed: orderId=$orderId, newStatus=${order.status}" }
    }

    /**
     * 결제 취소 이벤트 처리
     *
     * Payment Service에서 결제가 취소되면:
     * 1. 주문 상태를 ORDER_CANCELLED로 변경
     * 2. OrderCancelled 이벤트 발행 (Product Service가 재고 복원)
     */
    @Transactional
    override fun handlePaymentCancelled(
        orderId: UUID,
        paymentId: UUID,
        cancellationReason: String,
        cancelledAt: LocalDateTime,
    ) {
        logger.info { "Processing PaymentCancelled event: orderId=$orderId, reason=$cancellationReason" }

        val order = loadOrderPort.loadById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: → ORDER_CANCELLED
        order.cancel("결제 취소: $cancellationReason", cancelledAt)
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.ORDER_CANCELLED,
            changeSummary = "결제 취소로 인한 주문 취소",
            actorUserId = null,
            metadata = mapOf(
                "paymentId" to paymentId.toString(),
                "cancellationReason" to cancellationReason,
                "cancelledAt" to cancelledAt.toString(),
            ),
        )

        // OrderCancelled 이벤트 발행 → Product Service가 재고 복원
        orderEventPublisher.publishOrderCancelled(order, "PAYMENT_CANCELLED: $cancellationReason")

        logger.info { "PaymentCancelled processed: orderId=$orderId, newStatus=${order.status}" }
    }
}
