package com.groom.order.application.service

import com.groom.order.common.domain.DomainEventPublisher
import com.groom.order.common.exception.OrderException
import com.groom.order.application.dto.RefundOrderCommand
import com.groom.order.application.dto.RefundOrderResult
import com.groom.order.domain.event.OrderRefundedEvent
import com.groom.order.domain.service.OrderManager
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * 주문 환불 서비스
 *
 * 애플리케이션 서비스의 책임:
 * 1. 트랜잭션 관리
 * 2. 인프라 계층 접근 (Repository, PG사 API 등)
 * 3. 도메인 서비스 오케스트레이션
 * 4. 도메인 이벤트 발행
 *
 * 비즈니스 로직은 도메인 계층(OrderManager, OrderPolicy)에 위임
 */
@Service
class RefundOrderService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val orderManager: OrderManager,
    private val domainEventPublisher: DomainEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun refundOrder(command: RefundOrderCommand): RefundOrderResult {
        // 1. 주문 조회
        val order =
            loadOrderPort.loadById(command.orderId)
                ?: throw OrderException.OrderNotFound(command.orderId)

        logger.info { "Processing refund for order: ${order.orderNumber}" }

        // 2. 환불 가능 여부 검증 (도메인 로직 위임)
        orderManager.validateRefund(order, command.requestUserId)

        // 3. 환불 금액 계산 (도메인 로직 사용)
        val refundAmount = order.calculateTotalAmount()

        logger.info { "Refund amount: $refundAmount for order: ${order.orderNumber}" }

        // 4. PG사 환불 요청 (향후 구현 - 현재는 mock)
        val pgRefundId = "REFUND-${UUID.randomUUID()}"

        // 5. 주문 환불 처리
        order.refund(pgRefundId, command.refundReason)

        // 6. 주문 저장 (JPA dirty checking)
        saveOrderPort.save(order)

        logger.info { "Order refunded successfully: ${order.orderNumber}, refundId: $pgRefundId" }

        // 7. 도메인 이벤트 발행
        val event =
            OrderRefundedEvent(
                orderId = order.id,
                orderNumber = order.orderNumber,
                userExternalId = order.userExternalId,
                refundAmount = refundAmount.toLong(),
                refundId = pgRefundId,
                refundReason = command.refundReason,
            )
        domainEventPublisher.publish(event)

        return RefundOrderResult.from(order, refundAmount)
    }
}
