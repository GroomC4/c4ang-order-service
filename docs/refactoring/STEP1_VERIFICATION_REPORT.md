# Step 1: Product Service 재고 예약 로직 검증 결과

> **검증일**: 2025-12-05
>
> **비교 대상**:
> - Order Service (이관 대상): `PRODUCT_SERVICE_MIGRATION_INDEX.md`
> - Product Service (현재 구현): `/Users/groom/IdeaProjects/c4ang-product-service`

---

## 검증 요약

| 항목 | Order Service (이관 대상) | Product Service (현재) | 상태 |
|------|---------------------------|------------------------|------|
| OrderCreatedConsumer | N/A | ✅ 구현됨 | 완료 |
| 멱등성 보장 | ProcessedEvent | ✅ ProcessedEvent | 완료 |
| Redis 재고 차감 | CAS 기반 | ⚠️ addAndGet 사용 | 개선 필요 |
| 예약 정보 저장 | TTL + 만료 인덱스 | ✅ TTL만 사용 | 차이 있음 |
| 만료 예약 스케줄러 | ✅ 5분 주기 | ❌ 없음 | **추가 필요** |
| stock.reserved 이벤트 | N/A | ✅ 구현됨 | 완료 |
| stock.reservation.failed 이벤트 | N/A | ✅ 구현됨 | 완료 |

---

## 1. OrderCreatedConsumer 검증 ✅

**파일**: `adapter/inbound/event/OrderCreatedConsumer.kt`

### 확인 완료 항목
- [x] `order.created` 토픽 구독 (`topics = ["order.created"]`)
- [x] 멱등성 보장 (`ProcessedEventRepository.existsByEventId()`)
- [x] 재고 예약 후 이벤트 발행 (`stockEventProducer.publishStockReserved/Failed`)
- [x] Kafka Manual Commit (`acknowledgment.acknowledge()`)

### 플로우
```
order.created 수신
  → 멱등성 체크 (ProcessedEvent)
  → stockService.reserveStock()
  → Success → stockEventProducer.publishStockReserved()
  → Failure → stockEventProducer.publishStockReservationFailed()
  → ProcessedEvent 저장
  → Kafka Commit
```

---

## 2. Redis 재고 예약 로직 비교 ⚠️

### 2.1 Redis Key 구조 차이

| 용도 | Order Service (이관 대상) | Product Service (현재) |
|------|---------------------------|------------------------|
| 재고 | `product:remaining-stock:{productId}` | `stock:{productId}` |
| 예약 | `product:reservation-stock:{reservationId}` | `stock:reservation:{orderId}:{productId}` |
| 만료 인덱스 | `product:reservation-expiry-index` | ❌ 없음 |

**분석**:
- Key prefix가 다름 (`product:` vs `stock:`)
- 예약 Key 구조가 다름 (reservationId 단일 vs orderId+productId 복합)
- **만료 인덱스 없음** → 스케줄러 동작 불가

### 2.2 재고 차감 로직 비교

**Order Service (CAS 기반)**:
```kotlin
// Compare-And-Set 재시도 루프
repeat(MAX_RETRY_ATTEMPTS) {
    val current = atomicLong.get()
    if (current < quantity) return false
    if (atomicLong.compareAndSet(current, current - quantity)) {
        return true
    }
}
```

**Product Service (현재)**:
```kotlin
// 단순 addAndGet
val remainingStock = stockReservationPort.decrementStock(productId, quantity)
if (remainingStock < 0) {
    // 롤백
    stockReservationPort.incrementStock(productId, quantity)
}
```

**분석**:
- Product Service는 먼저 차감 후 음수면 롤백하는 방식
- 이 방식도 동작하지만, 음수 상태가 순간적으로 존재할 수 있음
- CAS 방식이 더 안전하지만 현재 방식도 기능적으로 동작함

---

## 3. 만료 예약 스케줄러 ❌ 누락

**Order Service (이관 대상)**:
```kotlin
@Component
class StockReservationScheduler {
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "processExpiredReservations")
    fun processExpiredReservations() {
        // ScoredSortedSet에서 만료된 예약 조회
        // 재고 복구 + 예약 정보 삭제
    }
}
```

**Product Service**: 스케줄러 없음

**분석**:
- 현재 Product Service는 예약 만료 시 Redis TTL로만 의존
- TTL 만료 시 Key는 자동 삭제되지만, **재고 복구가 이루어지지 않음**
- 결제 완료/취소 없이 TTL 만료되면 재고가 영구적으로 차감된 상태로 유지됨

**필수 추가 작업**:
1. `product:reservation-expiry-index` ScoredSortedSet 추가
2. 만료 예약 스케줄러 구현
3. ShedLock 의존성 추가

---

## 4. StockEventProducer 검증 ✅

**파일**: `adapter/outbound/event/StockEventProducer.kt`

### 확인 완료 항목
- [x] `stock.reserved` 이벤트 발행
- [x] `stock.reservation.failed` 이벤트 발행
- [x] Avro 스키마 사용 (`StockReserved`, `StockReservationFailed`)
- [x] orderId를 파티션 키로 사용

---

## 5. 필수 보완 작업 목록

### 5.1 만료 예약 스케줄러 추가 (필수)

**새 파일 생성**: `adapter/outbound/scheduler/StockReservationScheduler.kt`

```kotlin
@Component
class StockReservationScheduler(
    private val stockReservationPort: StockReservationPort,
) {
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(
        name = "StockReservationScheduler.processExpiredReservations",
        lockAtMostFor = "9m",
        lockAtLeastFor = "30s",
    )
    fun processExpiredReservations() {
        stockReservationPort.processExpiredReservations(LocalDateTime.now())
    }
}
```

### 5.2 StockReservationPort 확장 (필수)

```kotlin
interface StockReservationPort {
    // 기존 메서드들...

    // 추가 필요
    fun registerExpiry(orderId: UUID, productId: UUID, expiresAt: LocalDateTime)
    fun getExpiredReservations(now: LocalDateTime): List<ExpiredReservation>
    fun processExpiredReservations(now: LocalDateTime)
}
```

### 5.3 RedisStockReservationAdapter 확장 (필수)

```kotlin
// 만료 인덱스 관리 추가
fun registerExpiry(orderId: UUID, productId: UUID, expiresAt: LocalDateTime) {
    val expirySet = redissonClient.getScoredSortedSet<String>("stock:reservation-expiry-index")
    val score = expiresAt.toEpochSecond(ZoneOffset.UTC).toDouble()
    val entry = "$orderId:$productId"
    expirySet.add(score, entry)
}

fun processExpiredReservations(now: LocalDateTime) {
    val expirySet = redissonClient.getScoredSortedSet<String>("stock:reservation-expiry-index")
    val nowEpoch = now.toEpochSecond(ZoneOffset.UTC).toDouble()

    // 만료된 예약 조회
    val expiredEntries = expirySet.valueRange(0.0, true, nowEpoch, true)

    for (entry in expiredEntries) {
        val (orderId, productId) = entry.split(":")
        // 예약 정보 조회 → 재고 복구 → 예약 삭제
        rollbackExpiredReservation(UUID.fromString(orderId), UUID.fromString(productId))
        expirySet.remove(entry)
    }
}
```

### 5.4 build.gradle 의존성 추가 (필수)

```groovy
// ShedLock
implementation 'net.javacrumbs.shedlock:shedlock-spring:5.x.x'
implementation 'net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.x.x'
```

---

## 6. 선택적 개선 사항

### 6.1 Redis Key 구조 통일 (선택)

현재 Key 구조가 Order Service와 다르지만, Product Service 내에서 일관성 있게 사용되므로 유지해도 무방.

단, 문서화 필요:
```
Product Service Redis Key 규칙:
- stock:{productId} → 재고 수량 (AtomicLong)
- stock:reservation:{orderId}:{productId} → 예약 정보 (Bucket, TTL)
- stock:reservation-expiry-index → 만료 인덱스 (ScoredSortedSet)
```

### 6.2 CAS 기반 재고 차감 (선택)

현재 addAndGet + 롤백 방식도 동작하지만, CAS 방식이 더 안전.
현재 방식 유지 가능 (기능적으로 문제 없음).

---

## 7. 다음 단계

Step 1.1부터 순차적으로 진행:

1. **Step 1.1**: 만료 예약 스케줄러 추가
2. **Step 1.2**: StockReservationPort/Adapter 확장
3. **Step 1.3**: ShedLock 설정 추가

완료 후 Step 2 (Order Service CreateOrderService 리팩토링) 진행.

---

## 변경 이력

| 날짜 | 작성자 | 내용 |
|------|--------|------|
| 2025-12-05 | Claude | 초기 검증 보고서 작성 |
