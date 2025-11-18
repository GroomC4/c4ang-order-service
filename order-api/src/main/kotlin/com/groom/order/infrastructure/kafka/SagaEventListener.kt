package com.groom.order.infrastructure.kafka

import com.groom.ecommerce.payment.event.avro.PaymentCompleted
import com.groom.ecommerce.payment.event.avro.PaymentFailed
import com.groom.ecommerce.product.event.avro.StockReserved
import com.groom.ecommerce.saga.event.avro.StockReservationFailed
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * SAGA 패턴 이벤트 리스너
 *
 * Order Creation SAGA 및 Payment Processing SAGA의 이벤트를 소비하여
 * 주문 상태를 업데이트하고 다음 단계 이벤트를 발행합니다.
 */
@Component
class SagaEventListener(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val orderEventPublisher: OrderEventPublisher,
) {
    /**
     * 재고 예약 성공 이벤트 처리
     *
     * Product Service가 재고를 성공적으로 예약하면 주문을 확정하고
     * OrderConfirmed 이벤트를 발행합니다.
     *
     * SAGA Flow: OrderCreated → StockReserved → OrderConfirmed
     *
     * @param event StockReserved 이벤트
     */
    @Transactional
    @KafkaListener(
        topics = ["stock.reserved"],
        groupId = "order-service-saga",
        containerFactory = "avroKafkaListenerContainerFactory",
    )
    fun handleStockReserved(
        @Payload event: StockReserved,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val orderId = UUID.fromString(event.orderId)

            logger.info {
                "Received StockReserved event: orderId=$orderId, " +
                    "reservedItems=${event.reservedItems.size}, " +
                    "partition=$partition, offset=$offset"
            }

            // 주문 조회
            val order = loadOrderPort.findById(orderId)
                ?: throw IllegalStateException("Order not found: orderId=$orderId")

            // 주문 상태가 ORDER_CREATED인지 확인 (멱등성)
            if (order.status != OrderStatus.ORDER_CREATED) {
                logger.warn {
                    "Order status is not ORDER_CREATED: orderId=$orderId, " +
                        "status=${order.status}. Skipping."
                }
                acknowledgment.acknowledge()
                return
            }

            // 주문 상태를 STOCK_RESERVED로 업데이트
            val updatedOrder = order.copy(status = OrderStatus.STOCK_RESERVED)
            saveOrderPort.save(updatedOrder)

            logger.info {
                "Order status updated to STOCK_RESERVED: orderId=$orderId"
            }

            // OrderConfirmed 이벤트 발행 (다음 SAGA 단계)
            orderEventPublisher.publishOrderConfirmed(updatedOrder)

            acknowledgment.acknowledge()

            logger.info {
                "Successfully processed StockReserved event: orderId=$orderId"
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to process StockReserved event: orderId=${event.orderId}, " +
                    "partition=$partition, offset=$offset"
            }
            throw e
        }
    }

    /**
     * 재고 예약 실패 이벤트 처리 (보상 트랜잭션)
     *
     * Product Service가 재고 예약에 실패하면 주문을 취소합니다.
     *
     * SAGA Flow: OrderCreated → StockReservationFailed → OrderCancelled
     *
     * @param event StockReservationFailed 이벤트
     */
    @Transactional
    @KafkaListener(
        topics = ["stock.reservation.failed"],
        groupId = "order-service-saga",
        containerFactory = "avroKafkaListenerContainerFactory",
    )
    fun handleStockReservationFailed(
        @Payload event: StockReservationFailed,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val orderId = UUID.fromString(event.orderId)

            logger.warn {
                "Received StockReservationFailed event: orderId=$orderId, " +
                    "reason=${event.failureReason}, " +
                    "failedItems=${event.failedItems.size}, " +
                    "partition=$partition, offset=$offset"
            }

            // 주문 조회
            val order = loadOrderPort.findById(orderId)
                ?: throw IllegalStateException("Order not found: orderId=$orderId")

            // 주문 상태가 ORDER_CREATED인지 확인 (멱등성)
            if (order.status != OrderStatus.ORDER_CREATED) {
                logger.warn {
                    "Order status is not ORDER_CREATED: orderId=$orderId, " +
                        "status=${order.status}. Skipping."
                }
                acknowledgment.acknowledge()
                return
            }

            // 주문 상태를 ORDER_CANCELLED로 업데이트 (보상 트랜잭션)
            val updatedOrder = order.copy(
                status = OrderStatus.ORDER_CANCELLED,
                failureReason = event.failureReason,
            )
            saveOrderPort.save(updatedOrder)

            logger.info {
                "Order cancelled due to stock reservation failure: orderId=$orderId, " +
                    "reason=${event.failureReason}"
            }

            // OrderCancelled 이벤트 발행
            orderEventPublisher.publishOrderCancelled(updatedOrder)

            acknowledgment.acknowledge()

            logger.info {
                "Successfully processed StockReservationFailed event: orderId=$orderId"
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to process StockReservationFailed event: orderId=${event.orderId}, " +
                    "partition=$partition, offset=$offset"
            }
            throw e
        }
    }

    /**
     * 결제 완료 이벤트 처리
     *
     * Payment Service가 결제를 완료하면 주문을 PAYMENT_COMPLETED 상태로 업데이트하고
     * StockConfirmed 이벤트를 발행합니다.
     *
     * SAGA Flow: OrderConfirmed → PaymentCompleted → OrderCompleted (StockConfirmed)
     *
     * @param event PaymentCompleted 이벤트
     */
    @Transactional
    @KafkaListener(
        topics = ["payment.completed"],
        groupId = "order-service-saga",
        containerFactory = "avroKafkaListenerContainerFactory",
    )
    fun handlePaymentCompleted(
        @Payload event: PaymentCompleted,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val orderId = UUID.fromString(event.orderId)
            val paymentId = UUID.fromString(event.paymentId)

            logger.info {
                "Received PaymentCompleted event: orderId=$orderId, " +
                    "paymentId=$paymentId, " +
                    "paymentMethod=${event.paymentMethod}, " +
                    "partition=$partition, offset=$offset"
            }

            // 주문 조회
            val order = loadOrderPort.findById(orderId)
                ?: throw IllegalStateException("Order not found: orderId=$orderId")

            // 주문 상태가 STOCK_RESERVED인지 확인 (멱등성)
            if (order.status != OrderStatus.STOCK_RESERVED) {
                logger.warn {
                    "Order status is not STOCK_RESERVED: orderId=$orderId, " +
                        "status=${order.status}. Skipping."
                }
                acknowledgment.acknowledge()
                return
            }

            // 주문 상태를 PAYMENT_COMPLETED로 업데이트
            val updatedOrder = order.copy(
                status = OrderStatus.PAYMENT_COMPLETED,
                paymentId = paymentId,
            )
            saveOrderPort.save(updatedOrder)

            logger.info {
                "Order status updated to PAYMENT_COMPLETED: orderId=$orderId, paymentId=$paymentId"
            }

            // TODO: StockConfirmed 이벤트 발행 (Product Service로 재고 확정 요청)
            // orderEventPublisher.publishStockConfirmed(updatedOrder)

            acknowledgment.acknowledge()

            logger.info {
                "Successfully processed PaymentCompleted event: orderId=$orderId"
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to process PaymentCompleted event: orderId=${event.orderId}, " +
                    "partition=$partition, offset=$offset"
            }
            throw e
        }
    }

    /**
     * 결제 실패 이벤트 처리 (보상 트랜잭션)
     *
     * Payment Service가 결제에 실패하면 주문을 취소하고
     * OrderCancelled 이벤트를 발행합니다.
     *
     * SAGA Flow: OrderConfirmed → PaymentFailed → OrderCancelled
     *
     * @param event PaymentFailed 이벤트
     */
    @Transactional
    @KafkaListener(
        topics = ["payment.failed"],
        groupId = "order-service-saga",
        containerFactory = "avroKafkaListenerContainerFactory",
    )
    fun handlePaymentFailed(
        @Payload event: PaymentFailed,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val orderId = UUID.fromString(event.orderId)

            logger.warn {
                "Received PaymentFailed event: orderId=$orderId, " +
                    "reason=${event.failureReason}, " +
                    "partition=$partition, offset=$offset"
            }

            // 주문 조회
            val order = loadOrderPort.findById(orderId)
                ?: throw IllegalStateException("Order not found: orderId=$orderId")

            // 주문 상태가 STOCK_RESERVED인지 확인 (멱등성)
            if (order.status != OrderStatus.STOCK_RESERVED) {
                logger.warn {
                    "Order status is not STOCK_RESERVED: orderId=$orderId, " +
                        "status=${order.status}. Skipping."
                }
                acknowledgment.acknowledge()
                return
            }

            // 주문 상태를 ORDER_CANCELLED로 업데이트 (보상 트랜잭션)
            val updatedOrder = order.copy(
                status = OrderStatus.ORDER_CANCELLED,
                failureReason = event.failureReason,
            )
            saveOrderPort.save(updatedOrder)

            logger.info {
                "Order cancelled due to payment failure: orderId=$orderId, " +
                    "reason=${event.failureReason}"
            }

            // OrderCancelled 이벤트 발행 (Product Service가 재고 복원)
            orderEventPublisher.publishOrderCancelled(updatedOrder)

            acknowledgment.acknowledge()

            logger.info {
                "Successfully processed PaymentFailed event: orderId=$orderId"
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to process PaymentFailed event: orderId=${event.orderId}, " +
                    "partition=$partition, offset=$offset"
            }
            throw e
        }
    }
}
