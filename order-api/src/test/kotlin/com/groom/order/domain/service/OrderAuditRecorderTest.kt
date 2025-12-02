package com.groom.order.domain.service

import com.groom.order.common.annotation.UnitTest
import com.groom.order.domain.model.OrderAudit
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.domain.port.SaveOrderAuditPort
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

@UnitTest
class OrderAuditRecorderTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("주문 감사 로그 기록 시") {
            val saveOrderAuditPort = mockk<SaveOrderAuditPort>()
            val recorder = OrderAuditRecorder(saveOrderAuditPort)

            val orderId = UUID.randomUUID()
            val actorUserId = UUID.randomUUID()
            val eventType = OrderAuditEventType.ORDER_CREATED
            val changeSummary = "주문이 생성되었습니다."

            val auditSlot = slot<OrderAudit>()
            every { saveOrderAuditPort.save(capture(auditSlot)) } just runs

            When("기본 정보로 기록하면") {
                recorder.record(
                    orderId = orderId,
                    eventType = eventType,
                    changeSummary = changeSummary,
                )

                Then("감사 로그가 저장된다") {
                    verify(exactly = 1) { saveOrderAuditPort.save(any()) }

                    val savedAudit = auditSlot.captured
                    savedAudit.orderId shouldBe orderId
                    savedAudit.eventType shouldBe eventType
                    savedAudit.changeSummary shouldBe changeSummary
                    savedAudit.actorUserId shouldBe null
                    savedAudit.metadata shouldBe null
                    savedAudit.recordedAt shouldNotBe null
                }
            }

            When("사용자 정보와 함께 기록하면") {
                recorder.record(
                    orderId = orderId,
                    eventType = eventType,
                    changeSummary = changeSummary,
                    actorUserId = actorUserId,
                )

                Then("사용자 정보가 포함된 감사 로그가 저장된다") {
                    val savedAudit = auditSlot.captured
                    savedAudit.actorUserId shouldBe actorUserId
                }
            }

            When("메타데이터와 함께 기록하면") {
                val metadata = mapOf(
                    "orderNumber" to "ORD-12345",
                    "totalAmount" to 50000,
                )

                recorder.record(
                    orderId = orderId,
                    eventType = eventType,
                    changeSummary = changeSummary,
                    metadata = metadata,
                )

                Then("메타데이터가 포함된 감사 로그가 저장된다") {
                    val savedAudit = auditSlot.captured
                    savedAudit.metadata shouldBe metadata
                }
            }
        }

        Given("다양한 이벤트 타입으로 기록 시") {
            val saveOrderAuditPort = mockk<SaveOrderAuditPort>()
            val recorder = OrderAuditRecorder(saveOrderAuditPort)

            val orderId = UUID.randomUUID()
            val auditSlot = slot<OrderAudit>()
            every { saveOrderAuditPort.save(capture(auditSlot)) } just runs

            When("ORDER_CANCELLED 이벤트를 기록하면") {
                recorder.record(
                    orderId = orderId,
                    eventType = OrderAuditEventType.ORDER_CANCELLED,
                    changeSummary = "주문이 취소되었습니다.",
                )

                Then("취소 이벤트 타입으로 저장된다") {
                    auditSlot.captured.eventType shouldBe OrderAuditEventType.ORDER_CANCELLED
                }
            }

            When("PAYMENT_COMPLETED 이벤트를 기록하면") {
                recorder.record(
                    orderId = orderId,
                    eventType = OrderAuditEventType.PAYMENT_COMPLETED,
                    changeSummary = "결제가 완료되었습니다.",
                )

                Then("결제 완료 이벤트 타입으로 저장된다") {
                    auditSlot.captured.eventType shouldBe OrderAuditEventType.PAYMENT_COMPLETED
                }
            }

            When("ORDER_REFUNDED 이벤트를 기록하면") {
                recorder.record(
                    orderId = orderId,
                    eventType = OrderAuditEventType.ORDER_REFUNDED,
                    changeSummary = "주문이 환불되었습니다.",
                )

                Then("환불 이벤트 타입으로 저장된다") {
                    auditSlot.captured.eventType shouldBe OrderAuditEventType.ORDER_REFUNDED
                }
            }
        }

        Given("주문 아이템 단위 감사 로그 기록 시") {
            val saveOrderAuditPort = mockk<SaveOrderAuditPort>()
            val recorder = OrderAuditRecorder(saveOrderAuditPort)

            val orderId = UUID.randomUUID()
            val orderItemId = UUID.randomUUID()
            val eventType = OrderAuditEventType.ITEM_SHIPPED
            val changeSummary = "아이템이 배송되었습니다."

            val auditSlot = slot<OrderAudit>()
            every { saveOrderAuditPort.save(capture(auditSlot)) } just runs

            When("아이템 ID와 함께 기록하면") {
                recorder.recordItem(
                    orderId = orderId,
                    orderItemId = orderItemId,
                    eventType = eventType,
                    changeSummary = changeSummary,
                )

                Then("아이템 ID가 포함된 감사 로그가 저장된다") {
                    verify(exactly = 1) { saveOrderAuditPort.save(any()) }

                    val savedAudit = auditSlot.captured
                    savedAudit.orderId shouldBe orderId
                    savedAudit.orderItemId shouldBe orderItemId
                    savedAudit.eventType shouldBe eventType
                    savedAudit.changeSummary shouldBe changeSummary
                }
            }
        }

        Given("저장 중 예외가 발생하는 경우") {
            val saveOrderAuditPort = mockk<SaveOrderAuditPort>()
            val recorder = OrderAuditRecorder(saveOrderAuditPort)

            val orderId = UUID.randomUUID()

            every { saveOrderAuditPort.save(any()) } throws RuntimeException("DB 연결 실패")

            When("record 메서드를 호출하면") {
                Then("예외가 발생하지 않고 조용히 실패한다") {
                    // 예외가 발생하지 않아야 함 (내부에서 catch)
                    recorder.record(
                        orderId = orderId,
                        eventType = OrderAuditEventType.ORDER_CREATED,
                        changeSummary = "주문 생성",
                    )
                }
            }

            When("recordItem 메서드를 호출하면") {
                Then("예외가 발생하지 않고 조용히 실패한다") {
                    // 예외가 발생하지 않아야 함 (내부에서 catch)
                    recorder.recordItem(
                        orderId = orderId,
                        orderItemId = UUID.randomUUID(),
                        eventType = OrderAuditEventType.ITEM_SHIPPED,
                        changeSummary = "아이템 배송",
                    )
                }
            }
        }
    })
