package com.groom.order.application.service

import com.groom.order.application.dto.ListOrdersQuery
import com.groom.order.application.dto.ListOrdersResult
import com.groom.order.infrastructure.repository.OrderRepositoryImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 리스트 조회 서비스
 *
 * 사용자의 주문 목록을 조회합니다.
 * - 사용자 ID로 주문 목록 조회
 * - 선택적 상태 필터링 (status)
 * - 최신 주문이 먼저 표시되도록 정렬
 */
@Service
class ListOrdersService(
    private val orderRepository: OrderRepositoryImpl,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun listOrders(query: ListOrdersQuery): ListOrdersResult {
        logger.info { "Listing orders for user: ${query.requestUserId}, status filter: ${query.status}" }

        // 상태 필터링 여부에 따라 다른 조회
        val orders =
            if (query.status != null) {
                orderRepository.findByUserExternalIdAndStatus(
                    query.requestUserId,
                    query.status,
                )
            } else {
                orderRepository.findByUserExternalId(query.requestUserId)
            }

        logger.info { "Found ${orders.size} orders for user: ${query.requestUserId}" }

        return ListOrdersResult.from(orders)
    }
}
