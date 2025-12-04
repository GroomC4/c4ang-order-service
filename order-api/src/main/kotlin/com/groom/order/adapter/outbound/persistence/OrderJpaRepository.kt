package com.groom.order.adapter.outbound.persistence

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

/**
 * Order JPA Repository
 *
 * Order 엔티티에 대한 JPA 인터페이스입니다.
 * 이 인터페이스는 Adapter 내부에서만 사용되며, Domain은 Port를 통해 접근합니다.
 */
interface OrderJpaRepository : JpaRepository<Order, UUID> {
    fun findByOrderNumber(orderNumber: String): Optional<Order>

    @Query(
        """
        SELECT o FROM Order o
        WHERE o.userExternalId = :userExternalId
        ORDER BY o.createdAt DESC
        """,
    )
    fun findByUserExternalId(
        @Param("userExternalId") userExternalId: UUID,
    ): List<Order>

    fun findByStoreId(storeId: UUID): List<Order>

    @Query(
        """
        SELECT o FROM Order o
        WHERE o.userExternalId = :userExternalId
        AND o.status = :status
        ORDER BY o.createdAt DESC
        """,
    )
    fun findByUserExternalIdAndStatus(
        @Param("userExternalId") userExternalId: UUID,
        @Param("status") status: OrderStatus,
    ): List<Order>

    fun existsByOrderNumber(orderNumber: String): Boolean

    /**
     * 만료된 주문 조회 (스케줄러용)
     * 특정 상태이면서 expiresAt이 지난 주문들을 조회
     */
    @Query(
        """
        SELECT o FROM Order o
        WHERE o.status IN :statuses
        AND o.expiresAt IS NOT NULL
        AND o.expiresAt < :expiredAt
        ORDER BY o.expiresAt ASC
        """,
    )
    fun findExpiredOrders(
        @Param("statuses") statuses: List<OrderStatus>,
        @Param("expiredAt") expiredAt: LocalDateTime,
    ): List<Order>

    /**
     * 기간 내 확정된 주문 조회 (일일 통계용)
     * confirmedAt이 해당 기간 내인 주문들을 조회
     */
    @Query(
        """
        SELECT o FROM Order o
        LEFT JOIN FETCH o.items
        WHERE o.confirmedAt >= :startDateTime
        AND o.confirmedAt < :endDateTime
        ORDER BY o.confirmedAt ASC
        """,
    )
    fun findConfirmedOrdersBetween(
        @Param("startDateTime") startDateTime: LocalDateTime,
        @Param("endDateTime") endDateTime: LocalDateTime,
    ): List<Order>
}
