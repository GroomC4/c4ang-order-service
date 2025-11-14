package com.groom.order.domain.service

import com.groom.ecommerce.common.exception.OrderException
import com.groom.order.domain.model.ReservationResult
import com.groom.order.domain.model.StockReservation
import com.groom.order.domain.model.StockReservation.ReservationItem
import com.groom.order.infrastructure.stock.OrderItemRequest
import com.groom.order.infrastructure.stock.StockReservationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * 재고 예약 관리자 (도메인 서비스)
 *
 * 재고 예약 생성, 확정, 취소 등의 비즈니스 로직을 담당합니다.
 */
@Component
class StockReservationManager(
    private val stockReservationService: StockReservationService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 재고 예약 시도
     *
     * @param storeId 스토어 ID
     * @param items 주문 상품 정보 (productId, quantity)
     * @param expiresAt 만료 시각
     * @return 생성된 재고 예약
     */
    fun tryReserve(stockReservation: StockReservation): StockReservation {
        // 1. 인프라를 통한 실제 재고 예약 (Redis)
        val stockReservationItems =
            stockReservation.items.map { item ->
                OrderItemRequest(item.productId, item.quantity)
            }

        val ttl = Duration.between(LocalDateTime.now(), stockReservation.expiresAt)

        val result =
            stockReservationService.tryReserve(
                storeId = stockReservation.storeId,
                items = stockReservationItems,
                reservationId = stockReservation.reservationId,
                ttl = ttl,
            )

        // 3. 예약 결과 확인
        return when (result) {
            is ReservationResult.InsufficientStock -> {
                logger.warn { "Insufficient stock for reservation: ${stockReservation.reservationId}" }
                throw OrderException.InsufficientStock(stockReservation.storeId)
            }

            is ReservationResult.StoreClosed -> {
                logger.warn { "Store closed for reservation: ${stockReservation.reservationId}" }
                throw OrderException.StoreClosed(stockReservation.storeId)
            }

            is ReservationResult.Success -> {
                logger.info { "Stock reserved successfully: ${stockReservation.reservationId}" }
                stockReservation
            }
        }
    }

    /**
     * 재고 예약 요청 항목
     */
    data class ReservationItemRequest(
        val productId: UUID,
        val quantity: Int,
    )

    /**
     * 재고 예약 확정 (결제 완료 시)
     *
     * @param reservationId 예약 ID
     */
    fun confirmReservation(reservationId: String) {
        logger.info { "Confirming stock reservation: $reservationId" }
        stockReservationService.confirmReservation(reservationId)
    }

    /**
     * 재고 예약 취소 (주문 취소 또는 타임아웃)
     *
     * @param reservationId 예약 ID
     */
    fun cancelReservation(reservationId: String) {
        logger.info { "Cancelling stock reservation: $reservationId" }
        stockReservationService.cancelReservation(reservationId)
    }

    fun generateStockReservation(
        storeId: UUID,
        items: List<ReservationItemRequest>,
        now: LocalDateTime,
    ): StockReservation {
        require(items.isNotEmpty()) { "재고예약할 대상이 없습니다" }

        // 1. 재고 예약 도메인 객체 생성
        val reservationItems =
            items.map { item ->
                ReservationItem(
                    productId = item.productId,
                    quantity = item.quantity,
                )
            }

        val reservationId = "RSV-${UUID.randomUUID()}"
        return StockReservation(
            reservationId = reservationId,
            storeId = storeId,
            items = reservationItems,
            expiresAt = now.plusMinutes(10),
        )
    }
}
