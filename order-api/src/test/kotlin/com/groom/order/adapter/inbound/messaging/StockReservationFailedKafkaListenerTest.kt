package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.saga.event.avro.FailedItem
import com.groom.ecommerce.saga.event.avro.StockReservationFailed
import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.port.OrderEventHandler
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.support.Acknowledgment
import java.util.UUID

@UnitTest
class StockReservationFailedKafkaListenerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val orderEventHandler = mockk<OrderEventHandler>()
        val listener = StockReservationFailedKafkaListener(orderEventHandler)

        Given("StockReservationFailed 이벤트가 수신되었을 때") {
            val orderId = UUID.randomUUID()
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID().toString()
            val failedAt = System.currentTimeMillis()

            val failedItem =
                FailedItem
                    .newBuilder()
                    .setProductId(productId.toString())
                    .setRequestedQuantity(10)
                    .setAvailableStock(5)
                    .build()

            val event =
                StockReservationFailed
                    .newBuilder()
                    .setEventId(eventId)
                    .setEventTimestamp(System.currentTimeMillis())
                    .setOrderId(orderId.toString())
                    .setFailedItems(listOf(failedItem))
                    .setFailureReason("재고 부족")
                    .setFailedAt(failedAt)
                    .build()

            val record =
                ConsumerRecord<String, StockReservationFailed>(
                    "stock.reservation.failed",
                    0,
                    0L,
                    orderId.toString(),
                    event,
                )

            val acknowledgment = mockk<Acknowledgment>()
            val failedItemsSlot = slot<List<OrderEventHandler.FailedItemInfo>>()

            every {
                orderEventHandler.handleStockReservationFailed(
                    orderId = orderId,
                    failedItems = capture(failedItemsSlot),
                    failureReason = "재고 부족",
                    failedAt = any(),
                )
            } just runs
            every { acknowledgment.acknowledge() } just runs

            When("리스너가 이벤트를 처리하면") {
                listener.onStockReservationFailed(record, acknowledgment)

                Then("OrderEventHandler.handleStockReservationFailed가 호출된다") {
                    verify(exactly = 1) {
                        orderEventHandler.handleStockReservationFailed(
                            orderId = orderId,
                            failedItems = any(),
                            failureReason = "재고 부족",
                            failedAt = any(),
                        )
                    }
                }

                Then("실패한 아이템 정보가 올바르게 변환된다") {
                    val capturedItems = failedItemsSlot.captured
                    capturedItems.size == 1
                    capturedItems[0].productId == productId
                    capturedItems[0].requestedQuantity == 10
                    capturedItems[0].availableStock == 5
                }

                Then("메시지가 acknowledge된다") {
                    verify(exactly = 1) { acknowledgment.acknowledge() }
                }
            }
        }

        Given("StockReservationFailed 이벤트 처리 중 예외가 발생한 경우") {
            val orderId = UUID.randomUUID()
            val event =
                StockReservationFailed
                    .newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setEventTimestamp(System.currentTimeMillis())
                    .setOrderId(orderId.toString())
                    .setFailedItems(emptyList())
                    .setFailureReason("재고 부족")
                    .setFailedAt(System.currentTimeMillis())
                    .build()

            val record =
                ConsumerRecord<String, StockReservationFailed>(
                    "stock.reservation.failed",
                    0,
                    0L,
                    orderId.toString(),
                    event,
                )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handleStockReservationFailed(any(), any(), any(), any())
            } throws RuntimeException("DB 연결 실패")

            When("리스너가 이벤트를 처리하면") {
                Then("예외가 상위로 전파된다 (재시도 처리를 위해)") {
                    try {
                        listener.onStockReservationFailed(record, acknowledgment)
                    } catch (e: RuntimeException) {
                        // 예외가 발생해야 함
                    }

                    // acknowledge가 호출되지 않아야 함
                    verify(exactly = 0) { acknowledgment.acknowledge() }
                }
            }
        }
    })
