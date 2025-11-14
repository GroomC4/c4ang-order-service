package com.groom.order.infrastructure.repository

import com.groom.order.domain.model.OrderAudit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 주문 감사 로그 리포지토리
 */
@Repository
interface OrderAuditRepositoryImpl : JpaRepository<OrderAudit, UUID> {
    /**
     * 특정 주문의 모든 감사 로그 조회
     */
    fun findByOrderIdOrderByRecordedAtDesc(orderId: UUID): List<OrderAudit>
}
