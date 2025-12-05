package com.groom.order.configuration.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * Kafka 토픽 설정
 *
 * Order Service에서 발행/소비하는 토픽 목록
 *
 * @see <a href="https://github.com/c4ang/c4ang-contract-hub/blob/main/docs/interface/kafka-event-specifications.md">Kafka 이벤트 명세서</a>
 *
 * application.yml 예시:
 * ```yaml
 * kafka:
 *   topics:
 *     # Producer Topics - Business Events
 *     order-created: order.created
 *     order-confirmed: order.confirmed
 *     order-cancelled: order.cancelled
 *     order-expiration-notification: order.expiration.notification
 *     daily-statistics: analytics.daily.statistics
 *     # Producer Topics - Payment Saga
 *     order-stock-confirmed: order.stock.confirmed
 *     # Producer Topics - SAGA Events
 *     saga-stock-confirmation-failed: saga.stock-confirmation.failed
 *     saga-order-confirmation-compensate: saga.order-confirmation.compensate
 *     # Consumer Topics - Business Events
 *     stock-reserved: stock.reserved
 *     payment-completed: payment.completed
 *     payment-failed: payment.failed
 *     # Consumer Topics - SAGA Events
 *     saga-stock-reservation-failed: saga.stock-reservation.failed
 *     saga-payment-initialization-failed: saga.payment-initialization.failed
 *     saga-payment-completion-compensate: saga.payment-completion.compensate
 * ```
 */
@ConfigurationProperties(prefix = "kafka.topics")
data class KafkaTopicProperties(
    // ===== Producer Topics - Business Events =====
    @DefaultValue("order.created")
    val orderCreated: String = "order.created",
    @DefaultValue("order.confirmed")
    val orderConfirmed: String = "order.confirmed",
    @DefaultValue("order.cancelled")
    val orderCancelled: String = "order.cancelled",
    @DefaultValue("order.expiration.notification")
    val orderExpirationNotification: String = "order.expiration.notification",
    @DefaultValue("analytics.daily.statistics")
    val dailyStatistics: String = "analytics.daily.statistics",
    // ===== Producer Topics - Payment Saga =====
    @DefaultValue("order.stock.confirmed")
    val orderStockConfirmed: String = "order.stock.confirmed",
    // ===== Producer Topics - SAGA Events =====
    @DefaultValue("saga.stock-confirmation.failed")
    val sagaStockConfirmationFailed: String = "saga.stock-confirmation.failed",
    @DefaultValue("saga.order-confirmation.compensate")
    val sagaOrderConfirmationCompensate: String = "saga.order-confirmation.compensate",
    // ===== Consumer Topics - Business Events =====
    @DefaultValue("stock.reserved")
    val stockReserved: String = "stock.reserved",
    @DefaultValue("payment.completed")
    val paymentCompleted: String = "payment.completed",
    @DefaultValue("payment.failed")
    val paymentFailed: String = "payment.failed",
    // ===== Consumer Topics - SAGA Events =====
    @DefaultValue("saga.stock-reservation.failed")
    val sagaStockReservationFailed: String = "saga.stock-reservation.failed",
    @DefaultValue("saga.payment-initialization.failed")
    val sagaPaymentInitializationFailed: String = "saga.payment-initialization.failed",
    @DefaultValue("saga.payment-completion.compensate")
    val sagaPaymentCompletionCompensate: String = "saga.payment-completion.compensate",
)
