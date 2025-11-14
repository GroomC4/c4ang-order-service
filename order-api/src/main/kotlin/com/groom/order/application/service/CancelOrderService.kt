package com.groom.order.application.service

import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.application.dto.CancelOrderCommand
import com.groom.order.application.dto.CancelOrderResult
import com.groom.order.domain.service.OrderManager
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.infrastructure.stock.StockReservationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문 취소 서비스
 *
 * 애플리케이션 서비스의 책임:
 * 1. 트랜잭션 관리
 * 2. 인프라 계층 접근 (Repository, Redis 등)
 * 3. 도메인 서비스 오케스트레이션
 * 4. 도메인 이벤트 발행
 *
 * 비즈니스 로직은 도메인 계층(OrderManager, OrderPolicy)에 위임
 */
@Service
class CancelOrderService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val stockReservationService: StockReservationService,
    private val orderManager: OrderManager,
    private val domainEventPublisher: DomainEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun cancelOrder(
        command: CancelOrderCommand,
        now: LocalDateTime = LocalDateTime.now(),
    ): CancelOrderResult {
        // 1. 주문 조회
        val order =
            loadOrderPort.loadById(command.orderId)
                ?: throw OrderException.OrderNotFound(command.orderId)

        logger.info { "Cancelling order: ${order.orderNumber}" }

        // 2. 주문 취소 처리 (도메인 서비스 사용 - 소유권 및 상태 검증 포함)
        val cancelEvent = orderManager.cancelOrder(order, command.requestUserId, command.cancelReason, now)

        // 3. 재고 복구 (예약이 있는 경우)
        if (order.reservationId != null) {
            try {
                stockReservationService.cancelReservation(order.reservationId!!)
                logger.info { "Stock reservation cancelled: ${order.reservationId}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to cancel stock reservation: ${order.reservationId}" }
                // 재고 복구 실패는 로그만 남기고 계속 진행 (수동 처리 필요)
            }
        }

        // 4. 주문 저장 (JPA dirty checking)
        saveOrderPort.save(order)

        logger.info { "Order cancelled successfully: ${order.orderNumber}" }

        // 5. 도메인 이벤트 발행
        domainEventPublisher.publish(cancelEvent)

        return CancelOrderResult.from(order)
    }
}
