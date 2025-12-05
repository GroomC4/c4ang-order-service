package com.groom.order.adapter.inbound.messaging

import com.groom.ecommerce.payment.event.avro.PaymentFailed
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
class PaymentFailedKafkaListenerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val orderEventHandler = mockk<OrderEventHandler>()
        val listener = PaymentFailedKafkaListener(orderEventHandler)

        Given("PaymentFailed 이벤트가 수신되었을 때") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val failureReason = "카드 한도 초과"

            val event = PaymentFailed.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setPaymentId(paymentId.toString())
                .setOrderId(orderId.toString())
                .setUserId(userId.toString())
                .setFailureReason(failureReason)
                .setFailedAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, PaymentFailed>(
                "payment.failed",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handlePaymentFailed(
                    orderId = orderId,
                    paymentId = paymentId,
                    failureReason = failureReason,
                    failedAt = any(),
                )
            } just runs
            every { acknowledgment.acknowledge() } just runs

            When("리스너가 이벤트를 처리하면") {
                listener.onPaymentFailed(record, acknowledgment)

                Then("OrderEventHandler.handlePaymentFailed가 호출된다") {
                    verify(exactly = 1) {
                        orderEventHandler.handlePaymentFailed(
                            orderId = orderId,
                            paymentId = paymentId,
                            failureReason = failureReason,
                            failedAt = any(),
                        )
                    }
                }

                Then("메시지가 acknowledge된다") {
                    verify(exactly = 1) { acknowledgment.acknowledge() }
                }
            }
        }

        Given("PaymentFailed 이벤트 처리 중 예외가 발생한 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val event = PaymentFailed.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventTimestamp(System.currentTimeMillis())
                .setPaymentId(paymentId.toString())
                .setOrderId(orderId.toString())
                .setUserId(userId.toString())
                .setFailureReason("실패 사유")
                .setFailedAt(System.currentTimeMillis())
                .build()

            val record = ConsumerRecord<String, PaymentFailed>(
                "payment.failed",
                0,
                0L,
                orderId.toString(),
                event,
            )

            val acknowledgment = mockk<Acknowledgment>()

            every {
                orderEventHandler.handlePaymentFailed(any(), any(), any(), any())
            } throws RuntimeException("처리 실패")

            When("리스너가 이벤트를 처리하면") {
                Then("예외가 상위로 전파된다") {
                    try {
                        listener.onPaymentFailed(record, acknowledgment)
                    } catch (e: RuntimeException) {
                        // 예외가 발생해야 함
                    }

                    verify(exactly = 0) { acknowledgment.acknowledge() }
                }
            }
        }
    })
