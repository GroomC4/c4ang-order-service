package com.groom.order.application.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.application.dto.RefundOrderCommand
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.service.OrderManager
import com.groom.order.fixture.OrderTestFixture
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@UnitTest
class RefundOrderServiceTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("배송 완료된 주문이 있는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                RefundOrderService(
                    orderRepository,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val orderItem =
                OrderTestFixture.createOrderItem(
                    productId = UUID.randomUUID(),
                    productName = "Test Product",
                    quantity = 2,
                    unitPrice = BigDecimal("10000"),
                )

            val order =
                OrderTestFixture
                    .createDeliveredOrder(
                        userExternalId = userId,
                        storeId = storeId,
                        items = listOf(orderItem),
                    ).apply {
                        OrderTestFixture.setField(this, "id", orderId)
                    }

            every { orderRepository.findById(orderId) } returns Optional.of(order)
            every { orderManager.validateRefund(any<Order>(), any<UUID>()) } just runs
            every { orderRepository.save(any<Order>()) } answers { firstArg() }
            every { orderRepository.flush() } just runs
            every { domainEventPublisher.publish(any()) } just runs

            When("환불을 요청하면") {
                val command = RefundOrderCommand(orderId, userId, "상품 불량")
                val result = service.refundOrder(command)

                Then("환불이 처리된다") {
                    result shouldNotBe null
                    result.orderId shouldBe orderId
                    result.status shouldBe OrderStatus.REFUND_COMPLETED
                    result.refundAmount shouldBe BigDecimal("20000")
                    result.refundReason shouldBe "상품 불량"
                    result.refundId shouldStartWith "REFUND-"

                    // 결과 상태로 검증하므로 verify 불필요
                }
            }
        }

        Given("배송 전 주문을 환불하려는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                RefundOrderService(
                    orderRepository,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-12345",
                    status = OrderStatus.PREPARING,
                )

            every { orderRepository.findById(orderId) } returns Optional.of(order)
            every { orderManager.validateRefund(any<Order>(), any<UUID>()) } throws
                OrderException.CannotRefundOrder(orderId, OrderStatus.PREPARING.name)

            When("환불을 요청하면") {
                val command = RefundOrderCommand(orderId, userId, "상품 불량")

                Then("CannotRefundOrder 예외가 발생한다") {
                    shouldThrow<OrderException.CannotRefundOrder> {
                        service.refundOrder(command)
                    }

                    // 예외 발생으로 검증하므로 verify 불필요
                }
            }
        }

        Given("다른 사용자의 주문을 환불하려는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                RefundOrderService(
                    orderRepository,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val orderOwnerId = UUID.randomUUID()
            val requestUserId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val order =
                OrderTestFixture
                    .createDeliveredOrder(
                        userExternalId = orderOwnerId,
                        storeId = storeId,
                    ).apply {
                        OrderTestFixture.setField(this, "id", orderId)
                    }

            every { orderRepository.findById(orderId) } returns Optional.of(order)
            every { orderManager.validateRefund(any<Order>(), any<UUID>()) } throws
                OrderException.OrderAccessDenied(orderId, requestUserId)

            When("다른 사용자가 환불을 요청하면") {
                val command = RefundOrderCommand(orderId, requestUserId, "상품 불량")

                Then("OrderAccessDenied 예외가 발생한다") {
                    shouldThrow<OrderException.OrderAccessDenied> {
                        service.refundOrder(command)
                    }

                    // 예외 발생으로 검증하므로 verify 불필요
                }
            }
        }

        Given("존재하지 않는 주문을 환불하려는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                RefundOrderService(
                    orderRepository,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            every { orderRepository.findById(orderId) } returns Optional.empty()

            When("환불을 요청하면") {
                val command = RefundOrderCommand(orderId, userId, "상품 불량")

                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.refundOrder(command)
                    }

                    // 예외 발생으로 검증하므로 verify 불필요
                }
            }
        }
    })
