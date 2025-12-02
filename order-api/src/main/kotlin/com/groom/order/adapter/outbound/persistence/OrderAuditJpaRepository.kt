package com.groom.order.adapter.outbound.persistence

import com.groom.order.domain.model.OrderAudit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * OrderAudit JPA Repository
 *
 * OrderAudit 엔티티에 대한 JPA 인터페이스입니다.
 */
interface OrderAuditJpaRepository : JpaRepository<OrderAudit, UUID>
