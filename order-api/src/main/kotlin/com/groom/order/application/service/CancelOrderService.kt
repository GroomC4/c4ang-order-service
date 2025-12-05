package com.groom.order.application.service

import com.groom.order.application.dto.CancelOrderCommand
import com.groom.order.application.dto.CancelOrderResult
import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import com.groom.order.domain.service.OrderManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문 취소 서비스
 *
 * 이벤트 기반 아키텍처:
 * 1. 주문 취소 처리 (ORDER_CANCELLED 상태)
 * 2. OrderCancelledEvent 발행 → Kafka → Product Service
 * 3. Product Service에서 재고 복구
 *
 * Note: Order Service는 Product Service에 직접 접근하지 않습니다.
 * 재고 복구는 order.cancelled 이벤트를 통해 Product Service에서 처리합니다.
 */
@Service
class CancelOrderService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
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

        // 3. 주문 저장 (JPA dirty checking)
        saveOrderPort.save(order)

        logger.info { "Order cancelled successfully: ${order.orderNumber}" }

        // 4. 도메인 이벤트 발행 (order.cancelled → Product Service에서 재고 복구)
        domainEventPublisher.publish(cancelEvent)

        return CancelOrderResult.from(order)
    }
}
