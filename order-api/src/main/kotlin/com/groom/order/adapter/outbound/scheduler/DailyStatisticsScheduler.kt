package com.groom.order.adapter.outbound.scheduler

import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 일일 주문 통계 스케줄러
 *
 * 매일 자정에 전일 주문 통계를 집계하여 Kafka로 발행합니다.
 *
 * 발행 이벤트:
 * - daily.statistics: Analytics Service가 리포트 생성에 활용
 *
 * 집계 항목:
 * - 총 주문 수
 * - 총 매출액
 * - 평균 주문 금액
 * - 인기 상품 Top 5
 *
 * 실행 주기: 매일 자정 (cron = "0 0 0 * * *")
 */
@Component
class DailyStatisticsScheduler(
    private val loadOrderPort: LoadOrderPort,
    private val orderEventPublisher: OrderEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val TOP_PRODUCTS_LIMIT = 5
    }

    /**
     * 전일 주문 통계 집계 및 발행
     * 매일 자정에 실행
     *
     * 분산 환경에서 중복 실행 방지:
     * - ShedLock을 통해 WAS 인스턴스 중 하나만 실행
     * - lockAtMostFor: 최대 30분 (대용량 데이터 처리 고려)
     * - lockAtLeastFor: 최소 5분 (너무 빈번한 실행 방지)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(
        name = "DailyStatisticsScheduler.aggregateAndPublishDailyStatistics",
        lockAtMostFor = "30m",
        lockAtLeastFor = "5m",
    )
    fun aggregateAndPublishDailyStatistics() {
        val targetDate = LocalDate.now().minusDays(1)
        val startDateTime = LocalDateTime.of(targetDate, LocalTime.MIN)
        val endDateTime = LocalDateTime.of(targetDate.plusDays(1), LocalTime.MIN)

        logger.info { "Starting daily statistics aggregation for date: $targetDate" }

        try {
            // 1. 전일 확정된 주문 조회
            val confirmedOrders = loadOrderPort.loadConfirmedOrdersBetween(startDateTime, endDateTime)

            if (confirmedOrders.isEmpty()) {
                logger.info { "No confirmed orders found for date: $targetDate" }
                publishEmptyStatistics(targetDate)
                return
            }

            logger.info { "Found ${confirmedOrders.size} confirmed orders for date: $targetDate" }

            // 2. 통계 집계
            val totalOrders = confirmedOrders.size
            val totalSales = confirmedOrders.sumOf { it.calculateTotalAmount() }
            val avgOrderAmount = totalSales.divide(BigDecimal(totalOrders), 2, RoundingMode.HALF_UP)

            // 3. 인기 상품 집계 (판매 수량 기준 Top 5)
            val topProducts = confirmedOrders
                .flatMap { it.items }
                .groupBy { it.productId to it.productName }
                .map { (productInfo, items) ->
                    OrderEventPublisher.TopProductData(
                        productId = productInfo.first,
                        productName = productInfo.second,
                        totalSold = items.sumOf { it.quantity },
                    )
                }
                .sortedByDescending { it.totalSold }
                .take(TOP_PRODUCTS_LIMIT)

            // 4. 통계 이벤트 발행
            val statisticsData = OrderEventPublisher.DailyStatisticsData(
                date = targetDate,
                totalOrders = totalOrders,
                totalSales = totalSales,
                avgOrderAmount = avgOrderAmount,
                topProducts = topProducts,
            )

            orderEventPublisher.publishDailyStatistics(statisticsData)

            logger.info {
                "Daily statistics published for date: $targetDate, " +
                    "totalOrders=$totalOrders, totalSales=$totalSales, avgOrderAmount=$avgOrderAmount"
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to aggregate and publish daily statistics for date: $targetDate" }
            // 스케줄러 실패는 로그만 남기고 다음 실행을 기다림
        }
    }

    private fun publishEmptyStatistics(targetDate: LocalDate) {
        val emptyStatistics = OrderEventPublisher.DailyStatisticsData(
            date = targetDate,
            totalOrders = 0,
            totalSales = BigDecimal.ZERO,
            avgOrderAmount = BigDecimal.ZERO,
            topProducts = emptyList(),
        )

        orderEventPublisher.publishDailyStatistics(emptyStatistics)

        logger.info { "Empty daily statistics published for date: $targetDate" }
    }
}
