package com.groom.order.adapter.outbound.scheduler

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventPublisher
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@DisplayName("DailyStatisticsScheduler 단위 테스트")
class DailyStatisticsSchedulerTest {
    private lateinit var loadOrderPort: LoadOrderPort
    private lateinit var orderEventPublisher: OrderEventPublisher
    private lateinit var scheduler: DailyStatisticsScheduler

    @BeforeEach
    fun setup() {
        loadOrderPort = mockk()
        orderEventPublisher = mockk(relaxed = true)
        scheduler = DailyStatisticsScheduler(loadOrderPort, orderEventPublisher)
    }

    private fun createTestOrder(
        confirmedAt: LocalDateTime = LocalDateTime.now(),
        items: List<Pair<UUID, Pair<String, Int>>> =
            listOf(
                UUID.randomUUID() to ("테스트 상품" to 1),
            ),
    ): Order {
        val order =
            Order(
                userExternalId = UUID.randomUUID(),
                storeId = UUID.randomUUID(),
                orderNumber = "ORD-${UUID.randomUUID().toString().take(8)}",
                status = OrderStatus.PREPARING,
                paymentSummary = mapOf("method" to "CARD"),
                timeline = emptyList(),
                confirmedAt = confirmedAt,
            )

        items.forEach { (productId, productInfo) ->
            val (productName, quantity) = productInfo
            order.addItem(
                OrderItem(
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    unitPrice = BigDecimal("10000"),
                ),
            )
        }

        return order
    }

    @Nested
    @DisplayName("aggregateAndPublishDailyStatistics")
    inner class AggregateAndPublishDailyStatisticsTest {
        @Test
        @DisplayName("전일 확정된 주문이 없으면 빈 통계를 발행한다")
        fun `전일 확정된 주문이 없으면 빈 통계를 발행한다`() {
            // given
            val targetDate = LocalDate.now().minusDays(1)
            val startDateTime = LocalDateTime.of(targetDate, LocalTime.MIN)
            val endDateTime = LocalDateTime.of(targetDate.plusDays(1), LocalTime.MIN)

            every {
                loadOrderPort.loadConfirmedOrdersBetween(startDateTime, endDateTime)
            } returns emptyList()

            val statisticsSlot = slot<OrderEventPublisher.DailyStatisticsData>()

            // when
            scheduler.aggregateAndPublishDailyStatistics()

            // then
            verify(exactly = 1) {
                orderEventPublisher.publishDailyStatistics(capture(statisticsSlot))
            }

            val statistics = statisticsSlot.captured
            statistics.date shouldBe targetDate
            statistics.totalOrders shouldBe 0
            statistics.totalSales shouldBe BigDecimal.ZERO
            statistics.avgOrderAmount shouldBe BigDecimal.ZERO
            statistics.topProducts shouldHaveSize 0
        }

        @Test
        @DisplayName("전일 확정된 주문 통계를 올바르게 집계한다")
        fun `전일 확정된 주문 통계를 올바르게 집계한다`() {
            // given
            val targetDate = LocalDate.now().minusDays(1)
            val startDateTime = LocalDateTime.of(targetDate, LocalTime.MIN)
            val endDateTime = LocalDateTime.of(targetDate.plusDays(1), LocalTime.MIN)

            val order1 =
                createTestOrder(
                    confirmedAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0)),
                    items = listOf(UUID.randomUUID() to ("상품 A" to 2)),
                )
            val order2 =
                createTestOrder(
                    confirmedAt = LocalDateTime.of(targetDate, LocalTime.of(14, 0)),
                    items = listOf(UUID.randomUUID() to ("상품 B" to 3)),
                )

            every {
                loadOrderPort.loadConfirmedOrdersBetween(startDateTime, endDateTime)
            } returns listOf(order1, order2)

            val statisticsSlot = slot<OrderEventPublisher.DailyStatisticsData>()

            // when
            scheduler.aggregateAndPublishDailyStatistics()

            // then
            verify(exactly = 1) {
                orderEventPublisher.publishDailyStatistics(capture(statisticsSlot))
            }

            val statistics = statisticsSlot.captured
            statistics.date shouldBe targetDate
            statistics.totalOrders shouldBe 2
            // order1: 10000 * 2 = 20000, order2: 10000 * 3 = 30000
            statistics.totalSales shouldBe BigDecimal("50000")
            // 50000 / 2 = 25000
            statistics.avgOrderAmount shouldBe BigDecimal("25000.00")
        }

        @Test
        @DisplayName("인기 상품 Top 5를 올바르게 집계한다")
        fun `인기 상품 Top 5를 올바르게 집계한다`() {
            // given
            val targetDate = LocalDate.now().minusDays(1)
            val startDateTime = LocalDateTime.of(targetDate, LocalTime.MIN)
            val endDateTime = LocalDateTime.of(targetDate.plusDays(1), LocalTime.MIN)

            val productId1 = UUID.randomUUID()
            val productId2 = UUID.randomUUID()
            val productId3 = UUID.randomUUID()

            // 같은 상품이 여러 주문에 포함된 경우
            val order1 =
                createTestOrder(
                    confirmedAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0)),
                    items =
                        listOf(
                            productId1 to ("인기 상품 1" to 5),
                            productId2 to ("인기 상품 2" to 3),
                        ),
                )
            val order2 =
                createTestOrder(
                    confirmedAt = LocalDateTime.of(targetDate, LocalTime.of(14, 0)),
                    items =
                        listOf(
                            productId1 to ("인기 상품 1" to 3),
                            productId3 to ("인기 상품 3" to 2),
                        ),
                )

            every {
                loadOrderPort.loadConfirmedOrdersBetween(startDateTime, endDateTime)
            } returns listOf(order1, order2)

            val statisticsSlot = slot<OrderEventPublisher.DailyStatisticsData>()

            // when
            scheduler.aggregateAndPublishDailyStatistics()

            // then
            verify(exactly = 1) {
                orderEventPublisher.publishDailyStatistics(capture(statisticsSlot))
            }

            val topProducts = statisticsSlot.captured.topProducts
            topProducts shouldHaveSize 3

            // productId1: 5 + 3 = 8, productId2: 3, productId3: 2
            topProducts[0].productId shouldBe productId1
            topProducts[0].totalSold shouldBe 8
            topProducts[1].productId shouldBe productId2
            topProducts[1].totalSold shouldBe 3
            topProducts[2].productId shouldBe productId3
            topProducts[2].totalSold shouldBe 2
        }

        @Test
        @DisplayName("상품이 6개 이상일 경우 Top 5만 반환한다")
        fun `상품이 6개 이상일 경우 Top 5만 반환한다`() {
            // given
            val targetDate = LocalDate.now().minusDays(1)
            val startDateTime = LocalDateTime.of(targetDate, LocalTime.MIN)
            val endDateTime = LocalDateTime.of(targetDate.plusDays(1), LocalTime.MIN)

            val products =
                (1..7).map { i ->
                    UUID.randomUUID() to ("상품 $i" to i)
                }

            val order =
                createTestOrder(
                    confirmedAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0)),
                    items = products,
                )

            every {
                loadOrderPort.loadConfirmedOrdersBetween(startDateTime, endDateTime)
            } returns listOf(order)

            val statisticsSlot = slot<OrderEventPublisher.DailyStatisticsData>()

            // when
            scheduler.aggregateAndPublishDailyStatistics()

            // then
            verify(exactly = 1) {
                orderEventPublisher.publishDailyStatistics(capture(statisticsSlot))
            }

            statisticsSlot.captured.topProducts shouldHaveSize 5
            // 상위 5개: 7, 6, 5, 4, 3
            statisticsSlot.captured.topProducts[0].totalSold shouldBe 7
            statisticsSlot.captured.topProducts[4].totalSold shouldBe 3
        }

        @Test
        @DisplayName("예외 발생 시 로그만 남기고 다음 실행을 기다린다")
        fun `예외 발생 시 로그만 남기고 다음 실행을 기다린다`() {
            // given
            val targetDate = LocalDate.now().minusDays(1)
            val startDateTime = LocalDateTime.of(targetDate, LocalTime.MIN)
            val endDateTime = LocalDateTime.of(targetDate.plusDays(1), LocalTime.MIN)

            every {
                loadOrderPort.loadConfirmedOrdersBetween(startDateTime, endDateTime)
            } throws RuntimeException("DB 연결 실패")

            // when & then - 예외가 발생해도 스케줄러가 중단되지 않아야 함
            scheduler.aggregateAndPublishDailyStatistics()

            verify(exactly = 0) {
                orderEventPublisher.publishDailyStatistics(any())
            }
        }
    }
}
