package com.groom.order.common.kafka

import com.groom.ecommerce.payment.event.avro.PaymentCompleted
import com.groom.ecommerce.payment.event.avro.PaymentFailed
import com.groom.ecommerce.product.event.avro.ReservedItem
import com.groom.ecommerce.product.event.avro.StockReserved
import com.groom.ecommerce.saga.event.avro.FailedItem
import com.groom.ecommerce.saga.event.avro.StockReservationFailed
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Kafka 통합 테스트 헬퍼
 *
 * 외부 서비스(Product, Payment)에서 발행하는 이벤트를 시뮬레이션합니다.
 * 통합 테스트에서 Kafka Consumer 로직을 검증할 때 사용합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @Test
 * fun `stock.reserved 이벤트 수신 시 주문이 ORDER_CONFIRMED로 변경된다`() {
 *     // given: PENDING 상태의 주문 생성
 *     val order = createPendingOrder()
 *
 *     // when: Product Service의 StockReserved 이벤트 시뮬레이션
 *     kafkaTestHelper.publishStockReserved(
 *         orderId = order.id,
 *         items = listOf(
 *             KafkaTestHelper.ReservedItemData(productId, quantity = 2, reservedStock = 98)
 *         )
 *     )
 *
 *     // then: 주문 상태 변경 확인
 *     await().atMost(5.seconds).untilAsserted {
 *         val updated = loadOrderPort.loadById(order.id)
 *         assertThat(updated.status).isEqualTo(OrderStatus.ORDER_CONFIRMED)
 *     }
 * }
 * ```
 */
@Component
class KafkaTestHelper(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    companion object {
        const val TOPIC_STOCK_RESERVED = "stock.reserved"
        const val TOPIC_STOCK_RESERVATION_FAILED = "stock.reservation.failed"
        const val TOPIC_PAYMENT_COMPLETED = "payment.completed"
        const val TOPIC_PAYMENT_FAILED = "payment.failed"
    }

    /**
     * Product Service의 StockReserved 이벤트 발행 시뮬레이션
     */
    fun publishStockReserved(
        orderId: UUID,
        items: List<ReservedItemData>,
        reservedAt: Instant = Instant.now(),
    ) {
        val event =
            StockReserved.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId.toString())
                .setReservedItems(
                    items.map { item ->
                        ReservedItem.newBuilder()
                            .setProductId(item.productId.toString())
                            .setQuantity(item.quantity)
                            .setReservedStock(item.reservedStock)
                            .build()
                    },
                )
                .setReservedAt(reservedAt.toEpochMilli())
                .build()

        kafkaTemplate.send(TOPIC_STOCK_RESERVED, orderId.toString(), event).get()
    }

    /**
     * Product Service의 StockReservationFailed 이벤트 발행 시뮬레이션
     */
    fun publishStockReservationFailed(
        orderId: UUID,
        failedItems: List<FailedItemData>,
        reason: String = "재고 부족",
        failedAt: Instant = Instant.now(),
    ) {
        val event =
            StockReservationFailed.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId.toString())
                .setFailedItems(
                    failedItems.map { item ->
                        FailedItem.newBuilder()
                            .setProductId(item.productId.toString())
                            .setRequestedQuantity(item.requestedQuantity)
                            .setAvailableStock(item.availableStock)
                            .build()
                    },
                )
                .setFailureReason(reason)
                .setFailedAt(failedAt.toEpochMilli())
                .build()

        kafkaTemplate.send(TOPIC_STOCK_RESERVATION_FAILED, orderId.toString(), event).get()
    }

    /**
     * Payment Service의 PaymentCompleted 이벤트 발행 시뮬레이션
     */
    fun publishPaymentCompleted(
        orderId: UUID,
        paymentId: UUID = UUID.randomUUID(),
        totalAmount: BigDecimal,
        completedAt: Instant = Instant.now(),
    ) {
        val event =
            PaymentCompleted.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId.toString())
                .setPaymentId(paymentId.toString())
                .setTotalAmount(totalAmount)
                .setCompletedAt(completedAt.toEpochMilli())
                .build()

        kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, orderId.toString(), event).get()
    }

    /**
     * Payment Service의 PaymentFailed 이벤트 발행 시뮬레이션
     */
    fun publishPaymentFailed(
        orderId: UUID,
        paymentId: UUID = UUID.randomUUID(),
        reason: String = "결제 실패",
        failedAt: Instant = Instant.now(),
    ) {
        val event =
            PaymentFailed.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId.toString())
                .setPaymentId(paymentId.toString())
                .setFailureReason(reason)
                .setFailedAt(failedAt.toEpochMilli())
                .build()

        kafkaTemplate.send(TOPIC_PAYMENT_FAILED, orderId.toString(), event).get()
    }

    /**
     * 재고 예약 아이템 데이터
     */
    data class ReservedItemData(
        val productId: UUID,
        val quantity: Int,
        val reservedStock: Int,
    )

    /**
     * 재고 예약 실패 아이템 데이터
     */
    data class FailedItemData(
        val productId: UUID,
        val requestedQuantity: Int,
        val availableStock: Int,
    )
}
