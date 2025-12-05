package com.groom.order.application.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventHandler
import com.groom.order.domain.port.OrderEventPublisher
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.domain.service.OrderAuditRecorder
import com.groom.order.fixture.OrderTestFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class OrderEventHandlerServiceTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val loadOrderPort = mockk<LoadOrderPort>()
        val saveOrderPort = mockk<SaveOrderPort>()
        val orderEventPublisher = mockk<OrderEventPublisher>()
        val orderAuditRecorder = mockk<OrderAuditRecorder>()

        val service = OrderEventHandlerService(
            loadOrderPort,
            saveOrderPort,
            orderEventPublisher,
            orderAuditRecorder,
        )

        Given("PENDING 상태의 주문이 있을 때") {
            val orderId = UUID.randomUUID()
            val order = OrderTestFixture.createOrder(
                id = orderId,
                status = OrderStatus.PENDING,
                reservationId = "RES-123",
            )

            val reservedItems = listOf(
                OrderEventHandler.ReservedItemInfo(
                    productId = UUID.randomUUID(),
                    quantity = 2,
                    reservedStock = 98,
                ),
            )
            val reservedAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(any()) } answers { firstArg() }
            every { orderAuditRecorder.record(any(), any(), any(), any(), any()) } just runs
            every { orderEventPublisher.publishOrderConfirmed(any()) } just runs

            When("StockReserved 이벤트를 처리하면") {
                service.handleStockReserved(orderId, reservedItems, reservedAt)

                Then("주문 상태가 STOCK_RESERVED로 변경된다") {
                    order.status shouldBe OrderStatus.STOCK_RESERVED
                }

                Then("주문이 저장된다") {
                    verify(exactly = 1) { saveOrderPort.save(order) }
                }

                Then("OrderConfirmed 이벤트가 발행된다") {
                    verify(exactly = 1) { orderEventPublisher.publishOrderConfirmed(order) }
                }

                Then("감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = any(),
                            changeSummary = any(),
                            actorUserId = null,
                            metadata = any(),
                        )
                    }
                }
            }
        }

        Given("PAYMENT_PENDING 상태의 주문이 있을 때") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val order = OrderTestFixture.createOrder(
                id = orderId,
                status = OrderStatus.PAYMENT_PENDING,
                paymentId = paymentId,
            )

            val totalAmount = BigDecimal("50000")
            val completedAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(any()) } answers { firstArg() }
            every { orderAuditRecorder.record(any(), any(), any(), any(), any()) } just runs

            When("PaymentCompleted 이벤트를 처리하면") {
                service.handlePaymentCompleted(orderId, paymentId, totalAmount, completedAt)

                Then("주문 상태가 PREPARING으로 변경된다") {
                    order.status shouldBe OrderStatus.PREPARING
                }

                Then("주문이 저장된다") {
                    verify(exactly = 1) { saveOrderPort.save(order) }
                }

                Then("감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = any(),
                            changeSummary = any(),
                            actorUserId = null,
                            metadata = any(),
                        )
                    }
                }
            }
        }

        Given("PAYMENT_PENDING 상태의 주문에서 결제가 실패하는 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val order = OrderTestFixture.createOrder(
                id = orderId,
                status = OrderStatus.PAYMENT_PENDING,
                reservationId = "RES-456",
            )

            val failureReason = "카드 한도 초과"
            val failedAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(any()) } answers { firstArg() }
            every { orderAuditRecorder.record(any(), any(), any(), any(), any()) } just runs
            every { orderEventPublisher.publishOrderCancelled(any(), any()) } just runs

            When("PaymentFailed 이벤트를 처리하면") {
                service.handlePaymentFailed(orderId, paymentId, failureReason, failedAt)

                Then("주문 상태가 ORDER_CANCELLED로 변경된다") {
                    order.status shouldBe OrderStatus.ORDER_CANCELLED
                }

                Then("실패 사유가 기록된다") {
                    order.failureReason shouldBe "결제 실패: $failureReason"
                }

                Then("주문이 저장된다") {
                    verify(exactly = 1) { saveOrderPort.save(order) }
                }

                Then("OrderCancelled 이벤트가 발행된다 (재고 복원용)") {
                    verify(exactly = 1) {
                        orderEventPublisher.publishOrderCancelled(
                            order,
                            "PAYMENT_FAILED: $failureReason",
                        )
                    }
                }
            }
        }

        Given("PAYMENT_PENDING 상태의 주문에서 결제가 취소되는 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val order = OrderTestFixture.createOrder(
                id = orderId,
                status = OrderStatus.PAYMENT_PENDING,
                reservationId = "RES-789",
            )

            val cancellationReason = "USER_CANCEL"
            val cancelledAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(any()) } answers { firstArg() }
            every { orderAuditRecorder.record(any(), any(), any(), any(), any()) } just runs
            every { orderEventPublisher.publishOrderCancelled(any(), any()) } just runs

            When("PaymentCancelled 이벤트를 처리하면") {
                service.handlePaymentCancelled(orderId, paymentId, cancellationReason, cancelledAt)

                Then("주문 상태가 ORDER_CANCELLED로 변경된다") {
                    order.status shouldBe OrderStatus.ORDER_CANCELLED
                }

                Then("취소 사유가 기록된다") {
                    order.failureReason shouldBe "결제 취소: $cancellationReason"
                }

                Then("주문이 저장된다") {
                    verify(exactly = 1) { saveOrderPort.save(order) }
                }

                Then("OrderCancelled 이벤트가 발행된다 (재고 복원용)") {
                    verify(exactly = 1) {
                        orderEventPublisher.publishOrderCancelled(
                            order,
                            "PAYMENT_CANCELLED: $cancellationReason",
                        )
                    }
                }
            }
        }

        Given("존재하지 않는 주문에 대한 이벤트가 들어온 경우") {
            val nonExistentOrderId = UUID.randomUUID()

            every { loadOrderPort.loadById(nonExistentOrderId) } returns null

            When("StockReserved 이벤트를 처리하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.handleStockReserved(
                            nonExistentOrderId,
                            emptyList(),
                            LocalDateTime.now(),
                        )
                    }
                }
            }

            When("PaymentCompleted 이벤트를 처리하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.handlePaymentCompleted(
                            nonExistentOrderId,
                            UUID.randomUUID(),
                            BigDecimal("10000"),
                            LocalDateTime.now(),
                        )
                    }
                }
            }

            When("PaymentFailed 이벤트를 처리하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.handlePaymentFailed(
                            nonExistentOrderId,
                            UUID.randomUUID(),
                            "실패 사유",
                            LocalDateTime.now(),
                        )
                    }
                }
            }

            When("PaymentCancelled 이벤트를 처리하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.handlePaymentCancelled(
                            nonExistentOrderId,
                            UUID.randomUUID(),
                            "취소 사유",
                            LocalDateTime.now(),
                        )
                    }
                }
            }
        }
    })
