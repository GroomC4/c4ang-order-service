package com.groom.order.domain.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderStatus
import com.groom.order.fixture.OrderTestFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class OrderManagerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        // ===== createOrder 테스트 =====
        Given("주문 생성 시") {
            val orderPolicy = OrderPolicy()
            val orderNumberGenerator = mockk<OrderNumberGenerator>()
            val orderManager = OrderManager(orderPolicy, orderNumberGenerator)

            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val productId = UUID.randomUUID()
            val now = LocalDateTime.now()

            val itemRequests =
                listOf(
                    OrderManager.OrderItemRequest(
                        productId = productId,
                        productName = "Test Product",
                        quantity = 2,
                        unitPrice = BigDecimal("10000"),
                    ),
                )

            every { orderNumberGenerator.generate(any()) } returns "ORD-20251202-ABC123"

            When("정상적인 주문 정보로 생성하면") {
                val order =
                    orderManager.createOrder(
                        userId = userId,
                        storeId = storeId,
                        itemRequests = itemRequests,
                        note = "테스트 주문",
                        now = now,
                    )

                Then("주문이 정상적으로 생성된다") {
                    order shouldNotBe null
                    order.userExternalId shouldBe userId
                    order.storeId shouldBe storeId
                    order.orderNumber shouldBe "ORD-20251202-ABC123"
                    order.status shouldBe OrderStatus.ORDER_CREATED
                    order.note shouldBe "테스트 주문"
                    order.items.size shouldBe 1
                    order.items[0].productName shouldBe "Test Product"
                    order.items[0].quantity shouldBe 2
                    order.items[0].unitPrice shouldBe BigDecimal("10000")
                }
            }

            When("여러 상품으로 주문을 생성하면") {
                val productId2 = UUID.randomUUID()
                val multiItemRequests =
                    listOf(
                        OrderManager.OrderItemRequest(
                            productId = productId,
                            productName = "Product 1",
                            quantity = 2,
                            unitPrice = BigDecimal("10000"),
                        ),
                        OrderManager.OrderItemRequest(
                            productId = productId2,
                            productName = "Product 2",
                            quantity = 3,
                            unitPrice = BigDecimal("5000"),
                        ),
                    )

                val order =
                    orderManager.createOrder(
                        userId = userId,
                        storeId = storeId,
                        itemRequests = multiItemRequests,
                        note = null,
                        now = now,
                    )

                Then("모든 상품이 주문에 포함된다") {
                    order.items.size shouldBe 2
                    order.calculateTotalAmount() shouldBe BigDecimal("35000") // 20000 + 15000
                }
            }

            When("주문 상품이 비어있으면") {
                Then("IllegalArgumentException이 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        orderManager.createOrder(
                            userId = userId,
                            storeId = storeId,
                            itemRequests = emptyList(),
                            note = null,
                            now = now,
                        )
                    }
                }
            }
        }

        // ===== cancelOrder 테스트 =====
        Given("주문 취소 시") {
            val orderPolicy = OrderPolicy()
            val orderNumberGenerator = mockk<OrderNumberGenerator>()
            val orderManager = OrderManager(orderPolicy, orderNumberGenerator)

            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val now = LocalDateTime.now()

            When("취소 가능한 상태의 본인 주문을 취소하면") {
                val order =
                    OrderTestFixture
                        .createPaymentPendingOrder(
                            userExternalId = userId,
                            storeId = storeId,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                val cancelEvent =
                    orderManager.cancelOrder(
                        order = order,
                        userId = userId,
                        cancelReason = "고객 변심",
                        now = now,
                    )

                Then("주문이 취소되고 이벤트가 반환된다") {
                    order.status shouldBe OrderStatus.ORDER_CANCELLED
                    order.cancelledAt shouldBe now
                    order.failureReason shouldBe "고객 변심"

                    cancelEvent shouldNotBe null
                    cancelEvent.orderId shouldBe orderId
                    cancelEvent.userExternalId shouldBe userId
                    cancelEvent.cancelReason shouldBe "고객 변심"
                }
            }

            When("ORDER_CREATED 상태의 주문을 취소하면") {
                val order =
                    OrderTestFixture
                        .createOrder(
                            userExternalId = userId,
                            storeId = storeId,
                            status = OrderStatus.ORDER_CREATED,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                val cancelEvent =
                    orderManager.cancelOrder(
                        order = order,
                        userId = userId,
                        cancelReason = "주문 실수",
                        now = now,
                    )

                Then("주문이 취소된다") {
                    order.status shouldBe OrderStatus.ORDER_CANCELLED
                    cancelEvent.cancelReason shouldBe "주문 실수"
                }
            }

            When("다른 사용자의 주문을 취소하려고 하면") {
                val otherUserId = UUID.randomUUID()
                val order =
                    OrderTestFixture
                        .createPaymentPendingOrder(
                            userExternalId = userId,
                            storeId = storeId,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("OrderAccessDenied 예외가 발생한다") {
                    shouldThrow<OrderException.OrderAccessDenied> {
                        orderManager.cancelOrder(
                            order = order,
                            userId = otherUserId,
                            cancelReason = "취소 요청",
                            now = now,
                        )
                    }
                }
            }

            When("배송 완료된 주문을 취소하려고 하면") {
                val order =
                    OrderTestFixture
                        .createDeliveredOrder(
                            userExternalId = userId,
                            storeId = storeId,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("CannotCancelOrder 예외가 발생한다") {
                    shouldThrow<OrderException.CannotCancelOrder> {
                        orderManager.cancelOrder(
                            order = order,
                            userId = userId,
                            cancelReason = "취소 요청",
                            now = now,
                        )
                    }
                }
            }

            When("배송 중인 주문을 취소하려고 하면") {
                val order =
                    OrderTestFixture
                        .createOrder(
                            userExternalId = userId,
                            storeId = storeId,
                            status = OrderStatus.SHIPPED,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("CannotCancelOrder 예외가 발생한다") {
                    shouldThrow<OrderException.CannotCancelOrder> {
                        orderManager.cancelOrder(
                            order = order,
                            userId = userId,
                            cancelReason = "취소 요청",
                            now = now,
                        )
                    }
                }
            }
        }

        // ===== validateRefund 테스트 =====
        Given("환불 검증 시") {
            val orderPolicy = OrderPolicy()
            val orderNumberGenerator = mockk<OrderNumberGenerator>()
            val orderManager = OrderManager(orderPolicy, orderNumberGenerator)

            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val orderId = UUID.randomUUID()

            When("배송 완료된 본인 주문이면") {
                val order =
                    OrderTestFixture
                        .createDeliveredOrder(
                            userExternalId = userId,
                            storeId = storeId,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("검증을 통과한다") {
                    // 예외 없이 통과
                    orderManager.validateRefund(order, userId)
                }
            }

            When("다른 사용자의 주문을 환불하려고 하면") {
                val otherUserId = UUID.randomUUID()
                val order =
                    OrderTestFixture
                        .createDeliveredOrder(
                            userExternalId = userId,
                            storeId = storeId,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("OrderAccessDenied 예외가 발생한다") {
                    shouldThrow<OrderException.OrderAccessDenied> {
                        orderManager.validateRefund(order, otherUserId)
                    }
                }
            }

            When("배송 전 주문을 환불하려고 하면") {
                val order =
                    OrderTestFixture
                        .createOrder(
                            userExternalId = userId,
                            storeId = storeId,
                            status = OrderStatus.PREPARING,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("CannotRefundOrder 예외가 발생한다") {
                    shouldThrow<OrderException.CannotRefundOrder> {
                        orderManager.validateRefund(order, userId)
                    }
                }
            }

            When("결제 완료 상태의 주문을 환불하려고 하면") {
                val order =
                    OrderTestFixture
                        .createOrder(
                            userExternalId = userId,
                            storeId = storeId,
                            status = OrderStatus.PAYMENT_COMPLETED,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("CannotRefundOrder 예외가 발생한다") {
                    shouldThrow<OrderException.CannotRefundOrder> {
                        orderManager.validateRefund(order, userId)
                    }
                }
            }

            When("배송 중인 주문을 환불하려고 하면") {
                val order =
                    OrderTestFixture
                        .createOrder(
                            userExternalId = userId,
                            storeId = storeId,
                            status = OrderStatus.SHIPPED,
                        ).apply {
                            OrderTestFixture.setField(this, "id", orderId)
                        }

                Then("CannotRefundOrder 예외가 발생한다") {
                    shouldThrow<OrderException.CannotRefundOrder> {
                        orderManager.validateRefund(order, userId)
                    }
                }
            }
        }
    })
