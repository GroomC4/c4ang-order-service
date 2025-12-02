package com.groom.order.domain.model

import com.groom.order.common.annotation.UnitTest
import com.groom.order.fixture.OrderTestFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class OrderTest :
    FunSpec({
        // ===== markStockReserved 테스트 =====
        test("markStockReserved should change status from PENDING to STOCK_RESERVED") {
            // Given: PENDING 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PENDING)

            // When: markStockReserved 호출
            order.markStockReserved()

            // Then: 상태가 STOCK_RESERVED로 변경
            order.status shouldBe OrderStatus.STOCK_RESERVED
        }

        test("markStockReserved should throw exception if order is not PENDING") {
            // Given: STOCK_RESERVED 상태의 Order
            val order = OrderTestFixture.createStockReservedOrder()

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.markStockReserved()
                }

            exception.message shouldBe "Only PENDING orders can mark stock reserved"
        }

        // ===== markPaymentPending 테스트 =====
        test("markPaymentPending should set paymentId and change status to PAYMENT_PENDING") {
            // Given: STOCK_RESERVED 상태의 Order
            val order = OrderTestFixture.createStockReservedOrder()

            val paymentId = UUID.randomUUID()

            // When: markPaymentPending 호출
            order.markPaymentPending(paymentId)

            // Then: Payment ID가 설정되고 상태가 PAYMENT_PENDING으로 변경
            order.paymentId shouldBe paymentId
            order.status shouldBe OrderStatus.PAYMENT_PENDING
        }

        test("markPaymentPending should throw exception if order is not STOCK_RESERVED") {
            // Given: PENDING 상태의 Order
            val order =
                OrderTestFixture.createOrder(
                    status = OrderStatus.PENDING,
                )

            val paymentId = UUID.randomUUID()

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.markPaymentPending(paymentId)
                }

            exception.message shouldBe "Only STOCK_RESERVED orders can mark payment pending"
        }

        test("markPaymentPending should throw exception if order is PAYMENT_COMPLETED") {
            // Given: PAYMENT_COMPLETED 상태의 Order (Fixture 사용)
            val order =
                OrderTestFixture.createOrder(
                    status = OrderStatus.PAYMENT_COMPLETED,
                )

            val newPaymentId = UUID.randomUUID()

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.markPaymentPending(newPaymentId)
                }

            exception.message shouldBe "Only STOCK_RESERVED orders can mark payment pending"
        }

        // ===== completePayment 테스트 =====
        test("completePayment should change status from PAYMENT_PENDING to PAYMENT_COMPLETED") {
            // Given: PAYMENT_PENDING 상태의 Order
            val order = OrderTestFixture.createPaymentPendingOrder()
            val paymentId = UUID.randomUUID()

            // When: completePayment 호출
            order.completePayment(paymentId)

            // Then: 상태가 PAYMENT_COMPLETED로 변경
            order.status shouldBe OrderStatus.PAYMENT_COMPLETED
            order.paymentId shouldBe paymentId
        }

        test("completePayment should throw exception if order is not PAYMENT_PENDING") {
            // Given: STOCK_RESERVED 상태의 Order
            val order = OrderTestFixture.createStockReservedOrder()
            val paymentId = UUID.randomUUID()

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.completePayment(paymentId)
                }

            exception.message shouldBe "Only PAYMENT_PENDING orders can complete payment"
        }

        // ===== confirmOrder 테스트 =====
        test("confirmOrder should change status from PAYMENT_COMPLETED to PREPARING") {
            // Given: PAYMENT_COMPLETED 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PAYMENT_COMPLETED)
            val now = LocalDateTime.now()

            // When: confirmOrder 호출
            order.confirmOrder(now)

            // Then: 상태가 PREPARING으로 변경되고 confirmedAt이 설정됨
            order.status shouldBe OrderStatus.PREPARING
            order.confirmedAt shouldBe now
        }

        test("confirmOrder should throw exception if order is not PAYMENT_COMPLETED") {
            // Given: PENDING 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PENDING)

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.confirmOrder()
                }

            exception.message shouldBe "Only PAYMENT_COMPLETED orders can be confirmed"
        }

        // ===== cancel 테스트 =====
        test("cancel should change status to ORDER_CANCELLED for PENDING order") {
            // Given: PENDING 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PENDING)
            val reason = "고객 변심"
            val now = LocalDateTime.now()

            // When: cancel 호출
            order.cancel(reason, now)

            // Then: 상태가 ORDER_CANCELLED로 변경
            order.status shouldBe OrderStatus.ORDER_CANCELLED
            order.cancelledAt shouldBe now
            order.failureReason shouldBe reason
        }

        test("cancel should change status to ORDER_CANCELLED for PAYMENT_PENDING order") {
            // Given: PAYMENT_PENDING 상태의 Order
            val order = OrderTestFixture.createPaymentPendingOrder()
            val reason = "결제 취소"
            val now = LocalDateTime.now()

            // When: cancel 호출
            order.cancel(reason, now)

            // Then: 상태가 ORDER_CANCELLED로 변경
            order.status shouldBe OrderStatus.ORDER_CANCELLED
            order.cancelledAt shouldBe now
            order.failureReason shouldBe reason
        }

        test("cancel should throw exception if order is SHIPPED") {
            // Given: SHIPPED 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.SHIPPED)

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.cancel("취소 요청")
                }

            exception.message shouldBe "Cannot cancel orders that are already shipped or delivered"
        }

        test("cancel should throw exception if order is DELIVERED") {
            // Given: DELIVERED 상태의 Order
            val order = OrderTestFixture.createDeliveredOrder()

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.cancel("취소 요청")
                }

            exception.message shouldBe "Cannot cancel orders that are already shipped or delivered"
        }

        // ===== timeout 테스트 =====
        test("timeout should change status from PAYMENT_PENDING to PAYMENT_TIMEOUT") {
            // Given: PAYMENT_PENDING 상태의 Order
            val order = OrderTestFixture.createPaymentPendingOrder()

            // When: timeout 호출
            order.timeout()

            // Then: 상태가 PAYMENT_TIMEOUT으로 변경
            order.status shouldBe OrderStatus.PAYMENT_TIMEOUT
            order.failureReason shouldNotBe null
        }

        test("timeout should throw exception if order is not PAYMENT_PENDING or PAYMENT_PROCESSING") {
            // Given: PENDING 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PENDING)

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.timeout()
                }

            exception.message shouldBe "Only PAYMENT_PENDING or PAYMENT_PROCESSING orders can timeout"
        }

        // ===== refund 테스트 =====
        test("refund should change status from DELIVERED to REFUND_COMPLETED") {
            // Given: DELIVERED 상태의 Order
            val order = OrderTestFixture.createDeliveredOrder()
            val refundId = "REFUND-12345"
            val reason = "상품 불량"

            // When: refund 호출
            order.refund(refundId, reason)

            // Then: 상태가 REFUND_COMPLETED로 변경
            order.status shouldBe OrderStatus.REFUND_COMPLETED
            order.refundId shouldBe refundId
            order.failureReason shouldBe reason
        }

        test("refund should throw exception if order is not DELIVERED") {
            // Given: PREPARING 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PREPARING)

            // When & Then: IllegalArgumentException 발생
            val exception =
                shouldThrow<IllegalArgumentException> {
                    order.refund("REFUND-123", "환불 요청")
                }

            exception.message shouldBe "Only DELIVERED orders can be refunded"
        }

        // ===== reserveStock 테스트 =====
        test("reserveStock should set reservationId and expiresAt") {
            // Given: PENDING 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PENDING)
            val reservationId = "RES-12345"
            val expiresAt = LocalDateTime.now().plusMinutes(10)

            // When: reserveStock 호출
            order.reserveStock(reservationId, expiresAt)

            // Then: reservationId와 expiresAt이 설정됨
            order.reservationId shouldBe reservationId
            order.expiresAt shouldBe expiresAt
        }

        // ===== calculateTotalAmount 테스트 =====
        test("calculateTotalAmount should return correct total for single item") {
            // Given: 단일 아이템이 있는 Order
            val item = OrderTestFixture.createOrderItem(
                quantity = 2,
                unitPrice = BigDecimal("10000"),
            )
            val order = OrderTestFixture.createOrder(items = listOf(item))

            // When: calculateTotalAmount 호출
            val totalAmount = order.calculateTotalAmount()

            // Then: 총액이 20000이어야 함
            totalAmount shouldBe BigDecimal("20000")
        }

        test("calculateTotalAmount should return correct total for multiple items") {
            // Given: 다중 아이템이 있는 Order
            val item1 = OrderTestFixture.createOrderItem(
                quantity = 2,
                unitPrice = BigDecimal("10000"),
            )
            val item2 = OrderTestFixture.createOrderItem(
                quantity = 3,
                unitPrice = BigDecimal("5000"),
            )
            val order = OrderTestFixture.createOrder(items = listOf(item1, item2))

            // When: calculateTotalAmount 호출
            val totalAmount = order.calculateTotalAmount()

            // Then: 총액이 35000이어야 함 (20000 + 15000)
            totalAmount shouldBe BigDecimal("35000")
        }

        test("calculateTotalAmount should return zero for empty items") {
            // Given: 아이템이 없는 Order
            val order = OrderTestFixture.createOrder(items = emptyList())

            // When: calculateTotalAmount 호출
            val totalAmount = order.calculateTotalAmount()

            // Then: 총액이 0이어야 함
            totalAmount shouldBe BigDecimal.ZERO
        }

        // ===== addItem 테스트 =====
        test("addItem should add item to order and set order reference") {
            // Given: 빈 Order
            val order = OrderTestFixture.createOrder(items = emptyList())
            val item = OrderTestFixture.createOrderItem()

            // When: addItem 호출
            order.addItem(item)

            // Then: 아이템이 추가되고 Order 참조가 설정됨
            order.items.size shouldBe 1
            order.items[0] shouldBe item
            item.order shouldBe order
        }

        // ===== 비동기 플로우 통합 테스트 =====
        test("markPaymentPending should work correctly in async order-payment flow") {
            // Given: 비동기 플로우 시뮬레이션 - STOCK_RESERVED 상태
            val order = OrderTestFixture.createStockReservedOrder()

            order.status shouldBe OrderStatus.STOCK_RESERVED
            order.paymentId shouldBe null

            // When 1: Payment 생성 및 연결 (OrderStockReservedEventHandler에서 수행)
            val paymentId = UUID.randomUUID()
            order.markPaymentPending(paymentId)

            // Then 1: PAYMENT_PENDING 상태로 전환
            order.status shouldBe OrderStatus.PAYMENT_PENDING
            order.paymentId shouldBe paymentId

            // When 2: 결제 완료
            order.completePayment(paymentId)

            // Then 2: PAYMENT_COMPLETED 상태로 전환
            order.status shouldBe OrderStatus.PAYMENT_COMPLETED
            order.paymentId shouldBe paymentId
        }

        test("full order lifecycle: PENDING -> STOCK_RESERVED -> PAYMENT_PENDING -> PAYMENT_COMPLETED -> PREPARING") {
            // Given: PENDING 상태의 Order
            val order = OrderTestFixture.createOrder(status = OrderStatus.PENDING)
            val reservationId = "RES-12345"
            val paymentId = UUID.randomUUID()
            val expiresAt = LocalDateTime.now().plusMinutes(10)

            // Step 1: 재고 예약 정보 설정
            order.reserveStock(reservationId, expiresAt)
            order.reservationId shouldBe reservationId

            // Step 2: 재고 예약 완료
            order.markStockReserved()
            order.status shouldBe OrderStatus.STOCK_RESERVED

            // Step 3: 결제 대기
            order.markPaymentPending(paymentId)
            order.status shouldBe OrderStatus.PAYMENT_PENDING

            // Step 4: 결제 완료
            order.completePayment(paymentId)
            order.status shouldBe OrderStatus.PAYMENT_COMPLETED

            // Step 5: 주문 확정
            order.confirmOrder()
            order.status shouldBe OrderStatus.PREPARING
            order.confirmedAt shouldNotBe null
        }
    })
