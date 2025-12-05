package com.groom.order.adapter.outbound.scheduler

import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.domain.event.OrderTimeoutEvent
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문 타임아웃 스케줄러
 *
 * 결제 대기 중인 주문 중 만료 시간(expiresAt)이 지난 주문을 자동으로 타임아웃 처리합니다.
 *
 * 이벤트 기반 플로우:
 * 1. 결제 대기/처리 중 상태의 주문 중 만료된 주문 조회
 * 2. 주문 타임아웃 처리 (상태 변경: PAYMENT_TIMEOUT)
 * 3. 도메인 이벤트 발행 (OrderTimeoutEvent)
 * 4. Product Service에서 order.timeout 이벤트 소비 후 재고 복구
 *
 * Note: 재고 복구는 이벤트를 통해 Product Service에서 처리합니다.
 *
 * 실행 주기: 1분마다 (cron = "0 * * * * *")
 */
@Component
class OrderTimeoutScheduler(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val domainEventPublisher: DomainEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 만료된 주문 자동 타임아웃 처리
     * 매분 00초에 실행
     *
     * Note: 분산 환경에서 중복 실행 방지가 필요한 경우 ShedLock 도입 고려
     */
    @Scheduled(cron = "0 * * * * *")
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
                    logger.info { "Processing timeout for order: ${order.orderNumber}" }

                    // 2-1. 주문 타임아웃 처리
                    order.timeout()

                    // 2-2. 주문 저장 (JPA dirty checking)
                    saveOrderPort.save(order)

                    // 2-3. 도메인 이벤트 발행 (order.timeout → Product Service에서 재고 복구)
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
