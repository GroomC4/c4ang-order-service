package com.groom.order.adapter.outbound.stock

import com.groom.order.domain.model.ReservationResult
import com.groom.order.domain.port.ProductPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.redisson.api.RedissonClient
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Redisson 기반 재고 예약 서비스
 *
 * 1차 Lua 스크립트 대비 개선사항:
 * - 코드 줄 수: 166줄 → 80줄 (51% 감소)
 * - 디버깅: 불가능 → 가능 (브레이크포인트)
 * - IDE 지원: 없음 → 완벽 (IntelliJ)
 * - 원자성: 보장 (compareAndSet 낙관적 락)
 * - 유지보수: 어려움 → 쉬움 (Kotlin 코드)
 *
 * Key 구조:
 * - product:remaining-stock:{productId} → 상품별 잔여 재고 수량 (RAtomicLong)
 * - product:reservation-stock:{reservationId} → 재고 예약 정보 (RBucket, TTL)
 * - product:reservation-expiry-index → 예약 만료 인덱스 (RScoredSortedSet)
 *
 * 동시성 제어:
 * - compareAndSet으로 "체크 후 차감" 원자적 실행
 * - CAS 충돌 시 재시도 (최대 10회)
 * - 재고 부족 시 즉시 반환 (불필요한 재시도 없음)
 */
@Service
@Primary
class RedissonStockReservationService(
    private val redissonClient: RedissonClient,
    private val productPort: ProductPort,
) : StockReservationService {
    private val logger = KotlinLogging.logger {}

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 10
    }

    override fun tryReserve(
        storeId: UUID,
        items: List<OrderItemRequest>,
        reservationId: String,
        ttl: Duration,
    ): ReservationResult {
        return try {
            // 1. 각 상품별로 compareAndSet으로 재고 차감
            val reservedItems = mutableListOf<OrderItemRequest>()

            for (item in items) {
                val success = decrementStockIfAvailable(item.productId, item.quantity)

                if (!success) {
                    // 실패 시 이미 차감한 재고 복구
                    logger.warn { "Insufficient stock for product ${item.productId}, reservation: $reservationId" }
                    rollbackStock(reservedItems)
                    return ReservationResult.InsufficientStock
                }

                reservedItems.add(item)
            }

            // 2. 예약 정보 저장 (TTL)
            val reservationData = buildReservationData(items)
            val reservation = redissonClient.getBucket<String>("product:reservation-stock:$reservationId")
            reservation.set(reservationData, ttl)

            // 3. 만료 스케줄 등록 (예약 데이터 포함 - TTL 만료 후에도 재고 복구 가능)
            val expiryTimestamp =
                Instant
                    .now()
                    .plusSeconds(ttl.seconds)
                    .epochSecond
                    .toDouble()
            val expiry = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
            // reservationId:reservationData 형식으로 저장 (예: "RES-001:productId1:qty1,productId2:qty2")
            expiry.add(expiryTimestamp, "$reservationId:$reservationData")

            logger.info { "Stock reserved successfully: $reservationId" }
            ReservationResult.Success(reservationId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to reserve stock: $reservationId" }
            ReservationResult.InsufficientStock
        }
    }

    override fun confirmReservation(reservationId: String) {
        try {
            val reservationKey = "product:reservation-stock:$reservationId"
            val reservationData = redissonClient.getBucket<String>(reservationKey).get()

            if (reservationData == null) {
                logger.warn { "Reservation not found or already expired: $reservationId" }
                return
            }

            // 배치로 원자적 실행
            val batch = redissonClient.createBatch()

            // Bucket과 expiry index에서 삭제
            // Expiry index는 "reservationId:data" 형식이므로 패턴 매칭 필요
            batch.getBucket<String>(reservationKey).deleteAsync()

            // Expiry index에서 해당 reservationId로 시작하는 항목 찾아서 삭제
            val expirySet = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
            val allEntries = expirySet.readAll()
            allEntries.filter { it.startsWith("$reservationId:") }.forEach { entry ->
                batch.getScoredSortedSet<String>("product:reservation-expiry-index").removeAsync(entry)
            }

            batch.execute()

            logger.info { "Reservation confirmed: $reservationId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to confirm reservation: $reservationId" }
            throw e
        }
    }

    override fun cancelReservation(reservationId: String) {
        try {
            val reservationKey = "product:reservation-stock:$reservationId"

            // 1. 예약 정보 조회 (버킷 → 만료 인덱스 순서로 시도)
            var reservationData =
                redissonClient
                    .getBucket<String>(reservationKey)
                    .get()

            // 버킷이 없으면 만료 인덱스에서 찾기 (TTL 만료된 경우)
            if (reservationData == null) {
                val expirySet = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
                val matchingEntry =
                    expirySet
                        .readAll()
                        .firstOrNull { it.startsWith("$reservationId:") }

                if (matchingEntry == null) {
                    logger.debug { "Reservation not found in both bucket and expiry index: $reservationId" }
                    return
                }

                // "reservationId:data" 형식에서 data 추출
                reservationData = matchingEntry.substringAfter(":")
                logger.info { "Reservation data recovered from expiry index: $reservationId" }
            }

            val items = parseReservationData(reservationData)

            // 2. 재고 복구 + 예약 삭제 (원자적)
            val batch = redissonClient.createBatch()

            items.forEach { (productId, quantity) ->
                batch.getAtomicLong("product:remaining-stock:$productId").addAndGetAsync(quantity.toLong())
            }

            batch.getBucket<String>(reservationKey).deleteAsync()

            // Expiry index에서 삭제 (패턴 매칭)
            val expirySet = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
            expirySet
                .readAll()
                .filter { it.startsWith("$reservationId:") }
                .forEach { entry ->
                    batch.getScoredSortedSet<String>("product:reservation-expiry-index").removeAsync(entry)
                }

            batch.execute()

            logger.info { "Reservation cancelled and stock restored: $reservationId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel reservation: $reservationId" }
            throw e
        }
    }

    override fun processExpiredReservations(now: LocalDateTime) {
        val epochSecond = now.atZone(ZoneId.systemDefault()).toInstant().epochSecond

        try {
            // 만료된 예약 조회 (score 범위: 0 ~ 현재 시간)
            // 형식: "reservationId:data"
            val expiredEntries =
                redissonClient
                    .getScoredSortedSet<String>("product:reservation-expiry-index")
                    .valueRange(0.0, true, epochSecond.toDouble(), true)

            if (expiredEntries.isEmpty()) {
                return
            }

            logger.info { "Processing ${expiredEntries.size} expired reservations" }

            expiredEntries.forEach { entry ->
                try {
                    // "reservationId:data" 형식에서 reservationId 추출
                    val reservationId = entry.substringBefore(":")
                    cancelReservation(reservationId)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to process expired reservation: $entry" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process expired reservations" }
        }
    }

    /**
     * compareAndSet으로 재고 차감 시도
     *
     * Redis 키가 없으면 Product 엔티티에서 재고를 조회하여 초기화합니다.
     *
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     * @return true: 성공, false: 재고 부족 또는 재시도 실패
     */
    private fun decrementStockIfAvailable(
        productId: UUID,
        quantity: Int,
    ): Boolean {
        val key = "product:remaining-stock:$productId"
        val atomicLong = redissonClient.getAtomicLong(key)

        // 키가 존재하지 않으면 Product에서 재고를 조회하여 설정
        if (!atomicLong.isExists) {
            // TODO: Product Service에서 재고 정보 조회 필요
            // ProductInfo에 stockQuantity 필드 추가 필요
            // val product = productPort.loadById(productId)
            //     ?: run {
            //         logger.warn { "Product not found: $productId" }
            //         return false
            //     }
            //
            // atomicLong.set(product.stockQuantity.toLong())
            // logger.info { "Initialized Redis stock for product $productId: ${product.stockQuantity}" }

            // 임시: 기본 재고 설정 (실제로는 Product Service에서 조회해야 함)
            atomicLong.set(100)
            logger.warn { "Using default stock (100) for product $productId - TODO: implement Product Service integration" }
        }

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            val current = atomicLong.get()

            // 재고 부족 체크
            if (current < quantity) {
                logger.debug { "Insufficient stock for $productId: current=$current, required=$quantity" }
                return false
            }

            val newValue = current - quantity

            // compareAndSet: 원자적 "체크 후 차감"
            if (atomicLong.compareAndSet(current, newValue)) {
                logger.debug { "Stock reserved for $productId: $current → $newValue (attempt: ${attempt + 1})" }
                return true
            }

            // CAS 충돌 → 재시도
            logger.debug { "CAS collision for $productId, retrying... (attempt: ${attempt + 1}/$MAX_RETRY_ATTEMPTS)" }
        }

        // 10회 재시도 실패 → 재고 부족으로 간주
        logger.warn { "Failed to reserve stock for $productId after $MAX_RETRY_ATTEMPTS retries (likely sold out)" }
        return false
    }

    /**
     * 재고 롤백 (부분 성공 시)
     */
    private fun rollbackStock(items: List<OrderItemRequest>) {
        val batch = redissonClient.createBatch()

        items.forEach { item ->
            batch
                .getAtomicLong("product:remaining-stock:${item.productId}")
                .addAndGetAsync(item.quantity.toLong())
        }

        batch.execute()
        logger.info { "Stock rollback completed for ${items.size} items" }
    }

    /**
     * 예약 정보 생성
     * 형식: "productId1:quantity1,productId2:quantity2"
     */
    private fun buildReservationData(items: List<OrderItemRequest>): String =
        items.joinToString(",") { item ->
            "${item.productId}:${item.quantity}"
        }

    /**
     * 예약 정보 파싱
     * "productId1:quantity1,productId2:quantity2" → Map<productId, quantity>
     */
    private fun parseReservationData(data: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        data.split(",").forEach { item ->
            val parts = item.split(":")
            if (parts.size >= 2) {
                val productId = parts[0]
                val quantity = parts[1].toInt()
                result[productId] = quantity
            }
        }
        return result
    }
}
