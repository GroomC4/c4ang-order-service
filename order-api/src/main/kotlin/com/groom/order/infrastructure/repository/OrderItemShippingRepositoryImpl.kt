package com.groom.order.infrastructure.repository

import com.groom.order.domain.model.OrderItemShipping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface OrderItemShippingRepositoryImpl : JpaRepository<OrderItemShipping, UUID> {
    fun findByOrderId(orderId: UUID): List<OrderItemShipping>

    fun findByOrderItem_Id(orderItemId: UUID): Optional<OrderItemShipping>

    fun findByStatus(status: String): List<OrderItemShipping>

    fun findByTrackingNumber(trackingNumber: String): Optional<OrderItemShipping>
}
