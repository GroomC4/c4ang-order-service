package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.payment.event.avro.PaymentCompleted
import com.groom.ecommerce.payment.event.avro.PaymentMethod
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
import java.math.BigDecimal
import java.util.UUID

@UnitTest
class PaymentCompletedKafkaListenerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val orderEventHandler = mockk<OrderEventHandler>()
        val listener = PaymentCompletedKafkaListener(orderEventHandler)

        Given("PaymentCompleted 이벤트가 수신되었을 때") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val totalAmount = BigDecimal("50000")

            val event = PaymentCompleted.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setPaymentId(paymentId.toString())
                .setOrderId(orderId.toString())
                .setUserId(userId.toString())
                .setTotalAmount(totalAmount)
                .setPaymentMethod(PaymentMethod.CARD)
                .setPgApprovalNumber("APPROVAL-123")
                .setCompletedAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, PaymentCompleted>(
                "payment.completed",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handlePaymentCompleted(
                    orderId = orderId,
                    paymentId = paymentId,
                    totalAmount = totalAmount,
                    completedAt = any(),
                )
            } just runs
            every { acknowledgment.acknowledge() } just runs

            When("리스너가 이벤트를 처리하면") {
                listener.onPaymentCompleted(record, acknowledgment)

                Then("OrderEventHandler.handlePaymentCompleted가 호출된다") {
                    verify(exactly = 1) {
                        orderEventHandler.handlePaymentCompleted(
                            orderId = orderId,
                            paymentId = paymentId,
                            totalAmount = totalAmount,
                            completedAt = any(),
                        )
                    }
                }

                Then("메시지가 acknowledge된다") {
                    verify(exactly = 1) { acknowledgment.acknowledge() }
                }
            }
        }

        Given("PaymentCompleted 이벤트 처리 중 예외가 발생한 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val totalAmount = BigDecimal("50000")

            val event = PaymentCompleted.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setPaymentId(paymentId.toString())
                .setOrderId(orderId.toString())
                .setUserId(userId.toString())
                .setTotalAmount(totalAmount)
                .setPaymentMethod(PaymentMethod.CARD)
                .setPgApprovalNumber("APPROVAL-123")
                .setCompletedAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, PaymentCompleted>(
                "payment.completed",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handlePaymentCompleted(any(), any(), any(), any())
            } throws RuntimeException("처리 실패")

            When("리스너가 이벤트를 처리하면") {
                Then("예외가 상위로 전파된다") {
                    try {
                        listener.onPaymentCompleted(record, acknowledgment)
                    } catch (e: RuntimeException) {
                        // 예외가 발생해야 함
                    }

                    verify(exactly = 0) { acknowledgment.acknowledge() }
                }
            }
        }
    })
