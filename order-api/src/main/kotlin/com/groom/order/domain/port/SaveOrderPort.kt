package com.groom.order.domain.port

import com.groom.order.domain.model.Order

/**
 * Order 저장을 위한 Outbound Port
 *
 * Domain이 Order 영속성 계층에 요구하는 저장 계약입니다.
 */
interface SaveOrderPort {
    /**
     * 주문 저장
     *
     * @param order 저장할 주문
     * @return 저장된 주문
     */
    fun save(order: Order): Order

    /**
     * 여러 주문 일괄 저장
     *
     * @param orders 저장할 주문 목록
     * @return 저장된 주문 목록
     */
    fun saveAll(orders: List<Order>): List<Order>
}
