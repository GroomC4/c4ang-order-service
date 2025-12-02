package com.groom.order.adapter.outbound.persistence

import com.groom.order.domain.model.OrderAudit
import com.groom.order.domain.port.SaveOrderAuditPort
import org.springframework.stereotype.Component

/**
 * OrderAudit 영속성 Adapter
 *
 * SaveOrderAuditPort를 구현하여 감사 로그 저장 기능을 제공합니다.
 */
@Component
class OrderAuditPersistenceAdapter(
    private val orderAuditJpaRepository: OrderAuditJpaRepository,
) : SaveOrderAuditPort {
    override fun save(orderAudit: OrderAudit): OrderAudit = orderAuditJpaRepository.save(orderAudit)
}
