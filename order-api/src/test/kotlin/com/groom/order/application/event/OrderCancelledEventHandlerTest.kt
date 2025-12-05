package com.groom.order.application.event

import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.event.OrderCancelledEvent
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventPublisher
import com.groom.order.domain.service.OrderAuditRecorder
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class OrderCancelledEventHandlerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("주문 취소 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val loadOrderPort = mockk<LoadOrderPort>()
            val orderEventPublisher = mockk<OrderEventPublisher>(relaxed = true)
            val handler = OrderCancelledEventHandler(orderAuditRecorder, loadOrderPort, orderEventPublisher)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val cancelReason = "고객 변심"
            val cancelledAt = LocalDateTime.now()

            val event =
                OrderCancelledEvent(
                    orderId = orderId,
                    orderNumber = "ORD-20251202-ABC123",
                    userExternalId = userId,
                    storeId = storeId,
                    cancelReason = cancelReason,
                    cancelledAt = cancelledAt,
                )

            val order =
                Order(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-20251202-ABC123",
                    status = OrderStatus.ORDER_CANCELLED,
                    paymentSummary = mapOf("method" to "CARD"),
                    timeline = emptyList(),
                    cancelledAt = cancelledAt,
                ).apply {
                    addItem(OrderItem(UUID.randomUUID(), "테스트 상품", 1, BigDecimal("10000")))
                }

            every {
                orderAuditRecorder.record(
                    orderId = any(),
                    eventType = any(),
                    changeSummary = any(),
                    actorUserId = any(),
                    metadata = any(),
                )
            } just runs

            every { loadOrderPort.loadById(orderId) } returns order

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

                Then("Kafka로 order.cancelled 이벤트가 발행된다") {
                    verify(exactly = 1) {
                        orderEventPublisher.publishOrderCancelled(order, cancelReason)
                    }
                }
            }
        }

        Given("취소 사유 없이 주문 취소 이벤트가 발생했을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val loadOrderPort = mockk<LoadOrderPort>()
            val orderEventPublisher = mockk<OrderEventPublisher>(relaxed = true)
            val handler = OrderCancelledEventHandler(orderAuditRecorder, loadOrderPort, orderEventPublisher)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val event =
                OrderCancelledEvent(
                    orderId = orderId,
                    orderNumber = "ORD-20251202-XYZ789",
                    userExternalId = userId,
                    storeId = storeId,
                    cancelReason = null,
                    cancelledAt = LocalDateTime.now(),
                )

            val order =
                Order(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-20251202-XYZ789",
                    status = OrderStatus.ORDER_CANCELLED,
                    paymentSummary = mapOf("method" to "CARD"),
                    timeline = emptyList(),
                ).apply {
                    addItem(OrderItem(UUID.randomUUID(), "테스트 상품", 1, BigDecimal("10000")))
                }

            every {
                orderAuditRecorder.record(
                    orderId = any(),
                    eventType = any(),
                    changeSummary = any(),
                    actorUserId = any(),
                    metadata = any(),
                )
            } just runs

            every { loadOrderPort.loadById(orderId) } returns order

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

                Then("Kafka로 order.cancelled 이벤트가 발행된다") {
                    verify(exactly = 1) {
                        orderEventPublisher.publishOrderCancelled(order, null)
                    }
                }
            }
        }

        Given("주문이 존재하지 않을 때") {
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val loadOrderPort = mockk<LoadOrderPort>()
            val orderEventPublisher = mockk<OrderEventPublisher>(relaxed = true)
            val handler = OrderCancelledEventHandler(orderAuditRecorder, loadOrderPort, orderEventPublisher)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val event =
                OrderCancelledEvent(
                    orderId = orderId,
                    orderNumber = "ORD-NOTFOUND",
                    userExternalId = userId,
                    storeId = storeId,
                    cancelReason = "테스트",
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

            every { loadOrderPort.loadById(orderId) } returns null

            When("이벤트를 처리하면") {
                handler.handleOrderCancelled(event)

                Then("감사 로그는 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = OrderAuditEventType.ORDER_CANCELLED,
                            changeSummary = any(),
                            actorUserId = userId,
                            metadata = any(),
                        )
                    }
                }

                Then("Kafka 이벤트는 발행되지 않는다") {
                    verify(exactly = 0) {
                        orderEventPublisher.publishOrderCancelled(any(), any())
                    }
                }
            }
        }
    })
