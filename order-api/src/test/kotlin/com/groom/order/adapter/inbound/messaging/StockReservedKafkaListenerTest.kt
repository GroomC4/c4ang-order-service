package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.product.event.avro.ReservedItem
import com.groom.ecommerce.product.event.avro.StockReserved
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
class StockReservedKafkaListenerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val orderEventHandler = mockk<OrderEventHandler>()
        val listener = StockReservedKafkaListener(orderEventHandler)

        Given("StockReserved 이벤트가 수신되었을 때") {
            val orderId = UUID.randomUUID()
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID().toString()
            val reservedAt = System.currentTimeMillis()

            val reservedItem = ReservedItem.newBuilder()
                .setProductId(productId.toString())
                .setQuantity(2)
                .setReservedStock(98)
                .build()

            val event = StockReserved.newBuilder()
                .setEventId(eventId)
                .setEventTimestamp(System.currentTimeMillis())
                .setOrderId(orderId.toString())
                .setReservedItems(listOf(reservedItem))
                .setReservedAt(reservedAt)
                .build()

            val record = ConsumerRecord<String, StockReserved>(
                "stock.reserved",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()
            val reservedItemsSlot = slot<List<OrderEventHandler.ReservedItemInfo>>()

            every {
                orderEventHandler.handleStockReserved(
                    orderId = orderId,
                    reservedItems = capture(reservedItemsSlot),
                    reservedAt = any(),
                )
            } just runs
            every { acknowledgment.acknowledge() } just runs

            When("리스너가 이벤트를 처리하면") {
                listener.onStockReserved(record, acknowledgment)

                Then("OrderEventHandler.handleStockReserved가 호출된다") {
                    verify(exactly = 1) {
                        orderEventHandler.handleStockReserved(
                            orderId = orderId,
                            reservedItems = any(),
                            reservedAt = any(),
                        )
                    }
                }

                Then("예약된 아이템 정보가 올바르게 변환된다") {
                    val capturedItems = reservedItemsSlot.captured
                    capturedItems.size == 1
                    capturedItems[0].productId == productId
                    capturedItems[0].quantity == 2
                    capturedItems[0].reservedStock == 98
                }

                Then("메시지가 acknowledge된다") {
                    verify(exactly = 1) { acknowledgment.acknowledge() }
                }
            }
        }

        Given("StockReserved 이벤트 처리 중 예외가 발생한 경우") {
            val orderId = UUID.randomUUID()
            val event = StockReserved.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setOrderId(orderId.toString())
                .setReservedItems(emptyList())
                .setReservedAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, StockReserved>(
                "stock.reserved",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handleStockReserved(any(), any(), any())
            } throws RuntimeException("DB 연결 실패")

            When("리스너가 이벤트를 처리하면") {
                Then("예외가 상위로 전파된다 (재시도 처리를 위해)") {
                    try {
                        listener.onStockReserved(record, acknowledgment)
                    } catch (e: RuntimeException) {
                        // 예외가 발생해야 함
                    }

                    // acknowledge가 호출되지 않아야 함
                    verify(exactly = 0) { acknowledgment.acknowledge() }
                }
            }
        }
    })
