package com.groom.order.domain.model

import com.groom.order.common.annotation.UnitTest
import com.groom.order.fixture.OrderTestFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class OrderTest :
    FunSpec({
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
    })
