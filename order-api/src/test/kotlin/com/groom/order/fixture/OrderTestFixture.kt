package com.groom.order.fixture

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Order 엔티티 테스트 픽스처
 *
 * JPA Auditing으로 설정되는 createdAt/updatedAt 같은 필드를
 * 리플렉션으로 초기화하여 테스트용 Order 객체를 생성합니다.
 */
object OrderTestFixture {
    /**
     * 기본 Order 생성
     */
    fun createOrder(
        id: UUID = UUID.randomUUID(),
        userExternalId: UUID = UUID.randomUUID(),
        storeId: UUID = UUID.randomUUID(),
        orderNumber: String = "ORD-${UUID.randomUUID().toString().take(8).uppercase()}",
        status: OrderStatus = OrderStatus.PAYMENT_COMPLETED,
        reservationId: String? = null,
        paymentId: UUID? = null,
        expiresAt: LocalDateTime? = null,
        confirmedAt: LocalDateTime? = LocalDateTime.now(),
        cancelledAt: LocalDateTime? = null,
        failureReason: String? = null,
        refundId: String? = null,
        note: String? = null,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now(),
        items: List<OrderItem> = emptyList(),
    ): Order {
        val order =
            Order(
                userExternalId = userExternalId,
                storeId = storeId,
                orderNumber = orderNumber,
                status = status,
                paymentSummary = emptyMap(),
                timeline = emptyList(),
                note = note,
                reservationId = reservationId,
                paymentId = paymentId,
                expiresAt = expiresAt,
                confirmedAt = confirmedAt,
                cancelledAt = cancelledAt,
                failureReason = failureReason,
                refundId = refundId,
            )

        // 리플렉션으로 protected 필드 설정
        setField(order, "id", id)
        setField(order, "createdAt", createdAt)
        setField(order, "updatedAt", updatedAt)

        // 아이템 추가
        items.forEach { item ->
            order.addItem(item)
        }

        return order
    }

    /**
     * 재고 예약 완료 상태의 Order 생성
     */
    fun createStockReservedOrder(
        userExternalId: UUID = UUID.randomUUID(),
        storeId: UUID = UUID.randomUUID(),
        reservationId: String = "RES-${UUID.randomUUID().toString().take(8)}",
        items: List<OrderItem> = listOf(createOrderItem()),
    ): Order =
        createOrder(
            userExternalId = userExternalId,
            storeId = storeId,
            status = OrderStatus.STOCK_RESERVED,
            reservationId = reservationId,
            expiresAt = LocalDateTime.now().plusMinutes(10),
            items = items,
        )

    /**
     * 결제 대기 상태의 Order 생성
     */
    fun createPaymentPendingOrder(
        userExternalId: UUID = UUID.randomUUID(),
        storeId: UUID = UUID.randomUUID(),
        reservationId: String = "RES-${UUID.randomUUID().toString().take(8)}",
        items: List<OrderItem> = listOf(createOrderItem()),
    ): Order =
        createOrder(
            userExternalId = userExternalId,
            storeId = storeId,
            status = OrderStatus.PAYMENT_PENDING,
            reservationId = reservationId,
            items = items,
        )

    /**
     * 배송 완료 상태의 Order 생성 (환불 테스트용)
     */
    fun createDeliveredOrder(
        userExternalId: UUID = UUID.randomUUID(),
        storeId: UUID = UUID.randomUUID(),
        items: List<OrderItem> = listOf(createOrderItem()),
    ): Order =
        createOrder(
            userExternalId = userExternalId,
            storeId = storeId,
            status = OrderStatus.DELIVERED,
            paymentId = UUID.randomUUID(),
            confirmedAt = LocalDateTime.now().minusDays(3),
            items = items,
        )

    /**
     * 기본 OrderItem 생성
     */
    fun createOrderItem(
        productId: UUID = UUID.randomUUID(),
        productName: String = "Test Product",
        quantity: Int = 2,
        unitPrice: BigDecimal = BigDecimal("10000"),
    ): OrderItem =
        OrderItem(
            productId = productId,
            productName = productName,
            quantity = quantity,
            unitPrice = unitPrice,
        )

    /**
     * 리플렉션으로 필드 설정 (private/protected 필드 접근)
     */
    fun setField(
        target: Any,
        fieldName: String,
        value: Any?,
    ) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException("Field $fieldName not found in ${target.javaClass}")
    }
}
