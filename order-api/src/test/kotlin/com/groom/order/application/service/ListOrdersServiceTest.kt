package com.groom.order.application.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.application.dto.ListOrdersQuery
import com.groom.order.domain.model.OrderStatus
import com.groom.order.fixture.OrderTestFixture
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.util.UUID

@UnitTest
class ListOrdersServiceTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("사용자의 모든 주문을 조회하는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val service = ListOrdersService(orderRepository)

            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val item1 =
                OrderTestFixture.createOrderItem(
                    productId = UUID.randomUUID(),
                    productName = "Product 1",
                    quantity = 1,
                    unitPrice = BigDecimal("10000"),
                )

            val item2 =
                OrderTestFixture.createOrderItem(
                    productId = UUID.randomUUID(),
                    productName = "Product 2",
                    quantity = 2,
                    unitPrice = BigDecimal("5000"),
                )

            val order1 =
                OrderTestFixture.createOrder(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-001",
                    status = OrderStatus.PAYMENT_COMPLETED,
                    items = listOf(item1),
                )

            val order2 =
                OrderTestFixture.createOrder(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-002",
                    status = OrderStatus.PENDING,
                    items = listOf(item2),
                )

            every { orderRepository.findByUserExternalId(userId) } returns listOf(order1, order2)

            When("상태 필터 없이 주문 목록을 조회하면") {
                val query = ListOrdersQuery(requestUserId = userId, status = null)
                val result = service.listOrders(query)

                Then("모든 주문이 반환된다") {
                    result.orders.size shouldBe 2
                    result.orders[0].orderNumber shouldBe "ORD-001"
                    result.orders[0].status shouldBe OrderStatus.PAYMENT_COMPLETED
                    result.orders[0].totalAmount shouldBe BigDecimal("10000")
                    result.orders[0].itemCount shouldBe 1

                    result.orders[1].orderNumber shouldBe "ORD-002"
                    result.orders[1].status shouldBe OrderStatus.PENDING
                    result.orders[1].totalAmount shouldBe BigDecimal("10000")
                    result.orders[1].itemCount shouldBe 1

                    // 결과 상태로 충분히 검증하므로 repository 호출 verify 불필요
                }
            }
        }

        Given("특정 상태의 주문만 조회하는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val service = ListOrdersService(orderRepository)

            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val item3 =
                OrderTestFixture.createOrderItem(
                    productId = UUID.randomUUID(),
                    productName = "Product 3",
                    quantity = 1,
                    unitPrice = BigDecimal("15000"),
                )

            val completedOrder =
                OrderTestFixture.createOrder(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-003",
                    status = OrderStatus.PAYMENT_COMPLETED,
                    items = listOf(item3),
                )

            every {
                orderRepository.findByUserExternalIdAndStatus(
                    userId,
                    OrderStatus.PAYMENT_COMPLETED,
                )
            } returns listOf(completedOrder)

            When("PAYMENT_COMPLETED 상태로 필터링하면") {
                val query = ListOrdersQuery(requestUserId = userId, status = OrderStatus.PAYMENT_COMPLETED)
                val result = service.listOrders(query)

                Then("해당 상태의 주문만 반환된다") {
                    result.orders.size shouldBe 1
                    result.orders[0].orderNumber shouldBe "ORD-003"
                    result.orders[0].status shouldBe OrderStatus.PAYMENT_COMPLETED
                    result.orders[0].totalAmount shouldBe BigDecimal("15000")

                    // 결과 상태로 충분히 검증하므로 repository 호출 verify 불필요
                }
            }
        }

        Given("주문이 없는 사용자가 조회하는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val service = ListOrdersService(orderRepository)

            val userId = UUID.randomUUID()

            every { orderRepository.findByUserExternalId(userId) } returns emptyList()

            When("주문 목록을 조회하면") {
                val query = ListOrdersQuery(requestUserId = userId)
                val result = service.listOrders(query)

                Then("빈 목록이 반환된다") {
                    result.orders.size shouldBe 0

                    // 결과 상태로 충분히 검증하므로 repository 호출 verify 불필요
                }
            }
        }
    })
