package com.groom.order.application.service

import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventHandler
import com.groom.order.domain.port.OrderEventPublisher
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.domain.service.OrderAuditRecorder
import com.groom.platform.saga.SagaSteps
import com.groom.platform.saga.SagaTrackerClient
import com.groom.platform.saga.SagaType
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
    private val sagaTrackerClient: SagaTrackerClient,
) : OrderEventHandler {
    private val logger = KotlinLogging.logger {}

    /**
     * 재고 예약 완료 이벤트 처리
     *
     * Product Service에서 재고 예약이 완료되면:
     * 1. 주문 상태를 ORDER_CONFIRMED로 변경
     * 2. OrderConfirmed 이벤트 발행 (Payment Service가 결제 대기 생성)
     */
    @Transactional
    override fun handleStockReserved(
        orderId: UUID,
        reservedItems: List<OrderEventHandler.ReservedItemInfo>,
        reservedAt: LocalDateTime,
    ) {
        logger.info { "Processing StockReserved event: orderId=$orderId, items=${reservedItems.size}" }

        val order =
            loadOrderPort.loadById(orderId)
                ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: ORDER_CREATED → ORDER_CONFIRMED
        order.confirm()
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.STOCK_RESERVED,
            changeSummary = "재고 예약 완료 (Kafka 이벤트)",
            actorUserId = null,
            metadata =
                mapOf(
                    "reservedItems" to reservedItems.map { "${it.productId}:${it.quantity}" },
                    "reservedAt" to reservedAt.toString(),
                ),
        )

        // OrderConfirmed 이벤트 발행 → Payment Service가 결제 대기 생성
        orderEventPublisher.publishOrderConfirmed(order)

        // Saga Tracker 기록 (STOCK_RESERVED, IN_PROGRESS)
        sagaTrackerClient.recordProgress(
            sagaId = orderId.toString(),
            sagaType = SagaType.ORDER_CREATION,
            step = SagaSteps.STOCK_RESERVED,
            orderId = order.orderNumber,
            metadata = mapOf(
                "reservedItems" to reservedItems.size,
                "reservedAt" to reservedAt.toString(),
            ),
        )

        logger.info { "StockReserved processed: orderId=$orderId, newStatus=${order.status}" }
    }

    /**
     * 재고 예약 실패 이벤트 처리
     *
     * Product Service에서 재고 예약이 실패하면:
     * 1. 주문 상태를 ORDER_CANCELLED로 변경
     * (재고가 예약되지 않았으므로 OrderCancelled 이벤트 발행 불필요)
     */
    @Transactional
    override fun handleStockReservationFailed(
        orderId: UUID,
        failedItems: List<OrderEventHandler.FailedItemInfo>,
        failureReason: String,
        failedAt: LocalDateTime,
    ) {
        logger.info { "Processing StockReservationFailed event: orderId=$orderId, reason=$failureReason" }

        val order =
            loadOrderPort.loadById(orderId)
                ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: ORDER_CREATED → ORDER_CANCELLED
        order.cancel("재고 예약 실패: $failureReason", failedAt)
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.ORDER_CANCELLED,
            changeSummary = "재고 예약 실패로 인한 주문 취소 (Kafka 이벤트)",
            actorUserId = null,
            metadata =
                mapOf(
                    "failedItems" to failedItems.map { "${it.productId}:${it.requestedQuantity}/${it.availableStock}" },
                    "failureReason" to failureReason,
                    "failedAt" to failedAt.toString(),
                ),
        )

        // Note: 재고가 예약되지 않았으므로 OrderCancelled 이벤트 발행 불필요
        // Product Service에서 이미 실패 처리됨

        // Saga Tracker 기록 (STOCK_RESERVATION_FAILED, FAILED)
        sagaTrackerClient.recordFailure(
            sagaId = orderId.toString(),
            sagaType = SagaType.ORDER_CREATION,
            step = SagaSteps.STOCK_RESERVATION_FAILED,
            orderId = order.orderNumber,
            failureReason = failureReason,
            metadata = mapOf(
                "failedItems" to failedItems.size,
                "failedAt" to failedAt.toString(),
            ),
        )

        logger.info { "StockReservationFailed processed: orderId=$orderId, newStatus=${order.status}" }
    }

    /**
     * 결제 완료 이벤트 처리 (Payment Saga)
     *
     * Payment Service에서 결제가 완료되면:
     * 1. 주문 상태를 PAYMENT_COMPLETED로 변경
     * 2. 주문 확정 처리 (PREPARING 상태로 변경)
     * 3. StockConfirmed 이벤트 발행 (Payment Service가 Saga 완료)
     *
     * 실패 시:
     * - StockConfirmationFailed 이벤트 발행 (Payment Service가 결제 취소)
     *
     * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-sequence.md#4-payment-order-saga-결제-완료">Payment-Order Saga</a>
     */
    @Transactional
    override fun handlePaymentCompleted(
        orderId: UUID,
        paymentId: UUID,
        totalAmount: BigDecimal,
        completedAt: LocalDateTime,
    ) {
        logger.info { "Processing PaymentCompleted event: orderId=$orderId, paymentId=$paymentId" }

        val order =
            loadOrderPort.loadById(orderId)
                ?: throw OrderException.OrderNotFound(orderId)

        try {
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
                metadata =
                    mapOf(
                        "paymentId" to paymentId.toString(),
                        "totalAmount" to totalAmount.toString(),
                        "completedAt" to completedAt.toString(),
                    ),
            )

            // StockConfirmed 이벤트 발행 → Payment Service가 Saga 완료 처리
            val confirmedItems =
                order.items.map { item ->
                    OrderEventPublisher.ConfirmedItemInfo(
                        productId = item.productId,
                        quantity = item.quantity,
                    )
                }
            orderEventPublisher.publishStockConfirmed(
                orderId = orderId,
                paymentId = paymentId,
                confirmedItems = confirmedItems,
                confirmedAt = completedAt,
            )

            // Saga Tracker 기록 (ORDER_CONFIRMED, COMPLETED)
            sagaTrackerClient.recordComplete(
                sagaId = orderId.toString(),
                sagaType = SagaType.ORDER_CREATION,
                step = SagaSteps.ORDER_CONFIRMED,
                orderId = order.orderNumber,
                metadata = mapOf(
                    "paymentId" to paymentId.toString(),
                    "totalAmount" to totalAmount.toString(),
                    "completedAt" to completedAt.toString(),
                ),
            )

            logger.info { "PaymentCompleted processed: orderId=$orderId, newStatus=${order.status}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process PaymentCompleted event: orderId=$orderId" }

            // StockConfirmationFailed 이벤트 발행 → Payment Service가 결제 취소
            orderEventPublisher.publishStockConfirmationFailed(
                orderId = orderId,
                paymentId = paymentId,
                failureReason = e.message ?: "재고 확정 실패",
                failedAt = completedAt,
            )

            throw e
        }
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

        val order =
            loadOrderPort.loadById(orderId)
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
            metadata =
                mapOf(
                    "paymentId" to paymentId.toString(),
                    "failureReason" to failureReason,
                    "failedAt" to failedAt.toString(),
                ),
        )

        // OrderCancelled 이벤트 발행 → Product Service가 재고 복원
        orderEventPublisher.publishOrderCancelled(order, "PAYMENT_FAILED: $failureReason")

        // Saga Tracker 기록 (ORDER_CANCELLED, FAILED)
        sagaTrackerClient.recordFailure(
            sagaId = orderId.toString(),
            sagaType = SagaType.ORDER_CREATION,
            step = SagaSteps.ORDER_CANCELLED,
            orderId = order.orderNumber,
            failureReason = "PAYMENT_FAILED: $failureReason",
            metadata = mapOf(
                "paymentId" to paymentId.toString(),
                "failedAt" to failedAt.toString(),
            ),
        )

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

        val order =
            loadOrderPort.loadById(orderId)
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
            metadata =
                mapOf(
                    "paymentId" to paymentId.toString(),
                    "cancellationReason" to cancellationReason,
                    "cancelledAt" to cancelledAt.toString(),
                ),
        )

        // OrderCancelled 이벤트 발행 → Product Service가 재고 복원
        orderEventPublisher.publishOrderCancelled(order, "PAYMENT_CANCELLED: $cancellationReason")

        // Saga Tracker 기록 (ORDER_CANCELLED, FAILED)
        sagaTrackerClient.recordFailure(
            sagaId = orderId.toString(),
            sagaType = SagaType.ORDER_CREATION,
            step = SagaSteps.ORDER_CANCELLED,
            orderId = order.orderNumber,
            failureReason = "PAYMENT_CANCELLED: $cancellationReason",
            metadata = mapOf(
                "paymentId" to paymentId.toString(),
                "cancelledAt" to cancelledAt.toString(),
            ),
        )

        logger.info { "PaymentCancelled processed: orderId=$orderId, newStatus=${order.status}" }
    }

    /**
     * 결제 대기 생성 실패 이벤트 처리 (SAGA 보상)
     *
     * Payment Service에서 결제 대기 생성이 실패하면:
     * 1. 주문 상태를 ORDER_CANCELLED로 변경
     * 2. OrderCancelled 이벤트 발행 (Product Service가 재고 복원)
     *
     * 토픽: saga.payment-initialization.failed
     *
     * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
     */
    @Transactional
    override fun handlePaymentInitializationFailed(
        orderId: UUID,
        failureReason: String,
        failedAt: LocalDateTime,
    ) {
        logger.info { "Processing PaymentInitializationFailed event: orderId=$orderId, reason=$failureReason" }

        val order =
            loadOrderPort.loadById(orderId)
                ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: ORDER_CONFIRMED → ORDER_CANCELLED
        order.cancel("결제 대기 생성 실패: $failureReason", failedAt)
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.ORDER_CANCELLED,
            changeSummary = "결제 대기 생성 실패로 인한 주문 취소 (SAGA 보상)",
            actorUserId = null,
            metadata =
                mapOf(
                    "failureReason" to failureReason,
                    "failedAt" to failedAt.toString(),
                ),
        )

        // OrderCancelled 이벤트 발행 → Product Service가 재고 복원
        orderEventPublisher.publishOrderCancelled(order, "PAYMENT_INITIALIZATION_FAILED: $failureReason")

        // Saga Tracker 기록 (COMPENSATION_ORDER, COMPENSATED)
        sagaTrackerClient.recordCompensation(
            sagaId = orderId.toString(),
            sagaType = SagaType.ORDER_CREATION,
            step = SagaSteps.COMPENSATION_ORDER,
            orderId = order.orderNumber,
            metadata = mapOf(
                "compensationReason" to "PAYMENT_INITIALIZATION_FAILED: $failureReason",
                "failedAt" to failedAt.toString(),
            ),
        )

        logger.info { "PaymentInitializationFailed processed: orderId=$orderId, newStatus=${order.status}" }
    }

    /**
     * 결제 완료 후 재고 확정 실패에 대한 보상 이벤트 처리 (SAGA 보상)
     *
     * Product Service에서 재고 확정이 실패하면 Payment Service가 환불 처리 후
     * 이 이벤트를 발행합니다. Order Service는 주문을 취소합니다.
     *
     * 주문 상태: PAYMENT_COMPLETED → ORDER_CANCELLED
     *
     * 토픽: saga.payment-completion.compensate
     */
    @Transactional
    override fun handlePaymentCompletionCompensate(
        orderId: UUID,
        paymentId: UUID,
        compensationReason: String,
        compensatedAt: LocalDateTime,
    ) {
        logger.info {
            "Processing PaymentCompletionCompensate event: orderId=$orderId, " +
                "paymentId=$paymentId, reason=$compensationReason"
        }

        val order =
            loadOrderPort.loadById(orderId)
                ?: throw OrderException.OrderNotFound(orderId)

        // 상태 전이: PAYMENT_COMPLETED → ORDER_CANCELLED
        order.cancel("결제 보상 처리: $compensationReason", compensatedAt)
        saveOrderPort.save(order)

        // 감사 로그 기록
        orderAuditRecorder.record(
            orderId = orderId,
            eventType = OrderAuditEventType.ORDER_CANCELLED,
            changeSummary = "결제 완료 후 재고 확정 실패로 인한 주문 취소 (SAGA 보상)",
            actorUserId = null,
            metadata =
                mapOf(
                    "paymentId" to paymentId.toString(),
                    "compensationReason" to compensationReason,
                    "compensatedAt" to compensatedAt.toString(),
                ),
        )

        // Note: 이미 Payment Service에서 환불 처리됨, Product Service는 재고 확정 전이므로
        // 추가 이벤트 발행 불필요 (Redis 예약은 TTL로 자동 만료됨)

        // Saga Tracker 기록 (COMPENSATION_ORDER, COMPENSATED)
        sagaTrackerClient.recordCompensation(
            sagaId = orderId.toString(),
            sagaType = SagaType.ORDER_CREATION,
            step = SagaSteps.COMPENSATION_ORDER,
            orderId = order.orderNumber,
            metadata = mapOf(
                "paymentId" to paymentId.toString(),
                "compensationReason" to compensationReason,
                "compensatedAt" to compensatedAt.toString(),
            ),
        )

        logger.info { "PaymentCompletionCompensate processed: orderId=$orderId, newStatus=${order.status}" }
    }
}
