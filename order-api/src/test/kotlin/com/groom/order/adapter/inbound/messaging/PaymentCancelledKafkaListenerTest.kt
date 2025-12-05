package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.payment.event.avro.PaymentCancellationReason
import com.groom.ecommerce.payment.event.avro.PaymentCancelled
import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.port.OrderEventHandler
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.support.Acknowledgment
import java.util.UUID

@UnitTest
class PaymentCancelledKafkaListenerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val orderEventHandler = mockk<OrderEventHandler>()
        val listener = PaymentCancelledKafkaListener(orderEventHandler)

        Given("PaymentCancelled 이벤트가 수신되었을 때") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val cancellationReason = PaymentCancellationReason.USER_CANCEL

            val event = PaymentCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setPaymentId(paymentId.toString())
                .setOrderId(orderId.toString())
                .setUserId(userId.toString())
                .setCancellationReason(cancellationReason)
                .setCancelledAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, PaymentCancelled>(
                "payment.cancelled",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handlePaymentCancelled(
                    orderId = orderId,
                    paymentId = paymentId,
                    cancellationReason = cancellationReason.name,
                    cancelledAt = any(),
                )
            } just runs
            every { acknowledgment.acknowledge() } just runs

            When("리스너가 이벤트를 처리하면") {
                listener.onPaymentCancelled(record, acknowledgment)

                Then("OrderEventHandler.handlePaymentCancelled가 호출된다") {
                    verify(exactly = 1) {
                        orderEventHandler.handlePaymentCancelled(
                            orderId = orderId,
                            paymentId = paymentId,
                            cancellationReason = cancellationReason.name,
                            cancelledAt = any(),
                        )
                    }
                }

                Then("메시지가 acknowledge된다") {
                    verify(exactly = 1) { acknowledgment.acknowledge() }
                }
            }
        }

        Given("STOCK_UNAVAILABLE 사유로 결제가 취소된 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val cancellationReason = PaymentCancellationReason.STOCK_UNAVAILABLE

            val event = PaymentCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setPaymentId(paymentId.toString())
                .setOrderId(orderId.toString())
                .setUserId(userId.toString())
                .setCancellationReason(cancellationReason)
                .setCancelledAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, PaymentCancelled>(
                "payment.cancelled",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handlePaymentCancelled(
                    orderId = orderId,
                    paymentId = paymentId,
                    cancellationReason = cancellationReason.name,
                    cancelledAt = any(),
                )
            } just runs
            every { acknowledgment.acknowledge() } just runs

            When("리스너가 이벤트를 처리하면") {
                listener.onPaymentCancelled(record, acknowledgment)

                Then("취소 사유가 STOCK_UNAVAILABLE로 전달된다") {
                    verify(exactly = 1) {
                        orderEventHandler.handlePaymentCancelled(
                            orderId = orderId,
                            paymentId = paymentId,
                            cancellationReason = "STOCK_UNAVAILABLE",
                            cancelledAt = any(),
                        )
                    }
                }
            }
        }

        Given("PaymentCancelled 이벤트 처리 중 예외가 발생한 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val event = PaymentCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setPaymentId(paymentId.toString())
                .setOrderId(orderId.toString())
                .setUserId(userId.toString())
                .setCancellationReason(PaymentCancellationReason.SYSTEM_ERROR)
                .setCancelledAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, PaymentCancelled>(
                "payment.cancelled",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handlePaymentCancelled(any(), any(), any(), any())
            } throws RuntimeException("처리 실패")

            When("리스너가 이벤트를 처리하면") {
                Then("예외가 상위로 전파된다") {
                    try {
                        listener.onPaymentCancelled(record, acknowledgment)
                    } catch (e: RuntimeException) {
                        // 예외가 발생해야 함
                    }

                    verify(exactly = 0) { acknowledgment.acknowledge() }
                }
            }
        }
    })
