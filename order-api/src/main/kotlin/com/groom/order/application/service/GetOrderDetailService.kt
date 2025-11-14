package com.groom.order.application.service

import com.groom.order.common.exception.OrderException
import com.groom.order.application.dto.GetOrderDetailQuery
import com.groom.order.application.dto.GetOrderDetailResult
import com.groom.order.domain.service.OrderPolicy
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 상세 조회 서비스
 *
 * 애플리케이션 서비스의 책임:
 * 1. 트랜잭션 관리
 * 2. 인프라 계층 접근 (Repository)
 * 3. 도메인 서비스 오케스트레이션
 *
 * 비즈니스 로직은 도메인 계층(OrderPolicy)에 위임
 */
@Service
class GetOrderDetailService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val orderPolicy: OrderPolicy,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun getOrderDetail(query: GetOrderDetailQuery): GetOrderDetailResult {
        // 1. 주문 조회 (인프라 접근)
        val order =
            orderRepository
                .findById(query.orderId)
                .orElseThrow { OrderException.OrderNotFound(query.orderId) }

        logger.info { "Retrieving order detail: ${order.orderNumber}" }

        // 2. 접근 권한 검증 (도메인 로직 위임)
        orderPolicy.checkOrderOwnership(order, query.requestUserId)

        // 3. 결과 반환
        return GetOrderDetailResult.from(order)
    }
}
