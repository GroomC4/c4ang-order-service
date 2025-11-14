package com.groom.order.domain.model

import com.groom.ecommerce.configuration.jpa.CreatedAndUpdatedAtAuditEntity
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Order 애그리게이트 루트.
 * DDL: p_order 테이블
 *
 * 비동기 주문-결제 플로우:
 * PENDING → STOCK_RESERVED → PAYMENT_PENDING → PAYMENT_COMPLETED → PREPARING → SHIPPED → DELIVERED
 */
@Entity
@Table(name = "p_order")
class Order(
    @Column(name = "user_id", nullable = false) val userExternalId: UUID,
    @Column(name = "store_id", nullable = false) val storeId: UUID,
    @Column(name = "order_number", nullable = false, unique = true) val orderNumber: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: OrderStatus = OrderStatus.PENDING,
    @Type(JsonType::class) @Column(nullable = false, columnDefinition = "json") val paymentSummary: Map<String, Any>,
    @Type(JsonType::class) @Column(nullable = false, columnDefinition = "json") val timeline: List<Map<String, Any>>,
    @Column val note: String? = null,
    @Column val deletedAt: LocalDateTime? = null,
    // ===== 비동기 플로우 관련 필드 =====
    @Column(name = "reservation_id") var reservationId: String? = null,
    @Column(name = "payment_id") var paymentId: UUID? = null,
    @Column(name = "expires_at") var expiresAt: LocalDateTime? = null,
    @Column(name = "confirmed_at") var confirmedAt: LocalDateTime? = null,
    @Column(name = "cancelled_at") var cancelledAt: LocalDateTime? = null,
    @Column(name = "failure_reason") var failureReason: String? = null,
    @Column(name = "refund_id") var refundId: String? = null,
) : CreatedAndUpdatedAtAuditEntity() {
    @Id
    @Column(columnDefinition = "uuid", updatable = false)
    var id: UUID = UUID.randomUUID()

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<OrderItem> = mutableListOf()

    fun addItem(item: OrderItem) {
        items.add(item)
        item.order = this
    }

    /**
     * 주문 총액 계산
     */
    fun calculateTotalAmount(): BigDecimal =
        items.sumOf { item ->
            item.unitPrice.multiply(BigDecimal(item.quantity))
        }

    // ===== 비즈니스 메서드 (상태 전이 + 도메인 이벤트 발행) =====

    /**
     * 재고 예약 정보 설정 (주문 생성 시)
     * PENDING 상태 유지 (이벤트 핸들러가 STOCK_RESERVED로 변경)
     */
    fun reserveStock(
        reservationId: String,
        expiresAt: LocalDateTime,
    ) {
        this.reservationId = reservationId
        this.expiresAt = expiresAt
    }

    /**
     * 재고 예약 완료 처리 (이벤트 핸들러 전용)
     * PENDING → STOCK_RESERVED
     */
    fun markStockReserved() {
        require(status == OrderStatus.PENDING) { "Only PENDING orders can mark stock reserved" }
        this.status = OrderStatus.STOCK_RESERVED
    }

    /**
     * 결제 대기 상태로 전환
     * STOCK_RESERVED → PAYMENT_PENDING
     *
     * Payment 생성 시점에 호출되며 Order와 Payment를 연결합니다.
     */
    fun markPaymentPending(paymentId: UUID) {
        require(status == OrderStatus.STOCK_RESERVED) {
            "Only STOCK_RESERVED orders can mark payment pending"
        }

        this.status = OrderStatus.PAYMENT_PENDING
        this.paymentId = paymentId
    }

    /**
     * 결제 완료 처리
     * PAYMENT_PENDING → PAYMENT_COMPLETED
     */
    fun completePayment(paymentId: UUID) {
        require(status == OrderStatus.PAYMENT_PENDING) { "Only PAYMENT_PENDING orders can complete payment" }

        this.status = OrderStatus.PAYMENT_COMPLETED
        this.paymentId = paymentId
    }

    /**
     * 주문 확정 처리
     * PAYMENT_COMPLETED → PREPARING
     */
    fun confirmOrder(now: LocalDateTime = LocalDateTime.now()) {
        require(status == OrderStatus.PAYMENT_COMPLETED) { "Only PAYMENT_COMPLETED orders can be confirmed" }

        this.status = OrderStatus.PREPARING
        this.confirmedAt = now
    }

    /**
     * 주문 취소 처리
     * 결제 전/후 모두 가능
     */
    fun cancel(
        reason: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        require(status !in listOf(OrderStatus.DELIVERED, OrderStatus.SHIPPED)) {
            "Cannot cancel orders that are already shipped or delivered"
        }

        this.status = OrderStatus.ORDER_CANCELLED
        this.cancelledAt = now
        this.failureReason = reason
    }

    /**
     * 결제 타임아웃 처리
     * PAYMENT_PENDING → PAYMENT_TIMEOUT
     */
    fun timeout() {
        require(status in listOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PROCESSING)) {
            "Only PAYMENT_PENDING or PAYMENT_PROCESSING orders can timeout"
        }

        this.status = OrderStatus.PAYMENT_TIMEOUT
        this.failureReason = "Payment timeout after $expiresAt"
    }

    /**
     * 환불 처리
     * DELIVERED → REFUND_COMPLETED
     */
    fun refund(
        refundId: String,
        reason: String?,
    ) {
        require(status == OrderStatus.DELIVERED) { "Only DELIVERED orders can be refunded" }

        this.status = OrderStatus.REFUND_COMPLETED
        this.refundId = refundId
        this.failureReason = reason
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Order) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode() ?: System.identityHashCode(this)

    override fun toString(): String = "Order(id=$id, orderNumber=$orderNumber, userExternalId=$userExternalId, status=$status)"
}
