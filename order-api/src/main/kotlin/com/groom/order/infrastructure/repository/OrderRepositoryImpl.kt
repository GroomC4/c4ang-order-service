package com.groom.order.infrastructure.repository

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@Repository
interface OrderRepositoryImpl : JpaRepository<Order, UUID> {
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
}
