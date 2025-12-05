package com.groom.order.application.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderAuditEventType
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
import io.mockk.justRun
import io.mockk.mockk
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

        val service =
            OrderEventHandlerService(
                loadOrderPort,
                saveOrderPort,
                orderEventPublisher,
                orderAuditRecorder,
            )

        Given("PENDING 상태의 주문이 있을 때") {
            val orderId = UUID.randomUUID()
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.ORDER_CREATED,
                    reservationId = "RES-123",
                )

            val reservedItems =
                listOf(
                    OrderEventHandler.ReservedItemInfo(
                        productId = UUID.randomUUID(),
                        quantity = 2,
                        reservedStock = 98,
                    ),
                )
            val reservedAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(any()) } answers { firstArg() }
            justRun { orderAuditRecorder.record(any(), any(), any(), any(), any()) }
            justRun { orderEventPublisher.publishOrderConfirmed(any()) }

            When("StockReserved 이벤트를 처리하면") {
                service.handleStockReserved(orderId, reservedItems, reservedAt)

                Then("주문 상태가 ORDER_CONFIRMED로 변경된다") {
                    order.status shouldBe OrderStatus.ORDER_CONFIRMED
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
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.PAYMENT_PENDING,
                    paymentId = paymentId,
                )

            val totalAmount = BigDecimal("50000")
            val completedAt = LocalDateTime.now()

            // 예상되는 confirmedItems 생성
            val expectedConfirmedItems =
                order.items.map { item ->
                    OrderEventPublisher.ConfirmedItemInfo(
                        productId = item.productId,
                        quantity = item.quantity,
                    )
                }

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(order) } returns order
            justRun {
                orderAuditRecorder.record(
                    orderId = orderId,
                    eventType = OrderAuditEventType.PAYMENT_COMPLETED,
                    changeSummary = "결제 완료 및 주문 확정 (Kafka 이벤트)",
                    actorUserId = null,
                    metadata = any(),
                )
            }
            justRun {
                orderEventPublisher.publishStockConfirmed(
                    orderId = orderId,
                    paymentId = paymentId,
                    confirmedItems = expectedConfirmedItems,
                    confirmedAt = completedAt,
                )
            }

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
                            eventType = OrderAuditEventType.PAYMENT_COMPLETED,
                            changeSummary = "결제 완료 및 주문 확정 (Kafka 이벤트)",
                            actorUserId = null,
                            metadata = any(),
                        )
                    }
                }

                Then("StockConfirmed 이벤트가 올바른 파라미터로 발행된다") {
                    verify(exactly = 1) {
                        orderEventPublisher.publishStockConfirmed(
                            orderId = orderId,
                            paymentId = paymentId,
                            confirmedItems = expectedConfirmedItems,
                            confirmedAt = completedAt,
                        )
                    }
                }
            }
        }

        Given("PAYMENT_PENDING 상태의 주문에서 결제가 실패하는 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.PAYMENT_PENDING,
                    reservationId = "RES-456",
                )

            val failureReason = "카드 한도 초과"
            val failedAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(order) } returns order
            justRun {
                orderAuditRecorder.record(
                    orderId = orderId,
                    eventType = OrderAuditEventType.ORDER_CANCELLED,
                    changeSummary = "결제 실패로 인한 주문 취소",
                    actorUserId = null,
                    metadata = any(),
                )
            }
            justRun {
                orderEventPublisher.publishOrderCancelled(
                    order,
                    "PAYMENT_FAILED: $failureReason",
                )
            }

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
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.PAYMENT_PENDING,
                    reservationId = "RES-789",
                )

            val cancellationReason = "USER_CANCEL"
            val cancelledAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(order) } returns order
            justRun {
                orderAuditRecorder.record(
                    orderId = orderId,
                    eventType = OrderAuditEventType.ORDER_CANCELLED,
                    changeSummary = "결제 취소로 인한 주문 취소",
                    actorUserId = null,
                    metadata = any(),
                )
            }
            justRun {
                orderEventPublisher.publishOrderCancelled(
                    order,
                    "PAYMENT_CANCELLED: $cancellationReason",
                )
            }

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

        // ===== handleStockReservationFailed 테스트 =====
        Given("ORDER_CREATED 상태의 주문에서 재고 예약이 실패하는 경우") {
            val orderId = UUID.randomUUID()
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.ORDER_CREATED,
                )

            val failedItems =
                listOf(
                    OrderEventHandler.FailedItemInfo(
                        productId = UUID.randomUUID(),
                        requestedQuantity = 10,
                        availableStock = 5,
                    ),
                )
            val failureReason = "재고 부족"
            val failedAt = LocalDateTime.now()

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(any()) } answers { firstArg() }
            justRun { orderAuditRecorder.record(any(), any(), any(), any(), any()) }

            When("StockReservationFailed 이벤트를 처리하면") {
                service.handleStockReservationFailed(orderId, failedItems, failureReason, failedAt)

                Then("주문 상태가 ORDER_CANCELLED로 변경된다") {
                    order.status shouldBe OrderStatus.ORDER_CANCELLED
                }

                Then("실패 사유가 기록된다") {
                    order.failureReason shouldBe "재고 예약 실패: $failureReason"
                }

                Then("취소 시각이 기록된다") {
                    order.cancelledAt shouldBe failedAt
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

                Then("OrderCancelled 이벤트는 발행되지 않는다 (재고가 예약되지 않았으므로)") {
                    verify(exactly = 0) { orderEventPublisher.publishOrderCancelled(any(), any()) }
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

            When("StockReservationFailed 이벤트를 처리하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.handleStockReservationFailed(
                            nonExistentOrderId,
                            emptyList(),
                            "재고 부족",
                            LocalDateTime.now(),
                        )
                    }
                }
            }
        }

        // ===== 멱등성 테스트 (이미 처리된 이벤트 재수신) =====
        Given("이미 ORDER_CONFIRMED 상태인 주문이 있을 때") {
            val orderId = UUID.randomUUID()
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.ORDER_CONFIRMED,
                )

            val reservedItems =
                listOf(
                    OrderEventHandler.ReservedItemInfo(
                        productId = UUID.randomUUID(),
                        quantity = 2,
                        reservedStock = 98,
                    ),
                )

            every { loadOrderPort.loadById(orderId) } returns order

            When("StockReserved 이벤트가 재수신되면") {
                Then("IllegalArgumentException이 발생한다 (이미 처리됨)") {
                    shouldThrow<IllegalArgumentException> {
                        service.handleStockReserved(orderId, reservedItems, LocalDateTime.now())
                    }
                }
            }
        }

        Given("이미 PREPARING 상태인 주문이 있을 때") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.PREPARING,
                    paymentId = paymentId,
                )

            every { loadOrderPort.loadById(orderId) } returns order
            justRun { orderEventPublisher.publishStockConfirmationFailed(any(), any(), any(), any()) }

            When("PaymentCompleted 이벤트가 재수신되면") {
                Then("IllegalArgumentException이 발생한다 (이미 처리됨)") {
                    shouldThrow<IllegalArgumentException> {
                        service.handlePaymentCompleted(
                            orderId,
                            paymentId,
                            BigDecimal("50000"),
                            LocalDateTime.now(),
                        )
                    }
                }
            }
        }

        // ===== 잘못된 상태에서 이벤트 수신 테스트 =====
        Given("ORDER_CANCELLED 상태인 주문이 있을 때") {
            val orderId = UUID.randomUUID()
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.ORDER_CANCELLED,
                )

            every { loadOrderPort.loadById(orderId) } returns order
            justRun { orderEventPublisher.publishStockConfirmationFailed(any(), any(), any(), any()) }

            When("StockReserved 이벤트를 수신하면") {
                Then("IllegalArgumentException이 발생한다 (잘못된 상태)") {
                    shouldThrow<IllegalArgumentException> {
                        service.handleStockReserved(
                            orderId,
                            emptyList(),
                            LocalDateTime.now(),
                        )
                    }
                }
            }

            When("PaymentCompleted 이벤트를 수신하면") {
                Then("IllegalArgumentException이 발생한다 (잘못된 상태)") {
                    shouldThrow<IllegalArgumentException> {
                        service.handlePaymentCompleted(
                            orderId,
                            UUID.randomUUID(),
                            BigDecimal("50000"),
                            LocalDateTime.now(),
                        )
                    }
                }
            }
        }

        Given("ORDER_CONFIRMED 상태인 주문에서 결제 없이 완료 이벤트가 오는 경우") {
            val orderId = UUID.randomUUID()
            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    status = OrderStatus.ORDER_CONFIRMED,
                )

            every { loadOrderPort.loadById(orderId) } returns order
            justRun { orderEventPublisher.publishStockConfirmationFailed(any(), any(), any(), any()) }

            When("PaymentCompleted 이벤트를 수신하면") {
                Then("IllegalArgumentException이 발생한다 (PAYMENT_PENDING이 아님)") {
                    shouldThrow<IllegalArgumentException> {
                        service.handlePaymentCompleted(
                            orderId,
                            UUID.randomUUID(),
                            BigDecimal("50000"),
                            LocalDateTime.now(),
                        )
                    }
                }
            }
        }
    })
