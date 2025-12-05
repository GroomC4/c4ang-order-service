package com.groom.order.application.event

import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.event.ReservedProduct
import com.groom.order.domain.event.StockReservedEvent
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.model.StockReservationLog
import com.groom.order.domain.port.SaveStockReservationLogPort
import com.groom.order.domain.service.OrderAuditRecorder
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID

@UnitTest
class StockReservedEventHandlerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("재고 예약 이벤트가 발생했을 때") {
            val saveStockReservationLogPort = mockk<SaveStockReservationLogPort>()
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = StockReservedEventHandler(saveStockReservationLogPort, orderAuditRecorder)

            val orderId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val productId = UUID.randomUUID()
            val reservationId = "RES-12345"
            val expiresAt = LocalDateTime.now().plusMinutes(10)
            val occurredAt = LocalDateTime.now()

            val event =
                StockReservedEvent(
                    orderId = orderId,
                    orderNumber = "ORD-20251202-ABC123",
                    reservationId = reservationId,
                    storeId = storeId,
                    products =
                        listOf(
                            ReservedProduct(productId = productId, quantity = 2),
                        ),
                    expiresAt = expiresAt,
                )

            val logSlot = slot<StockReservationLog>()
            every { saveStockReservationLogPort.save(capture(logSlot)) } answers { logSlot.captured }
            every {
                orderAuditRecorder.record(
                    orderId = any(),
                    eventType = any(),
                    changeSummary = any(),
                    actorUserId = any(),
                    metadata = any(),
                )
            } just runs

            When("이벤트를 처리하면") {
                handler.handleStockReserved(event)

                Then("재고 예약 로그가 저장된다") {
                    verify(exactly = 1) { saveStockReservationLogPort.save(any()) }

                    val savedLog = logSlot.captured
                    savedLog.reservationId shouldBe reservationId
                    savedLog.orderId shouldBe orderId
                    savedLog.storeId shouldBe storeId
                    savedLog.products.size shouldBe 1
                    savedLog.products[0].productId shouldBe productId
                    savedLog.products[0].quantity shouldBe 2
                }

                Then("감사 로그가 기록된다") {
                    verify(exactly = 1) {
                        orderAuditRecorder.record(
                            orderId = orderId,
                            eventType = OrderAuditEventType.STOCK_RESERVED,
                            changeSummary = any(),
                            actorUserId = null,
                            metadata = any(),
                        )
                    }
                }
            }
        }

        Given("여러 상품의 재고 예약 이벤트가 발생했을 때") {
            val saveStockReservationLogPort = mockk<SaveStockReservationLogPort>()
            val orderAuditRecorder = mockk<OrderAuditRecorder>()
            val handler = StockReservedEventHandler(saveStockReservationLogPort, orderAuditRecorder)

            val orderId = UUID.randomUUID()
            val storeId = UUID.randomUUID()
            val productId1 = UUID.randomUUID()
            val productId2 = UUID.randomUUID()
            val reservationId = "RES-67890"

            val event =
                StockReservedEvent(
                    orderId = orderId,
                    orderNumber = "ORD-20251202-XYZ789",
                    reservationId = reservationId,
                    storeId = storeId,
                    products =
                        listOf(
                            ReservedProduct(productId = productId1, quantity = 3),
                            ReservedProduct(productId = productId2, quantity = 5),
                        ),
                    expiresAt = LocalDateTime.now().plusMinutes(15),
                )

            val logSlot = slot<StockReservationLog>()
            every { saveStockReservationLogPort.save(capture(logSlot)) } answers { logSlot.captured }
            every {
                orderAuditRecorder.record(
                    orderId = any(),
                    eventType = any(),
                    changeSummary = any(),
                    actorUserId = any(),
                    metadata = any(),
                )
            } just runs

            When("이벤트를 처리하면") {
                handler.handleStockReserved(event)

                Then("모든 상품의 예약 정보가 로그에 저장된다") {
                    val savedLog = logSlot.captured
                    savedLog.products.size shouldBe 2
                    savedLog.products[0].productId shouldBe productId1
                    savedLog.products[0].quantity shouldBe 3
                    savedLog.products[1].productId shouldBe productId2
                    savedLog.products[1].quantity shouldBe 5
                }
            }
        }
    })
