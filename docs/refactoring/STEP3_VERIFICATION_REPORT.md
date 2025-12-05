# Step 3: Order Service Kafka Consumer 추가 검증 결과

> **검증일**: 2025-12-05
>
> **작업 범위**: Order Service에 Kafka Consumer 추가 (StockReservationFailed 이벤트 처리)
>
> **목표**: Product Service에서 발행한 재고 예약 실패 이벤트를 수신하여 주문 취소

---

## 검증 요약

| 항목 | 상태 | 비고 |
|------|------|------|
| OrderEventHandler 인터페이스 확장 | ✅ 완료 | `handleStockReservationFailed()` 추가 |
| OrderEventHandlerService 구현 | ✅ 완료 | 재고 예약 실패 시 ORDER_CANCELLED |
| StockReservationFailedKafkaListener 생성 | ✅ 완료 | `stock.reservation.failed` 토픽 구독 |
| Avro 스키마 확인 | ✅ 완료 | 기존 스키마 사용 가능 |
| 빌드 성공 | ✅ 완료 | `./gradlew :order-api:compileKotlin` |

---

## 1. 변경된 파일 목록

### 1.1 Domain Layer

| 파일 | 변경 내용 |
|------|-----------|
| `OrderEventHandler.kt` | `handleStockReservationFailed()` 메서드 추가, `FailedItemInfo` data class 추가 |

### 1.2 Application Layer

| 파일 | 변경 내용 |
|------|-----------|
| `OrderEventHandlerService.kt` | `handleStockReservationFailed()` 구현 추가 |

### 1.3 Adapter Layer (신규)

| 파일 | 변경 내용 |
|------|-----------|
| `StockReservationFailedKafkaListener.kt` | 신규 생성 - `stock.reservation.failed` 토픽 구독 |

---

## 2. 주요 변경 상세

### 2.1 OrderEventHandler 인터페이스 확장

```kotlin
interface OrderEventHandler {
    // 기존 메서드
    fun handleStockReserved(...)
    fun handlePaymentCompleted(...)
    fun handlePaymentFailed(...)
    fun handlePaymentCancelled(...)

    // 신규 추가
    fun handleStockReservationFailed(
        orderId: UUID,
        failedItems: List<FailedItemInfo>,
        failureReason: String,
        failedAt: LocalDateTime,
    )

    // 신규 data class
    data class FailedItemInfo(
        val productId: UUID,
        val requestedQuantity: Int,
        val availableStock: Int,
    )
}
```

### 2.2 OrderEventHandlerService 구현

```kotlin
@Transactional
override fun handleStockReservationFailed(
    orderId: UUID,
    failedItems: List<OrderEventHandler.FailedItemInfo>,
    failureReason: String,
    failedAt: LocalDateTime,
) {
    val order = loadOrderPort.loadById(orderId)
        ?: throw OrderException.OrderNotFound(orderId)

    // 상태 전이: ORDER_CREATED → ORDER_CANCELLED
    order.cancel("재고 예약 실패: $failureReason", failedAt)
    saveOrderPort.save(order)

    // 감사 로그 기록
    orderAuditRecorder.record(
        orderId = orderId,
        eventType = OrderAuditEventType.ORDER_CANCELLED,
        changeSummary = "재고 예약 실패로 인한 주문 취소 (Kafka 이벤트)",
        ...
    )

    // Note: 재고가 예약되지 않았으므로 OrderCancelled 이벤트 발행 불필요
}
```

### 2.3 StockReservationFailedKafkaListener

```kotlin
@Component
class StockReservationFailedKafkaListener(
    private val orderEventHandler: OrderEventHandler,
) {
    @KafkaListener(
        topics = ["\${kafka.topics.stock-reservation-failed:stock.reservation.failed}"],
        groupId = "\${kafka.consumer.group-id:order-service}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onStockReservationFailed(
        record: ConsumerRecord<String, StockReservationFailed>,
        acknowledgment: Acknowledgment,
    ) {
        val event = record.value()
        val orderId = UUID.fromString(event.orderId)

        val failedItems = event.failedItems.map { item ->
            OrderEventHandler.FailedItemInfo(
                productId = UUID.fromString(item.productId),
                requestedQuantity = item.requestedQuantity,
                availableStock = item.availableStock,
            )
        }

        orderEventHandler.handleStockReservationFailed(
            orderId = orderId,
            failedItems = failedItems,
            failureReason = event.failureReason,
            failedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.failedAt),
                ZoneId.systemDefault(),
            ),
        )

        acknowledgment.acknowledge()
    }
}
```

---

## 3. 이벤트 플로우 (재고 예약 실패)

```
┌─────────────────┐     order.created      ┌──────────────────┐
│  Order Service  │ ────────────────────► │ Product Service  │
│                 │                        │                  │
│ 1. 주문 생성     │                        │ 2. 재고 부족 확인  │
│    (ORDER_CREATED)                       │                  │
└─────────────────┘                        └────────┬─────────┘
        ▲                                           │
        │                                           │
        │    stock.reservation.failed               │
        └───────────────────────────────────────────┘
                          │
                          ▼
                   ORDER_CANCELLED
                   (주문 취소)
```

### 3.1 처리 순서
1. Client → Order Service: 주문 생성 요청
2. Order Service: 주문 저장 (ORDER_CREATED)
3. Order Service → Kafka: `order.created` 이벤트 발행
4. Product Service: 이벤트 수신 → 재고 부족 확인
5. Product Service → Kafka: `stock.reservation.failed` 이벤트 발행
6. **Order Service: 이벤트 수신 → 주문 취소 (ORDER_CANCELLED)**

---

## 4. Avro 스키마

### 4.1 StockReservationFailed.avsc (기존)

```json
{
  "type": "record",
  "name": "StockReservationFailed",
  "namespace": "com.groom.ecommerce.saga.event.avro",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "eventTimestamp", "type": "long"},
    {"name": "orderId", "type": "string"},
    {"name": "failedItems", "type": {"type": "array", "items": "FailedItem"}},
    {"name": "failureReason", "type": "string"},
    {"name": "failedAt", "type": "long"}
  ]
}
```

### 4.2 FailedItem (중첩 레코드)

```json
{
  "type": "record",
  "name": "FailedItem",
  "fields": [
    {"name": "productId", "type": "string"},
    {"name": "requestedQuantity", "type": "int"},
    {"name": "availableStock", "type": "int"}
  ]
}
```

---

## 5. 빌드 검증

```bash
$ ./gradlew :order-api:compileKotlin --no-daemon

BUILD SUCCESSFUL in 5s
```

---

## 6. 기존 Kafka Listener 현황

| Listener | 토픽 | 처리 내용 |
|----------|------|-----------|
| `StockReservedKafkaListener` | `stock.reserved` | ORDER_CREATED → ORDER_CONFIRMED |
| `StockReservationFailedKafkaListener` | `stock.reservation.failed` | ORDER_CREATED → ORDER_CANCELLED (신규) |
| `PaymentCompletedKafkaListener` | `payment.completed` | PAYMENT_PENDING → PAYMENT_COMPLETED |
| `PaymentFailedKafkaListener` | `payment.failed` | → ORDER_CANCELLED |
| `PaymentCancelledKafkaListener` | `payment.cancelled` | → ORDER_CANCELLED |

---

## 7. 다음 단계 (Step 4)

### 필요 작업: 테스트 코드 수정 (레거시 제거)

Step 2에서 다음 변경이 발생하여 테스트 코드 수정 필요:
- `OrderStatus.PENDING` → `OrderStatus.ORDER_CREATED`
- `OrderStatus.STOCK_RESERVED` → `OrderStatus.ORDER_CONFIRMED`
- `order.markStockReserved()` → `order.confirm()`
- `ProductPort`, `StorePort`, `StockReservationManager/Service` 의존성 제거

### Step 4 작업 목록

1. **단위 테스트 수정**
   - `OrderTest.kt`, `OrderPolicyTest.kt`, `OrderManagerTest.kt`
   - `CreateOrderServiceTest.kt`, `CancelOrderServiceTest.kt`

2. **Fixture 수정**
   - `OrderTestFixture.kt`: `createOrderConfirmedOrder()`, `createOrderCreatedOrder()`

3. **통합 테스트 Disable 처리**
   - `CreateOrderServiceIntegrationTest.kt`: `@Disabled` 추가

4. **Kafka Listener 테스트 추가**
   - `StockReservationFailedKafkaListenerTest.kt`

---

## 변경 이력

| 날짜 | 작성자 | 내용 |
|------|--------|------|
| 2025-12-05 | Claude | Step 3 검증 보고서 작성 |
