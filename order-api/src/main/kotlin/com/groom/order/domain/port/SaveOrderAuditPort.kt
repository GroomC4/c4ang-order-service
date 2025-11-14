package com.groom.order.domain.port

import com.groom.order.domain.model.OrderAudit

/**
 * OrderAudit 저장을 위한 Outbound Port
 *
 * Domain이 OrderAudit 영속성 계층에 요구하는 저장 계약입니다.
 */
interface SaveOrderAuditPort {
    /**
     * 주문 감사 로그 저장
     *
     * @param orderAudit 저장할 감사 로그
     * @return 저장된 감사 로그
     */
    fun save(orderAudit: OrderAudit): OrderAudit
}
