package com.groom.order.application.event

import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.event.PaymentCompletedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID

@UnitTest
class PaymentCompletedEventHandlerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("결제 완료 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = PaymentCompletedEventHandler(orderAuditRecorder)

            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val paymentAmount = 50000L

            val event = PaymentCompletedEvent(
                orderId = orderId,
                orderNumber = "ORD-20251202-ABC123",
                paymentId = paymentId,
                paymentAmount = paymentAmount,
            )

            every {
                orderAuditRecorder.record(
                    orderId = any(),
                    eventType = any(),
                    changeSummary = any(),
                    actorUserId = any(),
                    metadata = any(),
                )
            } just runs

            When("이벤트를 처리하면") {
                handler.handlePaymentCompleted(event)

                Then("결제 완료 감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = OrderAuditEventType.PAYMENT_COMPLETED,
                            changeSummary = match { it.contains(paymentId.toString()) && it.contains("$paymentAmount") },
                            actorUserId = null,
                            metadata = any(),
                        )
                    }
                }
            }
        }

        Given("다양한 금액의 결제 완료 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = PaymentCompletedEventHandler(orderAuditRecorder)

            every {
                orderAuditRecorder.record(
                    orderId = any(),
                    eventType = any(),
                    changeSummary = any(),
                    actorUserId = any(),
                    metadata = any(),
                )
            } just runs

            When("소액 결제가 완료되면") {
                val event = PaymentCompletedEvent(
                    orderId = UUID.randomUUID(),
                    orderNumber = "ORD-20251202-SMALL",
                    paymentId = UUID.randomUUID(),
                    paymentAmount = 1000L,
                )
                handler.handlePaymentCompleted(event)

                Then("감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = event.orderId,
                            eventType = OrderAuditEventType.PAYMENT_COMPLETED,
                            changeSummary = any(),
                            actorUserId = null,
                            metadata = any(),
                        )
                    }
                }
            }

            When("대액 결제가 완료되면") {
                val event = PaymentCompletedEvent(
                    orderId = UUID.randomUUID(),
                    orderNumber = "ORD-20251202-LARGE",
                    paymentId = UUID.randomUUID(),
                    paymentAmount = 10000000L,
                )
                handler.handlePaymentCompleted(event)

                Then("감사 로그가 기록된다") {
                    verify {
                        orderAuditRecorder.record(
                            orderId = event.orderId,
                            eventType = OrderAuditEventType.PAYMENT_COMPLETED,
                            changeSummary = any(),
                            actorUserId = null,
                            metadata = any(),
                        )
                    }
                }
            }
        }
    })
