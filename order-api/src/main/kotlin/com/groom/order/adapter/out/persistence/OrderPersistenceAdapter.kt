package com.groom.order.adapter.out.persistence

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

/**
 * Order 영속성 Adapter
 *
 * Order Port를 구현하여 JPA를 통한 영속성 기능을 제공합니다.
 */
@Component
class OrderPersistenceAdapter(
    private val orderJpaRepository: OrderJpaRepository,
) : LoadOrderPort, SaveOrderPort {
    override fun loadById(id: UUID): Order? = orderJpaRepository.findById(id).orElse(null)

    override fun loadByOrderNumber(orderNumber: String): Order? = orderJpaRepository.findByOrderNumber(orderNumber).orElse(null)

    override fun loadByUserExternalId(userExternalId: UUID): List<Order> = orderJpaRepository.findByUserExternalId(userExternalId)

    override fun loadByUserExternalIdAndStatus(
        userExternalId: UUID,
        status: OrderStatus,
    ): List<Order> = orderJpaRepository.findByUserExternalIdAndStatus(userExternalId, status)

    override fun loadByStoreId(storeId: UUID): List<Order> = orderJpaRepository.findByStoreId(storeId)

    override fun loadExpiredOrders(
        statuses: List<OrderStatus>,
        expiredAt: LocalDateTime,
    ): List<Order> = orderJpaRepository.findExpiredOrders(statuses, expiredAt)

    override fun existsByOrderNumber(orderNumber: String): Boolean = orderJpaRepository.existsByOrderNumber(orderNumber)

    override fun save(order: Order): Order = orderJpaRepository.save(order)

    override fun saveAll(orders: List<Order>): List<Order> = orderJpaRepository.saveAll(orders)
}
