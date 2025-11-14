package com.groom.order.application.service

import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.common.exception.ProductException
import com.groom.order.common.exception.StoreException
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.model.ProductInfo
import com.groom.order.domain.model.StockReservation
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.ProductPort
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.domain.port.StorePort
import com.groom.order.domain.service.OrderManager
import com.groom.order.domain.service.OrderPolicy
import com.groom.order.domain.service.StockReservationManager
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
            val productPort = mockk<ProductPort>()
            val storePort = mockk<StorePort>()
            val stockReservationManager = mockk<StockReservationManager>()
            val orderManager = mockk<OrderManager>()
            val orderPolicy = mockk<OrderPolicy>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    productPort,
                    storePort,
                    stockReservationManager,
                    orderManager,
                    orderPolicy,
                    domainEventPublisher,
                )

            val storeId = UUID.randomUUID()
            val productId = UUID.randomUUID()
            val userExternalId = UUID.randomUUID()

            val mockProduct =
                ProductInfo(
                    id = productId,
                    storeId = storeId,
                    storeName = "Test Store",
                    name = "Test Product",
                    price = BigDecimal("10000"),
                )

            val mockStockReservation =
                StockReservation(
                    reservationId = "test-reservation-id",
                    storeId = storeId,
                    items =
                        listOf(
                            StockReservation.ReservationItem(
                                productId = productId,
                                quantity = 2,
                            ),
                        ),
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )

            val orderId = UUID.randomUUID()
            val orderItem =
                OrderTestFixture.createOrderItem(
                    productId = productId,
                    productName = "Test Product",
                    quantity = 2,
                    unitPrice = BigDecimal("10000"),
                )
            // OrderManager.createOrder()는 PENDING 상태로 반환
            val createdOrder =
                OrderTestFixture.createOrder(
                    id = orderId,
                    userExternalId = userExternalId,
                    storeId = storeId,
                    status = OrderStatus.PENDING,
                    reservationId = "test-reservation-id",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                    items = listOf(orderItem),
                )

            every { storePort.existsById(storeId) } returns true
            every { productPort.loadById(productId) } returns mockProduct
            every { orderPolicy.validateProductsBelongToStore(any(), any()) } just runs
            every { stockReservationManager.generateStockReservation(any(), any(), any()) } returns mockStockReservation
            every { stockReservationManager.tryReserve(any()) } returns mockStockReservation
            every { orderManager.createOrder(any(), any(), any(), any(), any(), any(), any(), any()) } returns createdOrder
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
                                quantity = 2,
                            ),
                        ),
                    idempotencyKey = "test-key-123",
                )

            When("주문을 생성하면") {
                val result = service.createOrder(command)

                Then("주문이 성공적으로 생성된다") {
                    result shouldNotBe null
                    result.status shouldBe OrderStatus.STOCK_RESERVED

                    // 결과 상태로 검증하므로 구현 세부사항(verify)는 불필요
                }
            }
        }

        Given("중복 요청인 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val productPort = mockk<ProductPort>()
            val storePort = mockk<StorePort>()
            val stockReservationManager = mockk<StockReservationManager>()
            val orderManager = mockk<OrderManager>()
            val orderPolicy = mockk<OrderPolicy>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    productPort,
                    storePort,
                    stockReservationManager,
                    orderManager,
                    orderPolicy,
                    domainEventPublisher,
                )

            // Stripe 방식: getOrderId()로 null 반환 후, ensureIdempotency()도 false, getOrderId()도 null이면 예외 발생

            val command =
                CreateOrderCommand(
                    userExternalId = UUID.randomUUID(),
                    storeId = UUID.randomUUID(),
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = UUID.randomUUID(),
                                quantity = 1,
                            ),
                        ),
                    idempotencyKey = "duplicate-key",
                )

            When("주문을 생성하려고 하면") {
                Then("DuplicateOrderRequest 예외가 발생한다") {
                    shouldThrow<OrderException.DuplicateOrderRequest> {
                        service.createOrder(command)
                    }

                    // 예외 발생으로 검증하므로 verify 불필요
                }
            }
        }

        Given("존재하지 않는 스토어로 주문하는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val productPort = mockk<ProductPort>()
            val storePort = mockk<StorePort>()
            val stockReservationManager = mockk<StockReservationManager>()
            val orderManager = mockk<OrderManager>()
            val orderPolicy = mockk<OrderPolicy>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    productPort,
                    storePort,
                    stockReservationManager,
                    orderManager,
                    orderPolicy,
                    domainEventPublisher,
                )

            val storeId = UUID.randomUUID()

            every { storePort.existsById(storeId) } returns false

            val command =
                CreateOrderCommand(
                    userExternalId = UUID.randomUUID(),
                    storeId = storeId,
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = UUID.randomUUID(),
                                quantity = 1,
                            ),
                        ),
                    idempotencyKey = "test-key",
                )

            When("주문을 생성하려고 하면") {
                Then("StoreNotFound 예외가 발생한다") {
                    shouldThrow<StoreException.StoreNotFound> {
                        service.createOrder(command)
                    }

                    // 예외 발생으로 검증하므로 verify 불필요
                }
            }
        }

        Given("존재하지 않는 상품으로 주문하는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val productPort = mockk<ProductPort>()
            val storePort = mockk<StorePort>()
            val stockReservationManager = mockk<StockReservationManager>()
            val orderManager = mockk<OrderManager>()
            val orderPolicy = mockk<OrderPolicy>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    productPort,
                    storePort,
                    stockReservationManager,
                    orderManager,
                    orderPolicy,
                    domainEventPublisher,
                )

            val storeId = UUID.randomUUID()
            val productId = UUID.randomUUID()

            every { storePort.existsById(storeId) } returns true
            every { productPort.loadById(productId) } returns null

            val command =
                CreateOrderCommand(
                    userExternalId = UUID.randomUUID(),
                    storeId = storeId,
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = productId,
                                quantity = 1,
                            ),
                        ),
                    idempotencyKey = "test-key",
                )

            When("주문을 생성하려고 하면") {
                Then("ProductNotFound 예외가 발생한다") {
                    shouldThrow<ProductException.ProductNotFound> {
                        service.createOrder(command)
                    }

                    // 예외 발생으로 검증하므로 verify 불필요
                }
            }
        }

        Given("재고가 부족한 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val productPort = mockk<ProductPort>()
            val storePort = mockk<StorePort>()
            val stockReservationManager = mockk<StockReservationManager>()
            val orderManager = mockk<OrderManager>()
            val orderPolicy = mockk<OrderPolicy>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val service =
                CreateOrderService(
                    loadOrderPort,
                    saveOrderPort,
                    productPort,
                    storePort,
                    stockReservationManager,
                    orderManager,
                    orderPolicy,
                    domainEventPublisher,
                )

            val storeId = UUID.randomUUID()
            val productId = UUID.randomUUID()

            val mockProduct =
                ProductInfo(
                    id = productId,
                    storeId = storeId,
                    storeName = "Test Store",
                    name = "Test Product",
                    price = BigDecimal("10000"),
                )

            val mockStockReservation =
                StockReservation(
                    reservationId = "test-reservation-id",
                    storeId = storeId,
                    items =
                        listOf(
                            StockReservation.ReservationItem(
                                productId = productId,
                                quantity = 100,
                            ),
                        ),
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )

            every { storePort.existsById(storeId) } returns true
            every { productPort.loadById(productId) } returns mockProduct
            every { orderPolicy.validateProductsBelongToStore(any(), any()) } just runs
            every { stockReservationManager.generateStockReservation(any(), any(), any()) } returns mockStockReservation
            every { stockReservationManager.tryReserve(any()) } throws OrderException.InsufficientStock(storeId)

            val command =
                CreateOrderCommand(
                    userExternalId = UUID.randomUUID(),
                    storeId = storeId,
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = productId,
                                quantity = 100,
                            ),
                        ),
                    idempotencyKey = "test-key",
                )

            When("주문을 생성하려고 하면") {
                Then("InsufficientStock 예외가 발생한다") {
                    shouldThrow<OrderException.InsufficientStock> {
                        service.createOrder(command)
                    }

                    // 예외 발생으로 검증하므로 verify 불필요
                }
            }
        }
    })
