package com.groom.order.infrastructure.repository

import com.groom.order.domain.model.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrderItemRepositoryImpl : JpaRepository<OrderItem, UUID> {
    fun findByOrder_Id(orderId: UUID): List<OrderItem>

    fun findByProductId(productId: UUID): List<OrderItem>
}
