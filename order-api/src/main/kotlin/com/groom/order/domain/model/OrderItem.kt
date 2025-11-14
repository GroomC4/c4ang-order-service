package com.groom.order.domain.model

import com.groom.order.configuration.jpa.CreatedAndUpdatedAtAuditEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * OrderItem 엔티티.
 * DDL: p_order_item 테이블
 */
@Entity
@Table(name = "p_order_item")
class OrderItem(
    @Column(nullable = false)
    val productId: UUID,
    @Column(nullable = false)
    val productName: String,
    @Column(nullable = false)
    val quantity: Int,
    @Column(nullable = false, precision = 12, scale = 2)
    val unitPrice: BigDecimal,
) : CreatedAndUpdatedAtAuditEntity() {
    @Id
    @Column(columnDefinition = "uuid", updatable = false)
    var id: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null

    @OneToOne(mappedBy = "orderItem", cascade = [CascadeType.ALL], orphanRemoval = true)
    var shipping: OrderItemShipping? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderItem) return false
        if (id == null || other.id == null) return false
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: System.identityHashCode(this)

    override fun toString(): String = "OrderItem(id=$id, productName=$productName, quantity=$quantity, unitPrice=$unitPrice)"
}
