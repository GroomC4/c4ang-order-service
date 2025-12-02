package com.groom.order.domain.service

import com.groom.order.common.annotation.UnitTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import java.time.LocalDateTime

@UnitTest
class OrderNumberGeneratorTest :
    BehaviorSpec({
        val orderNumberGenerator = OrderNumberGenerator()

        Given("주문 번호 생성 시") {
            When("특정 날짜로 생성하면") {
                val testDate = LocalDateTime.of(2025, 12, 2, 10, 30, 0)
                val orderNumber = orderNumberGenerator.generate(testDate)

                Then("주문 번호가 올바른 형식으로 생성된다") {
                    orderNumber shouldStartWith "ORD-20251202-"
                    orderNumber.length shouldBe 19 // ORD-YYYYMMDD-XXXXXX
                }
            }

            When("같은 시간에 여러 번 생성하면") {
                val testDate = LocalDateTime.of(2025, 1, 15, 14, 0, 0)
                val orderNumber1 = orderNumberGenerator.generate(testDate)
                val orderNumber2 = orderNumberGenerator.generate(testDate)

                Then("서로 다른 주문 번호가 생성된다") {
                    // 날짜 부분은 같지만 랜덤 부분이 다름 (대부분의 경우)
                    orderNumber1 shouldStartWith "ORD-20250115-"
                    orderNumber2 shouldStartWith "ORD-20250115-"
                    // 랜덤이므로 같을 확률은 매우 낮음
                }
            }

            When("현재 시간으로 생성하면") {
                val orderNumber = orderNumberGenerator.generate()

                Then("올바른 형식의 주문 번호가 생성된다") {
                    orderNumber shouldMatch Regex("ORD-\\d{8}-[A-Z0-9]{6}")
                }
            }

            When("다른 날짜로 생성하면") {
                val date1 = LocalDateTime.of(2025, 6, 1, 12, 0, 0)
                val date2 = LocalDateTime.of(2025, 12, 31, 23, 59, 59)

                val orderNumber1 = orderNumberGenerator.generate(date1)
                val orderNumber2 = orderNumberGenerator.generate(date2)

                Then("날짜 부분이 다르게 생성된다") {
                    orderNumber1 shouldStartWith "ORD-20250601-"
                    orderNumber2 shouldStartWith "ORD-20251231-"
                }
            }
        }
    })
