package com.groom.order.application.event

import com.groom.order.domain.event.OrderCreatedEvent
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.OrderEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 생성 이벤트 Kafka 발행 핸들러
 *
 * OrderCreatedEvent 발생 시 Kafka로 order.created 이벤트를 발행합니다.
 * Product Service가 이 이벤트를 구독하여 재고 예약 처리를 수행합니다.
 *
 * @see OrderCreatedEventHandler 감사 로그 기록 핸들러 (별도)
 */
@Component
class OrderCreatedKafkaHandler(
    private val loadOrderPort: LoadOrderPort,
    private val orderEventPublisher: OrderEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        logger.info { "Publishing OrderCreated to Kafka: orderId=${event.orderId}" }

        val order = loadOrderPort.loadById(event.orderId)
        if (order == null) {
            logger.error { "Order not found for Kafka publishing: orderId=${event.orderId}" }
            return
        }

        orderEventPublisher.publishOrderCreated(order)

        logger.debug { "OrderCreated Kafka event published: orderId=${event.orderId}" }
    }
}
