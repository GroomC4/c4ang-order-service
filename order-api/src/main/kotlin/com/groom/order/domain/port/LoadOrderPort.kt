package com.groom.order.domain.port

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import java.time.LocalDateTime
import java.util.UUID

/**
 * Order 조회를 위한 Outbound Port
 *
 * Domain이 Order 영속성 계층에 요구하는 조회 계약입니다.
 */
interface LoadOrderPort {
    /**
     * ID로 주문 조회
     *
     * @param id 주문 ID
     * @return 주문 또는 null (존재하지 않을 경우)
     */
    fun loadById(id: UUID): Order?

    /**
     * 주문 번호로 주문 조회
     *
     * @param orderNumber 주문 번호
     * @return 주문 또는 null (존재하지 않을 경우)
     */
    fun loadByOrderNumber(orderNumber: String): Order?

    /**
     * 사용자 ID로 주문 목록 조회 (최신순)
     *
     * @param userExternalId 사용자 ID
     * @return 주문 목록
     */
    fun loadByUserExternalId(userExternalId: UUID): List<Order>

    /**
     * 사용자 ID와 상태로 주문 목록 조회 (최신순)
     *
     * @param userExternalId 사용자 ID
     * @param status 주문 상태
     * @return 주문 목록
     */
    fun loadByUserExternalIdAndStatus(
        userExternalId: UUID,
        status: OrderStatus,
    ): List<Order>

    /**
     * 스토어 ID로 주문 목록 조회
     *
     * @param storeId 스토어 ID
     * @return 주문 목록
     */
    fun loadByStoreId(storeId: UUID): List<Order>

    /**
     * 만료된 주문 조회
     *
     * @param statuses 확인할 주문 상태 목록
     * @param expiredAt 만료 기준 시각
     * @return 만료된 주문 목록
     */
    fun loadExpiredOrders(
        statuses: List<OrderStatus>,
        expiredAt: LocalDateTime,
    ): List<Order>

    /**
     * 주문 번호 존재 여부 확인
     *
     * @param orderNumber 주문 번호
     * @return 존재 여부
     */
    fun existsByOrderNumber(orderNumber: String): Boolean
}
