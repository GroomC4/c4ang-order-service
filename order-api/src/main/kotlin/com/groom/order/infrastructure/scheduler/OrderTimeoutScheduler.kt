package com.groom.order.infrastructure.scheduler

import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.domain.event.OrderTimeoutEvent
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.infrastructure.stock.StockReservationService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문 타임아웃 스케줄러
 *
 * 결제 대기 중인 주문 중 만료 시간(expiresAt)이 지난 주문을 자동으로 타임아웃 처리합니다.
 *
 * 처리 프로세스:
 * 1. 결제 대기/처리 중 상태의 주문 중 만료된 주문 조회
 * 2. 주문 타임아웃 처리 (상태 변경: PAYMENT_TIMEOUT)
 * 3. Redis 재고 예약 복구 (reservationId 사용)
 * 4. 도메인 이벤트 발행 (OrderTimeoutEvent)
 *
 * 실행 주기: 1분마다 (cron = "0 * * * * *")
 */
@Component
class OrderTimeoutScheduler(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val stockReservationService: StockReservationService,
    private val domainEventPublisher: DomainEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 만료된 주문 자동 타임아웃 처리
     * 매분 00초에 실행
     *
     * 분산 환경에서 중복 실행 방지:
     * - ShedLock을 통해 WAS 인스턴스 중 하나만 실행
     * - lockAtMostFor: 최대 9분 (비정상 종료 시 자동 해제)
     * - lockAtLeastFor: 최소 30초 (너무 빈번한 실행 방지)
     */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(
        name = "OrderTimeoutScheduler.processExpiredOrders",
        lockAtMostFor = "9m",
        lockAtLeastFor = "30s",
    )
    @Transactional
    fun processExpiredOrders() {
        val now = LocalDateTime.now()
        logger.info { "Starting expired order processing at $now" }

        try {
            // 1. 결제 대기/처리 중 상태의 만료된 주문 조회
            val expiredOrders =
                loadOrderPort.loadExpiredOrders(
                    statuses =
                        listOf(
                            OrderStatus.PAYMENT_PENDING,
                            OrderStatus.PAYMENT_PROCESSING,
                        ),
                    expiredAt = now,
                )

            if (expiredOrders.isEmpty()) {
                logger.debug { "No expired orders found" }
                return
            }

            logger.info { "Found ${expiredOrders.size} expired orders to process" }

            // 2. 각 주문에 대해 타임아웃 처리
            expiredOrders.forEach { order ->
                try {
                    logger.info { "Processing timeout for order: ${order.orderNumber}, reservationId: ${order.reservationId}" }

                    // 2-1. 주문 타임아웃 처리
                    order.timeout()

                    // 2-2. Redis 재고 예약 복구
                    order.reservationId?.let { reservationId ->
                        try {
                            stockReservationService.cancelReservation(reservationId)
                            logger.info { "Stock reservation cancelled for order: ${order.orderNumber}" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to cancel stock reservation: $reservationId" }
                            // 재고 복구 실패는 로그만 남기고 계속 진행 (별도 모니터링 필요)
                        }
                    }

                    // 2-3. 주문 저장 (JPA dirty checking)
                    orderRepository.save(order)

                    // 2-4. 도메인 이벤트 발행
                    val event =
                        OrderTimeoutEvent(
                            orderId = order.id,
                            orderNumber = order.orderNumber,
                            storeId = order.storeId,
                            reservationId = order.reservationId,
                            paymentId = order.paymentId,
                            timeoutAt = now,
                        )
                    domainEventPublisher.publish(event)

                    logger.info { "Order timeout processed successfully: ${order.orderNumber}" }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to process timeout for order: ${order.orderNumber}" }
                    // 개별 주문 처리 실패는 로그만 남기고 다음 주문 처리 계속
                }
            }

            logger.info { "Expired order processing completed. Processed: ${expiredOrders.size}" }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during expired order processing" }
            // 스케줄러 전체 실패는 로그만 남기고 다음 실행을 기다림
        }
    }
}
