package com.groom.order.domain.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.model.ProductInfo
import com.groom.order.fixture.OrderTestFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

@UnitTest
class OrderPolicyTest :
    BehaviorSpec({
        val orderPolicy = OrderPolicy()

        // ===== validateOrderCreation 테스트 =====
        Given("주문 생성 검증 시") {
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            When("정상적인 주문 상품이 있으면") {
                val items = listOf(
                    OrderItemData(
                        productId = UUID.randomUUID(),
                        productName = "Test Product",
                        quantity = 1,
                        unitPrice = BigDecimal("10000"),
                    ),
                )

                Then("검증을 통과한다") {
                    // 예외 없이 통과
                    orderPolicy.validateOrderCreation(userId, storeId, items)
                }
            }

            When("주문 상품이 비어있으면") {
                val items = emptyList<OrderItemData>()

                Then("IllegalArgumentException이 발생한다") {
                    val exception = shouldThrow<IllegalArgumentException> {
                        orderPolicy.validateOrderCreation(userId, storeId, items)
                    }
                    exception.message shouldBe "Order must contain at least one item"
                }
            }

            When("주문 상품 수량이 0이면") {
                val items = listOf(
                    OrderItemData(
                        productId = UUID.randomUUID(),
                        productName = "Test Product",
                        quantity = 0,
                        unitPrice = BigDecimal("10000"),
                    ),
                )

                Then("IllegalArgumentException이 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        orderPolicy.validateOrderCreation(userId, storeId, items)
                    }
                }
            }

            When("주문 상품 수량이 음수이면") {
                val items = listOf(
                    OrderItemData(
                        productId = UUID.randomUUID(),
                        productName = "Test Product",
                        quantity = -1,
                        unitPrice = BigDecimal("10000"),
                    ),
                )

                Then("IllegalArgumentException이 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        orderPolicy.validateOrderCreation(userId, storeId, items)
                    }
                }
            }

            When("주문 상품 수량이 999를 초과하면") {
                val items = listOf(
                    OrderItemData(
                        productId = UUID.randomUUID(),
                        productName = "Test Product",
                        quantity = 1000,
                        unitPrice = BigDecimal("10000"),
                    ),
                )

                Then("IllegalArgumentException이 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        orderPolicy.validateOrderCreation(userId, storeId, items)
                    }
                }
            }

            When("주문 상품 수량이 정확히 999이면") {
                val items = listOf(
                    OrderItemData(
                        productId = UUID.randomUUID(),
                        productName = "Test Product",
                        quantity = 999,
                        unitPrice = BigDecimal("10000"),
                    ),
                )

                Then("검증을 통과한다") {
                    orderPolicy.validateOrderCreation(userId, storeId, items)
                }
            }
        }

        // ===== checkOrderOwnership 테스트 =====
        Given("주문 소유권 검증 시") {
            val ownerId = UUID.randomUUID()
            val order = OrderTestFixture.createOrder(userExternalId = ownerId)

            When("주문 소유자가 요청하면") {
                Then("검증을 통과한다") {
                    // 예외 없이 통과
                    orderPolicy.checkOrderOwnership(order, ownerId)
                }
            }

            When("다른 사용자가 요청하면") {
                val otherUserId = UUID.randomUUID()

                Then("OrderAccessDenied 예외가 발생한다") {
                    shouldThrow<OrderException.OrderAccessDenied> {
                        orderPolicy.checkOrderOwnership(order, otherUserId)
                    }
                }
            }
        }

        // ===== canCancelOrder 테스트 =====
        Given("주문 취소 가능 여부 확인 시") {
            When("PENDING 상태이면") {
                val order = OrderTestFixture.createOrder(status = OrderStatus.PENDING)

                Then("취소 가능하다") {
                    orderPolicy.canCancelOrder(order) shouldBe true
                }
            }

            When("STOCK_RESERVED 상태이면") {
                val order = OrderTestFixture.createStockReservedOrder()

                Then("취소 가능하다") {
                    orderPolicy.canCancelOrder(order) shouldBe true
                }
            }

            When("PAYMENT_PENDING 상태이면") {
                val order = OrderTestFixture.createPaymentPendingOrder()

                Then("취소 가능하다") {
                    orderPolicy.canCancelOrder(order) shouldBe true
                }
            }

            When("PREPARING 상태이면") {
                val order = OrderTestFixture.createOrder(status = OrderStatus.PREPARING)

                Then("취소 가능하다") {
                    orderPolicy.canCancelOrder(order) shouldBe true
                }
            }

            When("SHIPPED 상태이면") {
                val order = OrderTestFixture.createOrder(status = OrderStatus.SHIPPED)

                Then("취소 불가능하다") {
                    orderPolicy.canCancelOrder(order) shouldBe false
                }
            }

            When("DELIVERED 상태이면") {
                val order = OrderTestFixture.createDeliveredOrder()

                Then("취소 불가능하다") {
                    orderPolicy.canCancelOrder(order) shouldBe false
                }
            }

            When("ORDER_CANCELLED 상태이면") {
                val order = OrderTestFixture.createOrder(status = OrderStatus.ORDER_CANCELLED)

                Then("취소 불가능하다") {
                    orderPolicy.canCancelOrder(order) shouldBe false
                }
            }
        }

        // ===== canRefundOrder 테스트 =====
        Given("주문 환불 가능 여부 확인 시") {
            When("DELIVERED 상태이면") {
                val order = OrderTestFixture.createDeliveredOrder()

                Then("환불 가능하다") {
                    orderPolicy.canRefundOrder(order) shouldBe true
                }
            }

            When("PAYMENT_COMPLETED 상태이면") {
                val order = OrderTestFixture.createOrder(status = OrderStatus.PAYMENT_COMPLETED)

                Then("환불 불가능하다") {
                    orderPolicy.canRefundOrder(order) shouldBe false
                }
            }

            When("PREPARING 상태이면") {
                val order = OrderTestFixture.createOrder(status = OrderStatus.PREPARING)

                Then("환불 불가능하다") {
                    orderPolicy.canRefundOrder(order) shouldBe false
                }
            }

            When("SHIPPED 상태이면") {
                val order = OrderTestFixture.createOrder(status = OrderStatus.SHIPPED)

                Then("환불 불가능하다") {
                    orderPolicy.canRefundOrder(order) shouldBe false
                }
            }
        }

        // ===== validateProductsBelongToStore 테스트 =====
        Given("상품 스토어 소속 검증 시") {
            val storeId = UUID.randomUUID()

            When("모든 상품이 해당 스토어에 속하면") {
                val products = listOf(
                    ProductInfo(
                        id = UUID.randomUUID(),
                        storeId = storeId,
                        storeName = "Test Store",
                        name = "Product 1",
                        price = BigDecimal("10000"),
                    ),
                    ProductInfo(
                        id = UUID.randomUUID(),
                        storeId = storeId,
                        storeName = "Test Store",
                        name = "Product 2",
                        price = BigDecimal("20000"),
                    ),
                )

                Then("검증을 통과한다") {
                    orderPolicy.validateProductsBelongToStore(products, storeId)
                }
            }

            When("일부 상품이 다른 스토어에 속하면") {
                val otherStoreId = UUID.randomUUID()
                val products = listOf(
                    ProductInfo(
                        id = UUID.randomUUID(),
                        storeId = storeId,
                        storeName = "Test Store",
                        name = "Product 1",
                        price = BigDecimal("10000"),
                    ),
                    ProductInfo(
                        id = UUID.randomUUID(),
                        storeId = otherStoreId,
                        storeName = "Other Store",
                        name = "Product 2",
                        price = BigDecimal("20000"),
                    ),
                )

                Then("IllegalArgumentException이 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        orderPolicy.validateProductsBelongToStore(products, storeId)
                    }
                }
            }

            When("상품 목록이 비어있으면") {
                val products = emptyList<ProductInfo>()

                Then("검증을 통과한다") {
                    orderPolicy.validateProductsBelongToStore(products, storeId)
                }
            }
        }
    })
