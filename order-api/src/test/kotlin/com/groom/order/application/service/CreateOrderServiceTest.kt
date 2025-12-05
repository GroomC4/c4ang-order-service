package com.groom.order.application.service

import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.common.idempotency.IdempotencyService
import com.groom.order.domain.model.Order
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
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class CreateOrderServiceTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("정상적인 주문 생성 요청") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderManager = mockk<OrderManager>()
            val idempotencyService = mockk<IdempotencyService>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    orderManager,
                    idempotencyService,
                    domainEventPublisher,
                )

            val storeId = UUID.randomUUID()
            val productId = UUID.randomUUID()
            val userExternalId = UUID.randomUUID()
            val orderId = UUID.randomUUID()

            val orderItem =
                OrderTestFixture.createOrderItem(
                    productId = productId,
                    productName = "Test Product",
                    quantity = 2,
                    unitPrice = BigDecimal("10000"),
                )

            // OrderManager.createOrder()는 ORDER_CREATED 상태로 반환
            val createdOrder =
                OrderTestFixture.createOrder(
                    id = orderId,
                    userExternalId = userExternalId,
                    storeId = storeId,
                    status = OrderStatus.ORDER_CREATED,
                    items = listOf(orderItem),
                )

            every { idempotencyService.getOrderId(any()) } returns null
            every { idempotencyService.ensureIdempotency(any()) } returns true
            every { idempotencyService.storeOrderId(any(), any()) } just runs
            every { orderManager.createOrder(any(), any(), any(), any(), any()) } returns createdOrder
            every { saveOrderPort.save(any<Order>()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just runs

            val command =
                CreateOrderCommand(
                    userExternalId = userExternalId,
                    storeId = storeId,
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = productId,
                                productName = "Test Product",
                                quantity = 2,
                                unitPrice = BigDecimal("10000"),
                            ),
                        ),
                    idempotencyKey = "test-key-123",
                )

            When("주문을 생성하면") {
                val result = service.createOrder(command)

                Then("주문이 성공적으로 생성된다") {
                    result shouldNotBe null
                    result.status shouldBe OrderStatus.ORDER_CREATED
                }

                Then("OrderCreatedEvent가 발행된다") {
                    verify(exactly = 1) {
                        domainEventPublisher.publish(
                            match { event ->
                                event is com.groom.order.domain.event.OrderCreatedEvent &&
                                    event.orderId == orderId &&
                                    event.storeId == storeId
                            },
                        )
                    }
                }
            }
        }

        Given("중복 요청인 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderManager = mockk<OrderManager>()
            val idempotencyService = mockk<IdempotencyService>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    orderManager,
                    idempotencyService,
                    domainEventPublisher,
                )

            val command =
                CreateOrderCommand(
                    userExternalId = UUID.randomUUID(),
                    storeId = UUID.randomUUID(),
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = UUID.randomUUID(),
                                productName = "Test Product",
                                quantity = 1,
                                unitPrice = BigDecimal("10000"),
                            ),
                        ),
                    idempotencyKey = "duplicate-key",
                )

            // 멱등성 서비스가 중복 요청 감지 (ensureIdempotency = false)
            every { idempotencyService.getOrderId(command.idempotencyKey) } returns null
            every { idempotencyService.ensureIdempotency(command.idempotencyKey) } returns false

            When("주문을 생성하려고 하면") {
                Then("DuplicateOrderRequest 예외가 발생한다") {
                    shouldThrow<OrderException.DuplicateOrderRequest> {
                        service.createOrder(command)
                    }
                }
            }
        }

        Given("이미 처리된 멱등성 키로 요청한 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderManager = mockk<OrderManager>()
            val idempotencyService = mockk<IdempotencyService>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    orderManager,
                    idempotencyService,
                    domainEventPublisher,
                )

            val existingOrderId = UUID.randomUUID()
            val existingOrder =
                OrderTestFixture.createOrder(
                    id = existingOrderId,
                    status = OrderStatus.ORDER_CREATED,
                )

            val command =
                CreateOrderCommand(
                    userExternalId = UUID.randomUUID(),
                    storeId = UUID.randomUUID(),
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = UUID.randomUUID(),
                                productName = "Test Product",
                                quantity = 1,
                                unitPrice = BigDecimal("10000"),
                            ),
                        ),
                    idempotencyKey = "existing-key",
                )

            // 기존 주문이 있음
            every { idempotencyService.getOrderId(command.idempotencyKey) } returns existingOrderId.toString()
            every { loadOrderPort.loadById(existingOrderId) } returns existingOrder

            When("주문을 생성하려고 하면") {
                val result = service.createOrder(command)

                Then("기존 주문이 반환된다") {
                    result.orderId shouldBe existingOrderId
                }

                Then("새로운 주문이 생성되지 않는다") {
                    verify(exactly = 0) { orderManager.createOrder(any(), any(), any(), any(), any()) }
                }
            }
        }
    })
