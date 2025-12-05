# Product Service 이관 대상 코드 인덱스

> **목적**: Order Service에서 Product Service로 이관해야 할 로직들의 참조 문서
>
> **사용법**: Product Service 구현 시 이 문서를 프롬프트에 포함하여 기존 로직 참고

---

## 목차

1. [재고 예약 핵심 로직](#1-재고-예약-핵심-로직)
2. [재고 예약 도메인 모델](#2-재고-예약-도메인-모델)
3. [재고 예약 스케줄러](#3-재고-예약-스케줄러)
4. [상품 조회 인터페이스](#4-상품-조회-인터페이스)
5. [이벤트 핸들러](#5-이벤트-핸들러)
6. [Redis Key 구조](#6-redis-key-구조)

---

## 1. 재고 예약 핵심 로직

### 1.1 StockReservationService (인터페이스)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/adapter/outbound/stock/StockReservationService.kt`

**역할**: 재고 예약/확정/취소/만료 처리를 위한 인터페이스

**핵심 메서드**:
```kotlin
interface StockReservationService {
    // 재고 예약 시도
    fun tryReserve(
        storeId: UUID,
        items: List<OrderItemRequest>,
        reservationId: String,
        ttl: Duration = Duration.ofMinutes(10),
    ): ReservationResult

    // 예약 확정 (결제 완료 시)
    fun confirmReservation(reservationId: String)

    // 예약 취소 (주문 취소 또는 타임아웃)
    fun cancelReservation(reservationId: String)

    // 만료된 예약 자동 처리 (스케줄러 호출)
    fun processExpiredReservations(now: LocalDateTime = LocalDateTime.now())
}

data class OrderItemRequest(
    val productId: UUID,
    val quantity: Int,
)
```

---

### 1.2 RedissonStockReservationService (구현체)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/adapter/outbound/stock/RedissonStockReservationService.kt`

**역할**: Redisson 기반 분산 재고 예약 시스템

**핵심 알고리즘**:

#### tryReserve (재고 예약)
```kotlin
override fun tryReserve(storeId, items, reservationId, ttl): ReservationResult {
    // 1. 각 상품별로 compareAndSet으로 재고 차감
    for (item in items) {
        val success = decrementStockIfAvailable(item.productId, item.quantity)
        if (!success) {
            // 실패 시 이미 차감한 재고 복구 (롤백)
            rollbackStock(reservedItems)
            return ReservationResult.InsufficientStock
        }
        reservedItems.add(item)
    }

    // 2. 예약 정보 저장 (TTL 설정)
    // Key: product:reservation-stock:{reservationId}
    // Value: "productId1:quantity1,productId2:quantity2"
    val reservation = redissonClient.getBucket<String>("product:reservation-stock:$reservationId")
    reservation.set(reservationData, ttl)

    // 3. 만료 스케줄 등록 (SortedSet)
    // Key: product:reservation-expiry-index
    // Score: 만료 timestamp
    // Value: "reservationId:reservationData"
    val expiry = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
    expiry.add(expiryTimestamp, "$reservationId:$reservationData")

    return ReservationResult.Success(reservationId)
}
```

#### decrementStockIfAvailable (CAS 기반 재고 차감)
```kotlin
private fun decrementStockIfAvailable(productId: UUID, quantity: Int): Boolean {
    val key = "product:remaining-stock:$productId"
    val atomicLong = redissonClient.getAtomicLong(key)

    // 키가 없으면 초기화 (Product DB에서 조회 필요)
    if (!atomicLong.isExists) {
        // TODO: Product 엔티티에서 재고 조회하여 설정
        atomicLong.set(initialStock)
    }

    // CAS (Compare-And-Set) 재시도 루프
    repeat(MAX_RETRY_ATTEMPTS) { attempt ->
        val current = atomicLong.get()

        // 재고 부족 체크
        if (current < quantity) return false

        val newValue = current - quantity

        // 원자적 "체크 후 차감"
        if (atomicLong.compareAndSet(current, newValue)) {
            return true
        }
        // CAS 충돌 시 재시도
    }
    return false
}
```

#### confirmReservation (예약 확정)
```kotlin
override fun confirmReservation(reservationId: String) {
    // 1. 예약 버킷 삭제
    batch.getBucket<String>("product:reservation-stock:$reservationId").deleteAsync()

    // 2. 만료 인덱스에서 삭제
    expirySet.filter { it.startsWith("$reservationId:") }.forEach { entry ->
        batch.getScoredSortedSet<String>("product:reservation-expiry-index").removeAsync(entry)
    }

    batch.execute()
}
```

#### cancelReservation (예약 취소 + 재고 복구)
```kotlin
override fun cancelReservation(reservationId: String) {
    // 1. 예약 정보 조회 (버킷 → 만료 인덱스 순서로 시도)
    var reservationData = redissonClient.getBucket<String>(reservationKey).get()

    // 버킷이 없으면 만료 인덱스에서 찾기 (TTL 만료된 경우)
    if (reservationData == null) {
        reservationData = expirySet.firstOrNull { it.startsWith("$reservationId:") }
            ?.substringAfter(":")
    }

    // 2. 예약 데이터 파싱
    val items = parseReservationData(reservationData)

    // 3. 재고 복구 (원자적 배치)
    items.forEach { (productId, quantity) ->
        batch.getAtomicLong("product:remaining-stock:$productId").addAndGetAsync(quantity.toLong())
    }

    // 4. 예약 정보 삭제
    batch.getBucket<String>(reservationKey).deleteAsync()
    batch.execute()
}
```

#### processExpiredReservations (만료 예약 처리)
```kotlin
override fun processExpiredReservations(now: LocalDateTime) {
    val epochSecond = now.toEpochSecond()

    // 만료된 예약 조회 (score 범위: 0 ~ 현재 시간)
    val expiredEntries = redissonClient
        .getScoredSortedSet<String>("product:reservation-expiry-index")
        .valueRange(0.0, true, epochSecond.toDouble(), true)

    expiredEntries.forEach { entry ->
        val reservationId = entry.substringBefore(":")
        cancelReservation(reservationId)
    }
}
```

---

## 2. 재고 예약 도메인 모델

### 2.1 StockReservation (Value Object)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/domain/model/StockReservation.kt`

```kotlin
data class StockReservation(
    val reservationId: String,      // 예약 ID (예: "RSV-{UUID}")
    val storeId: UUID,              // 스토어 ID
    val items: List<ReservationItem>, // 예약 상품 목록
    val expiresAt: LocalDateTime,   // 만료 시각 (기본 10분)
) {
    data class ReservationItem(
        val productId: UUID,
        val quantity: Int,
    ) {
        init {
            require(quantity > 0) { "Reservation quantity must be positive" }
        }
    }
}
```

### 2.2 ReservationResult (Sealed Class)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/domain/model/ReservationResult.kt`

```kotlin
sealed class ReservationResult {
    data class Success(val reservationId: String) : ReservationResult()
    data object InsufficientStock : ReservationResult()
    data object StoreClosed : ReservationResult()
}
```

### 2.3 StockReservationLog (DB 로그 모델)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/domain/model/StockReservationLog.kt`

```kotlin
enum class StockReservationStatus {
    RESERVED,   // 예약됨
    CONFIRMED,  // 확정됨 (결제 완료)
    RELEASED,   // 해제됨 (취소)
    EXPIRED,    // 만료됨
}

data class StockReservationLog(
    val id: UUID,
    val reservationId: String,
    val orderId: UUID,
    val storeId: UUID,
    val products: List<ProductReservation>,
    var status: StockReservationStatus,
    val reservedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    var confirmedAt: LocalDateTime? = null,
    var releasedAt: LocalDateTime? = null,
)

data class ProductReservation(
    val productId: UUID,
    val quantity: Int,
)
```

---

## 3. 재고 예약 스케줄러

### 3.1 StockReservationScheduler

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/adapter/outbound/scheduler/StockReservationScheduler.kt`

**역할**: 만료된 재고 예약 자동 복구 (5분 주기)

```kotlin
@Component
class StockReservationScheduler(
    private val stockReservationService: StockReservationService,
) {
    // 매 5분마다 실행
    // ShedLock으로 분산 환경 중복 실행 방지
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(
        name = "StockReservationScheduler.processExpiredReservations",
        lockAtMostFor = "9m",
        lockAtLeastFor = "30s",
    )
    fun processExpiredReservations() {
        stockReservationService.processExpiredReservations(LocalDateTime.now())
    }
}
```

---

## 4. 상품 조회 인터페이스

### 4.1 ProductPort (도메인 Port)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/domain/port/ProductPort.kt`

```kotlin
interface ProductPort {
    fun loadById(productId: UUID): ProductInfo?
    fun loadAllById(productIds: List<UUID>): List<ProductInfo>
}
```

### 4.2 ProductClient (클라이언트 인터페이스)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/adapter/outbound/client/ProductClient.kt`

```kotlin
interface ProductClient {
    fun getProduct(productId: UUID): ProductResponse?
    fun searchProducts(request: ProductSearchRequest): List<ProductResponse>

    data class ProductSearchRequest(val ids: List<UUID>)

    data class ProductResponse(
        val id: UUID,
        val storeId: UUID,
        val name: String,
        val storeName: String,
        val price: BigDecimal,
    )
}
```

### 4.3 ProductInfo (Value Object)

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/domain/model/ProductInfo.kt`

```kotlin
data class ProductInfo(
    val id: UUID,
    val storeId: UUID,
    val storeName: String,
    val name: String,
    val price: BigDecimal,
)
```

---

## 5. 이벤트 핸들러

### 5.1 StockReservedEventHandler

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/application/event/StockReservedEventHandler.kt`

**역할**: `StockReservedEvent`를 받아 주문 상태를 `ORDER_CONFIRMED`로 변경

> **참고**: 이 핸들러는 Product Service에서 발행한 `stock.reserved` 이벤트를 Order Service에서 소비하는 방식으로 변경됨

---

## 6. Redis Key 구조

### Product Service에서 관리해야 할 Redis Keys

| Key 패턴 | 타입 | 용도 | TTL |
|----------|------|------|-----|
| `product:remaining-stock:{productId}` | AtomicLong | 상품별 잔여 재고 수량 | 없음 (영구) |
| `product:reservation-stock:{reservationId}` | String (Bucket) | 예약 정보 (`productId:qty,productId:qty`) | 10분 (설정 가능) |
| `product:reservation-expiry-index` | ScoredSortedSet | 예약 만료 인덱스 (score=만료시각) | 없음 |

### 데이터 형식

**reservation-stock 값 형식**:
```
{productId}:{quantity},{productId}:{quantity}
예: "aaaaaaaa-aaaa-aaaa-aaaa-000000000001:5,aaaaaaaa-aaaa-aaaa-aaaa-000000000002:3"
```

**reservation-expiry-index 값 형식**:
```
{reservationId}:{reservationData}
예: "RSV-123e4567:aaaaaaaa-aaaa-aaaa-aaaa-000000000001:5"
```

---

## 7. 의존성 및 설정

### build.gradle 의존성
```groovy
// Redisson
implementation 'org.redisson:redisson-spring-boot-starter:3.x.x'

// ShedLock (분산 스케줄러 락)
implementation 'net.javacrumbs.shedlock:shedlock-spring:x.x.x'
implementation 'net.javacrumbs.shedlock:shedlock-provider-redis-spring:x.x.x'
```

### RedissonConfig

**파일 위치**: `order-api/src/main/kotlin/com/groom/order/configuration/RedissonConfig.kt`

---

## 8. 테스트 참조

| 테스트 파일 | 설명 |
|-------------|------|
| `RedissonStockReservationServiceIntegrationTest.kt` | Redis 재고 예약 통합 테스트 |
| `StockReservationSchedulerTest.kt` | 스케줄러 단위 테스트 |
| `StockReservationSchedulerIntegrationTest.kt` | 스케줄러 통합 테스트 |
| `CreateOrderServiceTest.kt` | 주문 생성 시 재고 예약 연동 테스트 |

---

## 9. Product Service 구현 시 프롬프트 예시

```
다음 문서를 참고하여 Product Service에 재고 예약 기능을 구현해주세요:

1. 이벤트 흐름: c4ang-contract-hub/event-flows/order-creation/success.md
2. 이관 대상 코드: docs/refactoring/PRODUCT_SERVICE_MIGRATION_INDEX.md

구현 요구사항:
- order.created 이벤트를 소비하여 재고 예약 처리
- 재고 충분 시: stock.reserved 이벤트 발행
- 재고 부족 시: stock.reservation.failed 이벤트 발행
- Redis 기반 분산 락 및 CAS 로직 유지
- 만료 예약 자동 복구 스케줄러 구현
```

---

## 변경 이력

| 날짜 | 작성자 | 내용 |
|------|--------|------|
| 2025-12-05 | Claude | 초기 문서 작성 |
