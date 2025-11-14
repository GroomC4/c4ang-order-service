package com.groom.order.application.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.exception.OrderException
import com.groom.order.application.dto.GetOrderDetailQuery
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.service.OrderPolicy
import com.groom.order.fixture.OrderTestFixture
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
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
import java.util.UUID

@UnitTest
class GetOrderDetailServiceTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("존재하는 주문을 조회하는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderPolicy = mockk<OrderPolicy>()
            val service = GetOrderDetailService(loadOrderPort,
                    saveOrderPort, orderPolicy)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val productId = UUID.randomUUID()

            val orderItem =
                OrderTestFixture.createOrderItem(
                    productId = productId,
                    productName = "Test Product",
                    quantity = 2,
                    unitPrice = BigDecimal("10000"),
                )

            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-12345",
                    status = OrderStatus.PAYMENT_COMPLETED,
                    items = listOf(orderItem),
                )

            every { loadOrderPort.loadById(orderId) } returns order
            every { orderPolicy.checkOrderOwnership(order, userId) } just runs

            When("본인이 자신의 주문을 조회하면") {
                val query = GetOrderDetailQuery(orderId, userId)
                val result = service.getOrderDetail(query)

                Then("주문 상세 정보가 반환된다") {
                    result shouldNotBe null
                    result.orderId shouldBe orderId
                    result.orderNumber shouldBe "ORD-12345"
                    result.userExternalId shouldBe userId
                    result.storeId shouldBe storeId
                    result.status shouldBe OrderStatus.PAYMENT_COMPLETED
                    result.totalAmount shouldBe BigDecimal("20000")
                    result.items.size shouldBe 1
                    result.items[0].productName shouldBe "Test Product"
                    result.items[0].quantity shouldBe 2
                }
            }
        }

        Given("다른 사용자의 주문을 조회하는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderPolicy = mockk<OrderPolicy>()
            val service = GetOrderDetailService(loadOrderPort,
                    saveOrderPort, orderPolicy)

            val orderId = UUID.randomUUID()
            val orderOwnerId = UUID.randomUUID()
            val requestUserId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val order =
                OrderTestFixture.createOrder(
                    id = orderId,
                    userExternalId = orderOwnerId,
                    storeId = storeId,
                    orderNumber = "ORD-12345",
                    status = OrderStatus.PENDING,
                )

            every { loadOrderPort.loadById(orderId) } returns order
            every { orderPolicy.checkOrderOwnership(order, requestUserId) } throws
                OrderException.OrderAccessDenied(orderId, requestUserId)

            When("다른 사용자가 주문을 조회하려고 하면") {
                val query = GetOrderDetailQuery(orderId, requestUserId)

                Then("OrderAccessDenied 예외가 발생한다") {
                    shouldThrow<OrderException.OrderAccessDenied> {
                        service.getOrderDetail(query)
                    }
                }
            }
        }

        Given("존재하지 않는 주문을 조회하는 경우") {
            val loadOrderPort = mockk<LoadOrderPort>()
            val saveOrderPort = mockk<SaveOrderPort>()
            val orderPolicy = mockk<OrderPolicy>()
            val service = GetOrderDetailService(loadOrderPort,
                    saveOrderPort, orderPolicy)

            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            every { loadOrderPort.loadById(orderId) } returns null

            When("존재하지 않는 주문을 조회하려고 하면") {
                val query = GetOrderDetailQuery(orderId, userId)

                Then("OrderNotFound 예외가 발생한다") {
                    shouldThrow<OrderException.OrderNotFound> {
                        service.getOrderDetail(query)
                    }
                }
            }
        }
    })
