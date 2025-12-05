package com.groom.order.application.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
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
import java.util.UUID

@UnitTest
class OrderInternalServiceTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        val loadOrderPort = mockk<LoadOrderPort>()
        val saveOrderPort = mockk<SaveOrderPort>()
        val service = OrderInternalService(loadOrderPort, saveOrderPort)

        // ===== getOrder 테스트 =====
        Given("존재하는 주문을 조회하는 경우") {
            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val productId = UUID.randomUUID()

            val orderItem = OrderTestFixture.createOrderItem(
                productId = productId,
                productName = "테스트 상품",
                quantity = 2,
                unitPrice = BigDecimal("25000"),
            )

            val order = OrderTestFixture.createOrder(
                id = orderId,
                userExternalId = userId,
                orderNumber = "ORD-2024-001",
                status = OrderStatus.ORDER_CONFIRMED,
                items = listOf(orderItem),
            )

            every { loadOrderPort.loadById(orderId) } returns order

            When("주문을 조회하면") {
                val result = service.getOrder(orderId)

                Then("주문 정보가 반환된다") {
                    result.orderId shouldBe orderId
                    result.userId shouldBe userId
                    result.orderNumber shouldBe "ORD-2024-001"
                    result.status shouldBe OrderStatus.ORDER_CONFIRMED
                    result.totalAmount shouldBe BigDecimal("50000")
                    result.items.size shouldBe 1
                    result.items[0].productId shouldBe productId
                    result.items[0].productName shouldBe "테스트 상품"
                    result.items[0].quantity shouldBe 2
                    result.items[0].unitPrice shouldBe BigDecimal("25000")
                }
            }
        }

        Given("존재하지 않는 주문을 조회하는 경우") {
            val orderId = UUID.randomUUID()

            every { loadOrderPort.loadById(orderId) } returns null

            When("주문을 조회하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    val exception = shouldThrow<OrderException.OrderNotFound> {
                        service.getOrder(orderId)
                    }
                    exception.orderId shouldBe orderId
                }
            }
        }

        // ===== markPaymentPending 테스트 =====
        Given("ORDER_CONFIRMED 상태의 주문에 결제를 연결하는 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()

            val order = OrderTestFixture.createOrderConfirmedOrder()
            OrderTestFixture.setField(order, "id", orderId)

            every { loadOrderPort.loadById(orderId) } returns order
            every { saveOrderPort.save(any()) } returns order

            When("결제 대기 상태로 변경하면") {
                val result = service.markPaymentPending(orderId, paymentId)

                Then("상태가 PAYMENT_PENDING으로 변경되고 paymentId가 설정된다") {
                    result.orderId shouldBe orderId
                    result.status shouldBe OrderStatus.PAYMENT_PENDING
                    result.paymentId shouldBe paymentId
                }

                Then("주문이 저장된다") {
                    verify(exactly = 1) { saveOrderPort.save(any()) }
                }
            }
        }

        Given("ORDER_CONFIRMED가 아닌 상태의 주문에 결제를 연결하는 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()

            val order = OrderTestFixture.createOrder(
                id = orderId,
                status = OrderStatus.ORDER_CREATED,
            )

            every { loadOrderPort.loadById(orderId) } returns order

            When("결제 대기 상태로 변경하면") {
                Then("OrderStatusInvalid 예외가 발생한다") {
                    val exception = shouldThrow<OrderException.OrderStatusInvalid> {
                        service.markPaymentPending(orderId, paymentId)
                    }
                    exception.orderId shouldBe orderId
                    exception.currentStatus shouldBe OrderStatus.ORDER_CREATED.name
                    exception.requiredStatus shouldBe OrderStatus.ORDER_CONFIRMED.name
                }
            }
        }

        Given("이미 결제가 연결된 주문에 결제를 연결하는 경우") {
            val orderId = UUID.randomUUID()
            val existingPaymentId = UUID.randomUUID()
            val newPaymentId = UUID.randomUUID()

            val order = OrderTestFixture.createOrder(
                id = orderId,
                status = OrderStatus.ORDER_CONFIRMED,
                paymentId = existingPaymentId,
            )

            every { loadOrderPort.loadById(orderId) } returns order

            When("결제 대기 상태로 변경하면") {
                Then("PaymentAlreadyExists 예외가 발생한다") {
                    val exception = shouldThrow<OrderException.PaymentAlreadyExists> {
                        service.markPaymentPending(orderId, newPaymentId)
                    }
                    exception.orderId shouldBe orderId
                    exception.existingPaymentId shouldBe existingPaymentId
                }
            }
        }

        Given("존재하지 않는 주문에 결제를 연결하는 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()

            every { loadOrderPort.loadById(orderId) } returns null

            When("결제 대기 상태로 변경하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.markPaymentPending(orderId, paymentId)
                    }
                }
            }
        }

        // ===== checkHasPayment 테스트 =====
        Given("결제가 연결되지 않은 주문의 결제 존재 여부를 확인하는 경우") {
            val orderId = UUID.randomUUID()

            val order = OrderTestFixture.createOrderConfirmedOrder()
            OrderTestFixture.setField(order, "id", orderId)

            every { loadOrderPort.loadById(orderId) } returns order

            When("결제 존재 여부를 확인하면") {
                val result = service.checkHasPayment(orderId)

                Then("hasPayment가 false로 반환된다") {
                    result.orderId shouldBe orderId
                    result.hasPayment shouldBe false
                }
            }
        }

        Given("결제가 연결된 주문의 결제 존재 여부를 확인하는 경우") {
            val orderId = UUID.randomUUID()
            val paymentId = UUID.randomUUID()

            val order = OrderTestFixture.createOrder(
                id = orderId,
                status = OrderStatus.PAYMENT_PENDING,
                paymentId = paymentId,
            )

            every { loadOrderPort.loadById(orderId) } returns order

            When("결제 존재 여부를 확인하면") {
                val result = service.checkHasPayment(orderId)

                Then("hasPayment가 true로 반환된다") {
                    result.orderId shouldBe orderId
                    result.hasPayment shouldBe true
                }
            }
        }

        Given("존재하지 않는 주문의 결제 존재 여부를 확인하는 경우") {
            val orderId = UUID.randomUUID()

            every { loadOrderPort.loadById(orderId) } returns null

            When("결제 존재 여부를 확인하면") {
                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.checkHasPayment(orderId)
                    }
                }
            }
        }
    })
