package com.groom.order.application.event

import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.event.OrderRefundedEvent
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
class OrderRefundedEventHandlerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("주문 환불 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = OrderRefundedEventHandler(orderAuditRecorder)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val refundId = "REFUND-12345"
            val refundAmount = 50000L
            val refundReason = "상품 불량"

            val event = OrderRefundedEvent(
                orderId = orderId,
                orderNumber = "ORD-20251202-ABC123",
                userExternalId = userId,
                refundAmount = refundAmount,
                refundId = refundId,
                refundReason = refundReason,
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
                handler.handleOrderRefunded(event)

                Then("환불 정보와 함께 감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = OrderAuditEventType.ORDER_REFUNDED,
                            changeSummary = match {
                                it.contains(refundId) &&
                                    it.contains("$refundAmount") &&
                                    it.contains(refundReason)
                            },
                            actorUserId = userId,
                            metadata = any(),
                        )
                    }
                }
            }
        }

        Given("환불 사유 없이 주문 환불 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = OrderRefundedEventHandler(orderAuditRecorder)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val refundId = "REFUND-67890"
            val refundAmount = 30000L

            val event = OrderRefundedEvent(
                orderId = orderId,
                orderNumber = "ORD-20251202-XYZ789",
                userExternalId = userId,
                refundAmount = refundAmount,
                refundId = refundId,
                refundReason = null,
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
                handler.handleOrderRefunded(event)

                Then("사유 없음으로 감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = OrderAuditEventType.ORDER_REFUNDED,
                            changeSummary = match { it.contains("사유 없음") },
                            actorUserId = userId,
                            metadata = any(),
                        )
                    }
                }
            }
        }
    })
