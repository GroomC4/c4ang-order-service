package com.groom.order.adapter.outbound.persistence

import com.groom.order.domain.model.ProductReservation
import com.groom.order.domain.model.StockReservationLog
import com.groom.order.domain.model.StockReservationStatus
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
 * 재고 예약 로그 JPA 엔티티
 */
@Entity
@Table(name = "p_stock_reservation_log")
class StockReservationLogJpaEntity(
    @Id
    @Column(name = "id")
    val id: UUID = UUID.randomUUID(),
    @Column(name = "reservation_id", nullable = false, unique = true)
    val reservationId: String,
    @Column(name = "order_id", nullable = false)
    val orderId: UUID,
    @Column(name = "store_id", nullable = false)
    val storeId: UUID,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "products", columnDefinition = "jsonb", nullable = false)
    val products: List<ProductReservationJson>,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: StockReservationStatus,
    @Column(name = "reserved_at", nullable = false)
    val reservedAt: LocalDateTime,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,
    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = null,
    @Column(name = "released_at")
    var releasedAt: LocalDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun from(domain: StockReservationLog): StockReservationLogJpaEntity =
            StockReservationLogJpaEntity(
                id = domain.id,
                reservationId = domain.reservationId,
                orderId = domain.orderId,
                storeId = domain.storeId,
                products = domain.products.map { ProductReservationJson(it.productId, it.quantity) },
                status = domain.status,
                reservedAt = domain.reservedAt,
                expiresAt = domain.expiresAt,
                confirmedAt = domain.confirmedAt,
                releasedAt = domain.releasedAt,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt,
            )
    }

    fun toDomain(): StockReservationLog =
        StockReservationLog(
            id = id,
            reservationId = reservationId,
            orderId = orderId,
            storeId = storeId,
            products = products.map { ProductReservation(it.productId, it.quantity) },
            status = status,
            reservedAt = reservedAt,
            expiresAt = expiresAt,
            confirmedAt = confirmedAt,
            releasedAt = releasedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StockReservationLogJpaEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "StockReservationLogJpaEntity(id=$id, reservationId=$reservationId, orderId=$orderId)"
}

/**
 * JSONB 직렬화를 위한 상품 예약 데이터
 */
data class ProductReservationJson(
    val productId: UUID,
    val quantity: Int,
)
