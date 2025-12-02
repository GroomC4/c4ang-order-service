package com.groom.order.application.event

import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.event.OrderCancelledEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.service.OrderAuditRecorder
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class OrderCancelledEventHandlerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("주문 취소 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = OrderCancelledEventHandler(orderAuditRecorder)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val cancelReason = "고객 변심"
            val cancelledAt = LocalDateTime.now()

            val event = OrderCancelledEvent(
                orderId = orderId,
                orderNumber = "ORD-20251202-ABC123",
                userExternalId = userId,
                storeId = storeId,
                cancelReason = cancelReason,
                cancelledAt = cancelledAt,
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
                handler.handleOrderCancelled(event)

                Then("취소 사유와 함께 감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = OrderAuditEventType.ORDER_CANCELLED,
                            changeSummary = match { it.contains(cancelReason) },
                            actorUserId = userId,
                            metadata = any(),
                        )
                    }
                }
            }
        }

        Given("취소 사유 없이 주문 취소 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = OrderCancelledEventHandler(orderAuditRecorder)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val event = OrderCancelledEvent(
                orderId = orderId,
                orderNumber = "ORD-20251202-XYZ789",
                userExternalId = userId,
                storeId = storeId,
                cancelReason = null,
                cancelledAt = LocalDateTime.now(),
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
                handler.handleOrderCancelled(event)

                Then("사유 없음으로 감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = OrderAuditEventType.ORDER_CANCELLED,
                            changeSummary = match { it.contains("사유 없음") },
                            actorUserId = userId,
                            metadata = any(),
                        )
                    }
                }
            }
        }
    })
