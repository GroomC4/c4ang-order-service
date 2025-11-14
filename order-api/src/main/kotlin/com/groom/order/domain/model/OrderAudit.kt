package com.groom.order.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 감사 로그 엔티티
 *
 * 주문 및 배송 상태 변화를 추적하는 감사 로그입니다.
 * 모든 주문 관련 도메인 이벤트가 발생하면 자동으로 기록됩니다.
 */
@Entity
@Table(name = "p_order_audit")
class OrderAudit(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),
    @Column(name = "order_id", nullable = false)
    val orderId: UUID,
    @Column(name = "order_item_id")
    val orderItemId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val eventType: OrderAuditEventType,
    @Column(name = "change_summary", columnDefinition = "TEXT")
    val changeSummary: String?,
    @Column(name = "actor_user_id")
    val actorUserId: UUID?,
    @Column(name = "recorded_at", nullable = false)
    val recordedAt: LocalDateTime = LocalDateTime.now(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderAudit) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "OrderAudit(id=$id, orderId=$orderId, eventType=$eventType)"
}

/**
 * 주문 감사 이벤트 타입
 */
enum class OrderAuditEventType {
    // 비동기 주문-결제 플로우 이벤트
    ORDER_CREATED,
    STOCK_RESERVED,
    PAYMENT_PENDING, // Payment 엔티티 생성 및 Order 연결
    PAYMENT_REQUESTED,
    PAYMENT_COMPLETED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ORDER_REFUNDED,
    ORDER_TIMEOUT,
}
