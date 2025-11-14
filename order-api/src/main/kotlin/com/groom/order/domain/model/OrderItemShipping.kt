package com.groom.order.domain.model

import com.groom.order.configuration.jpa.CreatedAndUpdatedAtAuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * OrderItemShipping 엔티티.
 * DDL: p_order_item_shipping 테이블
 */
@Entity
@Table(name = "p_order_item_shipping")
class OrderItemShipping(
    @Column(nullable = false)
    val orderId: UUID,
    @Column(nullable = false)
    val productId: UUID,
    @Column
    val trackingNumber: String? = null,
    @Column
    val carrierCode: String? = null,
    @Column(nullable = false)
    val addressLine1: String,
    @Column
    val addressLine2: String? = null,
    @Column(nullable = false)
    val recipientName: String,
    @Column(nullable = false)
    val recipientPhone: String,
    @Column(nullable = false)
    val postalCode: String,
    @Column(nullable = false)
    var status: String = "PREPARING", // PREPARING, REQUESTED, IN_TRANSIT, DELIVERED
    @Column
    val shippedAt: LocalDateTime? = null,
    @Column
    val deliveredAt: LocalDateTime? = null,
    @Column
    val deletedAt: LocalDateTime? = null,
) : CreatedAndUpdatedAtAuditEntity() {
    @Id
    @Column(columnDefinition = "uuid", updatable = false)
    var id: UUID = UUID.randomUUID()

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    var orderItem: OrderItem? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderItemShipping) return false
        if (id == null || other.id == null) return false
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: System.identityHashCode(this)

    override fun toString(): String = "OrderItemShipping(id=$id, orderId=$orderId, status=$status, recipientName=$recipientName)"
}
