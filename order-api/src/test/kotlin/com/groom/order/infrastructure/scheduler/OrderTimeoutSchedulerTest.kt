package com.groom.order.infrastructure.scheduler

import com.groom.ecommerce.common.annotation.UnitTest
import com.groom.ecommerce.common.domain.DomainEventPublisher
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import com.groom.order.infrastructure.repository.OrderRepositoryImpl
import com.groom.order.infrastructure.stock.StockReservationService
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class OrderTimeoutSchedulerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("만료된 주문이 있는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val stockReservationService = mockk<StockReservationService>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val scheduler =
                OrderTimeoutScheduler(
                    orderRepository,
                    stockReservationService,
                    domainEventPublisher,
                )

            val now = LocalDateTime.now()
            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val reservationId = "test-reservation-123"

            val expiredOrder =
                Order(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-12345",
                    status = OrderStatus.PAYMENT_PENDING,
                    paymentSummary = emptyMap(),
                    timeline = emptyList(),
                ).apply {
                    id = orderId
                    this.reservationId = reservationId
                    this.expiresAt = now.minusMinutes(5) // 5분 전 만료
                }

            every {
                orderRepository.findExpiredOrders(
                    statuses = listOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PROCESSING),
                    expiredAt = any(),
                )
            } returns listOf(expiredOrder)
            every { stockReservationService.cancelReservation(reservationId) } just runs
            every { orderRepository.save(any<Order>()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just runs

            When("스케줄러가 실행되면") {
                scheduler.processExpiredOrders()

                Then("주문이 타임아웃 처리되고 재고가 복구된다") {
                    expiredOrder.status shouldBe OrderStatus.PAYMENT_TIMEOUT
                    expiredOrder.failureReason shouldNotBe null
                    expiredOrder.failureReason!! shouldStartWith "Payment timeout after"

                    verify(exactly = 1) {
                        orderRepository.findExpiredOrders(
                            statuses = listOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PROCESSING),
                            expiredAt = any(),
                        )
                    }
                    verify(exactly = 1) { stockReservationService.cancelReservation(reservationId) }
                    verify(exactly = 1) { orderRepository.save(expiredOrder) }
                    verify(exactly = 1) { domainEventPublisher.publish(any()) }
                }
            }
        }

        Given("만료된 주문이 없는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val stockReservationService = mockk<StockReservationService>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val scheduler =
                OrderTimeoutScheduler(
                    orderRepository,
                    stockReservationService,
                    domainEventPublisher,
                )

            every {
                orderRepository.findExpiredOrders(
                    statuses = listOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PROCESSING),
                    expiredAt = any(),
                )
            } returns emptyList()

            When("스케줄러가 실행되면") {
                scheduler.processExpiredOrders()

                Then("아무 처리도 하지 않는다") {
                    verify(exactly = 1) {
                        orderRepository.findExpiredOrders(
                            statuses = listOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PROCESSING),
                            expiredAt = any(),
                        )
                    }
                    verify(exactly = 0) { stockReservationService.cancelReservation(any()) }
                    verify(exactly = 0) { orderRepository.save(any<Order>()) }
                    verify(exactly = 0) { domainEventPublisher.publish(any()) }
                }
            }
        }

        Given("재고 복구가 실패하는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val stockReservationService = mockk<StockReservationService>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val scheduler =
                OrderTimeoutScheduler(
                    orderRepository,
                    stockReservationService,
                    domainEventPublisher,
                )

            val now = LocalDateTime.now()
            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val reservationId = "test-reservation-123"

            val expiredOrder =
                Order(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-12345",
                    status = OrderStatus.PAYMENT_PENDING,
                    paymentSummary = emptyMap(),
                    timeline = emptyList(),
                ).apply {
                    id = orderId
                    this.reservationId = reservationId
                    this.expiresAt = now.minusMinutes(5)
                }

            every {
                orderRepository.findExpiredOrders(
                    statuses = listOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PROCESSING),
                    expiredAt = any(),
                )
            } returns listOf(expiredOrder)
            every { stockReservationService.cancelReservation(reservationId) } throws RuntimeException("Redis connection error")
            every { orderRepository.save(any<Order>()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just runs

            When("스케줄러가 실행되면") {
                scheduler.processExpiredOrders()

                Then("재고 복구 실패에도 불구하고 주문 타임아웃 처리는 완료된다") {
                    expiredOrder.status shouldBe OrderStatus.PAYMENT_TIMEOUT

                    verify(exactly = 1) { stockReservationService.cancelReservation(reservationId) }
                    verify(exactly = 1) { orderRepository.save(expiredOrder) }
                    verify(exactly = 1) { domainEventPublisher.publish(any()) }
                }
            }
        }

        Given("reservationId가 없는 만료된 주문이 있는 경우") {
            val orderRepository = mockk<OrderRepositoryImpl>()
            val stockReservationService = mockk<StockReservationService>()
            val domainEventPublisher = mockk<DomainEventPublisher>()

            val scheduler =
                OrderTimeoutScheduler(
                    orderRepository,
                    stockReservationService,
                    domainEventPublisher,
                )

            val now = LocalDateTime.now()
            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val storeId = UUID.randomUUID()

            val expiredOrder =
                Order(
                    userExternalId = userId,
                    storeId = storeId,
                    orderNumber = "ORD-12345",
                    status = OrderStatus.PAYMENT_PENDING,
                    paymentSummary = emptyMap(),
                    timeline = emptyList(),
                ).apply {
                    id = orderId
                    this.reservationId = null // reservationId 없음
                    this.expiresAt = now.minusMinutes(5)
                }

            every {
                orderRepository.findExpiredOrders(
                    statuses = listOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PROCESSING),
                    expiredAt = any(),
                )
            } returns listOf(expiredOrder)
            every { orderRepository.save(any<Order>()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just runs

            When("스케줄러가 실행되면") {
                scheduler.processExpiredOrders()

                Then("재고 복구 없이 주문 타임아웃 처리만 완료된다") {
                    expiredOrder.status shouldBe OrderStatus.PAYMENT_TIMEOUT

                    verify(exactly = 0) { stockReservationService.cancelReservation(any()) }
                    verify(exactly = 1) { orderRepository.save(expiredOrder) }
                    verify(exactly = 1) { domainEventPublisher.publish(any()) }
                }
            }
        }
    })
