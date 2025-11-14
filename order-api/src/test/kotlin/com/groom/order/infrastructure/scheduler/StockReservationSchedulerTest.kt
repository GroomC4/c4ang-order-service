package com.groom.order.infrastructure.scheduler

import com.groom.order.common.annotation.UnitTest
import com.groom.order.infrastructure.stock.StockReservationService
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

@UnitTest
class StockReservationSchedulerTest :
    BehaviorSpec({
        isolationMode = IsolationMode.InstancePerLeaf

        Given("재고 예약 서비스가 정상 작동하는 경우") {
            val stockReservationService = mockk<StockReservationService>()
            val scheduler = StockReservationScheduler(stockReservationService)

            every { stockReservationService.processExpiredReservations(any()) } just runs

            When("스케줄러가 실행되면") {
                scheduler.processExpiredReservations()

                Then("재고 예약 서비스의 만료 처리 메서드가 호출된다") {
                    // 스케줄러가 외부 서비스를 호출했는지 검증 (필수)
                    verify(exactly = 1) { stockReservationService.processExpiredReservations(any()) }
                }
            }
        }

        Given("재고 예약 서비스에서 예외가 발생하는 경우") {
            val stockReservationService = mockk<StockReservationService>()
            val scheduler = StockReservationScheduler(stockReservationService)

            every { stockReservationService.processExpiredReservations(any()) } throws RuntimeException("Redis connection error")

            When("스케줄러가 실행되면") {
                // 예외가 발생해도 스케줄러는 정상 종료되어야 함
                scheduler.processExpiredReservations()

                Then("예외를 잡아서 로그만 남기고 스케줄러는 정상 종료된다") {
                    // 스케줄러가 서비스를 호출했는지만 검증
                    verify(exactly = 1) { stockReservationService.processExpiredReservations(any()) }
                }
            }
        }
    })
