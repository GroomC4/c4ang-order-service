package com.groom.order.application.service

import com.groom.order.application.dto.CancelOrderCommand
import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.event.OrderCancelledEvent
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.domain.service.OrderManager
import com.groom.order.fixture.OrderTestFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class CancelOrderServiceTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("취소 가능한 주문이 있는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CancelOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val reservationId = "test-reservation-123"

            val order =
                OrderTestFixture
                    .createPaymentPendingOrder(
                        userExternalId = userId,
                        storeId = storeId,
                        reservationId = reservationId,
                    ).apply {
                        OrderTestFixture.setField(this, "id", orderId)
                    }

            val cancelEvent =
                OrderCancelledEvent(
                    orderId = orderId,
                    orderNumber = order.orderNumber,
                    userExternalId = userId,
                    storeId = storeId,
                    cancelReason = "고객 변심",
                    cancelledAt = LocalDateTime.now(),
                )

            every { loadOrderPort.loadById(orderId) } returns order
            every { orderManager.cancelOrder(order, userId, "고객 변심", any()) } answers {
                // 실제 동작처럼 Order의 상태를 변경
                order.status = OrderStatus.ORDER_CANCELLED
                order.cancelledAt = LocalDateTime.now()
                order.failureReason = "고객 변심"
                cancelEvent
            }
            every { saveOrderPort.save(any()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just runs

            When("주문을 취소하면") {
                val command = CancelOrderCommand(orderId, userId, "고객 변심")
                val result = service.cancelOrder(command)

                Then("주문이 취소되고 OrderCancelledEvent가 발행된다") {
                    result shouldNotBe null
                    result.orderId shouldBe orderId
                    result.status shouldBe OrderStatus.ORDER_CANCELLED
                    result.cancelReason shouldBe "고객 변심"
                    result.cancelledAt shouldNotBe null
                }
            }
        }

        Given("배송 완료된 주문을 취소하려는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CancelOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val order =
                OrderTestFixture
                    .createDeliveredOrder(
                        userExternalId = userId,
                        storeId = storeId,
                    ).apply {
                        OrderTestFixture.setField(this, "id", orderId)
                    }

            every { loadOrderPort.loadById(orderId) } returns order
            every { orderManager.cancelOrder(order, userId, "고객 변심", any()) } throws
                OrderException.CannotCancelOrder(orderId, OrderStatus.DELIVERED.name)

            When("주문을 취소하려고 하면") {
                val command = CancelOrderCommand(orderId, userId, "고객 변심")

                Then("CannotCancelOrder 예외가 발생한다") {
                    shouldThrow<OrderException.CannotCancelOrder> {
                        service.cancelOrder(command)
                    }
                }
            }
        }

        Given("다른 사용자의 주문을 취소하려는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CancelOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val orderOwnerId = UUID.randomUUID()
            val requestUserId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    userExternalId = orderOwnerId,
                    storeId = storeId,
                    status = OrderStatus.ORDER_CREATED,
                )

            every { loadOrderPort.loadById(orderId) } returns order
            every { orderManager.cancelOrder(order, requestUserId, "고객 변심", any()) } throws
                OrderException.OrderAccessDenied(orderId, requestUserId)

            When("다른 사용자가 주문을 취소하려고 하면") {
                val command = CancelOrderCommand(orderId, requestUserId, "고객 변심")

                Then("OrderAccessDenied 예외가 발생한다") {
                    shouldThrow<OrderException.OrderAccessDenied> {
                        service.cancelOrder(command)
                    }
                }
            }
        }

        Given("존재하지 않는 주문을 취소하려는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderManager = mockk<OrderManager>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CancelOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    orderManager,
                    domainEventPublisher,
                )

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            every { loadOrderPort.loadById(orderId) } returns null

            When("주문을 취소하려고 하면") {
                val command = CancelOrderCommand(orderId, userId, "고객 변심")

                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.cancelOrder(command)
                    }
                }
            }
        }
    })
