package com.groom.order.infrastructure.scheduler

import com.groom.order.infrastructure.stock.StockReservationService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 재고 예약 만료 처리 스케줄러
 *
 * Redis에 저장된 만료된 재고 예약을 자동으로 복구합니다.
 *
 * 처리 프로세스:
 * 1. Redis에서 TTL이 만료된 예약 키 조회
 * 2. 해당 예약의 재고 복구 (decrement된 수량을 다시 increment)
 * 3. 예약 관련 Redis 키 삭제
 *
 * 실행 주기: 5분마다 (cron = "0 *\/5 * * * *")
 */
@Component
class StockReservationScheduler(
    private val stockReservationService: StockReservationService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 만료된 재고 예약 자동 복구
     * 매 5분마다 실행
     *
     * 분산 환경에서 중복 실행 방지:
     * - ShedLock을 통해 WAS 인스턴스 중 하나만 실행
     * - lockAtMostFor: 최대 9분 (비정상 종료 시 자동 해제)
     * - lockAtLeastFor: 최소 30초 (너무 빈번한 실행 방지)
     */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(
        name = "StockReservationScheduler.processExpiredReservations",
        lockAtMostFor = "9m",
        lockAtLeastFor = "30s",
    )
    fun processExpiredReservations() {
        val now = LocalDateTime.now()
        logger.info { "Starting expired stock reservation processing at $now" }

        try {
            stockReservationService.processExpiredReservations(now)
            logger.info { "Expired stock reservation processing completed" }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during expired stock reservation processing" }
            // 스케줄러 전체 실패는 로그만 남기고 다음 실행을 기다림
        }
    }
}
