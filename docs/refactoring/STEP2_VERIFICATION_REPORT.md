# Step 2: Order Service CreateOrderService 리팩토링 검증 결과

> **검증일**: 2025-12-05
>
> **작업 범위**: Order Service에서 Product/Stock 직접 의존성 제거
>
> **목표**: 이벤트 기반 아키텍처로 전환 (order.created → Product Service)

---

## 검증 요약

| 항목 | 변경 전 | 변경 후 | 상태 |
|------|---------|---------|------|
| Product 직접 조회 | ProductPort 사용 | ❌ 제거 | ✅ 완료 |
| Stock 직접 예약 | StockReservationManager 사용 | ❌ 제거 | ✅ 완료 |
| Store 직접 조회 | StorePort 사용 | ❌ 제거 | ✅ 완료 |
| 상품 정보 획득 | Product Service에서 조회 | 클라이언트에서 전달 | ✅ 완료 |
| 주문 상태 플로우 | PENDING → STOCK_RESERVED | ORDER_CREATED → ORDER_CONFIRMED | ✅ 완료 |
| 빌드 성공 | - | `./gradlew :order-api:compileKotlin` | ✅ 성공 |

---

## 1. 변경된 파일 목록

### 1.1 Application Layer

| 파일 | 변경 내용 |
|------|-----------|
| `CreateOrderCommand.kt` | OrderItemDto에 productName, unitPrice 추가 |
| `CreateOrderService.kt` | ProductPort, StorePort, StockReservationManager 의존성 제거 |

### 1.2 Domain Layer

| 파일 | 변경 내용 |
|------|-----------|
| `Order.kt` | 기본 상태 ORDER_CREATED, markStockReserved() → confirm() |
| `OrderStatus.kt` | PENDING/STOCK_RESERVED 제거, ORDER_CREATED/ORDER_CONFIRMED 추가 |
| `OrderPolicy.kt` | validateProductsBelongToStore() 제거, canCancelOrder() 상태 업데이트 |
| `OrderManager.kt` | ProductInfo 의존성 제거, createOrder 시그니처 변경 |

### 1.3 Adapter Layer

| 파일 | 변경 내용 |
|------|-----------|
| `OrderCommandController.kt` | CreateOrderRequest → CreateOrderCommand 매핑 수정 |
| `CreateOrderRequest.kt` | OrderItemRequest에 productName, unitPrice 추가 |
| `OrderEventHandlerService.kt` | markStockReserved() → confirm() 호출 변경 |
| `CancelOrderService.kt` | StockReservationService 의존성 제거 |
| `OrderTimeoutScheduler.kt` | StockReservationService 의존성 제거 |

### 1.4 Event Layer

| 파일 | 변경 내용 |
|------|-----------|
| `OrderCreatedEvent.kt` | items 리스트 추가 (Product Service가 재고 예약에 필요) |

---

## 2. 주요 변경 상세

### 2.1 OrderStatus 변경

**변경 전**:
```kotlin
enum class OrderStatus {
    PENDING,           // 주문 생성 (재고 확인 대기)
    STOCK_RESERVED,    // 재고 예약 완료
    PAYMENT_PENDING,
    // ...
}
```

**변경 후**:
```kotlin
enum class OrderStatus {
    ORDER_CREATED,     // 주문 생성 (재고 확인 대기)
    ORDER_CONFIRMED,   // 재고 예약 완료 → 결제 대기
    PAYMENT_PENDING,
    // ...
}
```

### 2.2 CreateOrderService 변경

**변경 전**:
```kotlin
@Service
class CreateOrderService(
    private val productPort: ProductPort,           // ❌ 제거
    private val storePort: StorePort,               // ❌ 제거
    private val stockReservationManager: StockReservationManager,  // ❌ 제거
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val orderManager: OrderManager,
    private val domainEventPublisher: DomainEventPublisher,
)
```

**변경 후**:
```kotlin
@Service
class CreateOrderService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val orderManager: OrderManager,
    private val domainEventPublisher: DomainEventPublisher,
)
```

### 2.3 Order 도메인 메서드 변경

**변경 전**:
```kotlin
fun markStockReserved() {
    require(status == OrderStatus.PENDING)
    this.status = OrderStatus.STOCK_RESERVED
}
```

**변경 후**:
```kotlin
fun confirm() {
    require(status == OrderStatus.ORDER_CREATED)
    this.status = OrderStatus.ORDER_CONFIRMED
}
```

### 2.4 OrderCreatedEvent 변경

**변경 전**:
```kotlin
data class OrderCreatedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val storeId: UUID,
    val userId: UUID,
    val totalAmount: BigDecimal,
    val createdAt: LocalDateTime,
) : DomainEvent
```

**변경 후**:
```kotlin
data class OrderCreatedEvent(
    val orderId: UUID,
    val orderNumber: String,
    val storeId: UUID,
    val userId: UUID,
    val totalAmount: BigDecimal,
    val createdAt: LocalDateTime,
    val items: List<OrderItem>,  // ✅ 추가: Product Service 재고 예약에 필요
) : DomainEvent {
    data class OrderItem(
        val productId: UUID,
        val productName: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
    )
}
```

---

## 3. 이벤트 플로우 (리팩토링 후)

```
┌─────────────────┐     order.created      ┌──────────────────┐
│  Order Service  │ ────────────────────► │ Product Service  │
│                 │                        │                  │
│ 1. 주문 생성     │                        │ 2. 재고 예약      │
│    (ORDER_CREATED)                       │                  │
└─────────────────┘                        └────────┬─────────┘
        ▲                                           │
        │                                           │
        │          stock.reserved                   │
        └───────────────────────────────────────────┘
                          │
                          ▼
                   ORDER_CONFIRMED
                   (결제 대기 상태)
```

### 3.1 성공 플로우
1. Client → Order Service: 주문 생성 요청 (상품 정보 포함)
2. Order Service: 주문 저장 (ORDER_CREATED)
3. Order Service → Kafka: `order.created` 이벤트 발행
4. Product Service: 이벤트 수신 → 재고 예약
5. Product Service → Kafka: `stock.reserved` 이벤트 발행
6. Order Service: 이벤트 수신 → 주문 확정 (ORDER_CONFIRMED)

### 3.2 실패 플로우
1. Client → Order Service: 주문 생성 요청
2. Order Service: 주문 저장 (ORDER_CREATED)
3. Order Service → Kafka: `order.created` 이벤트 발행
4. Product Service: 이벤트 수신 → 재고 부족
5. Product Service → Kafka: `stock.reservation.failed` 이벤트 발행
6. Order Service: 이벤트 수신 → 주문 취소 (ORDER_CANCELLED)

---

## 4. API 변경 사항

### 4.1 CreateOrderRequest 변경

**변경 전**:
```json
{
  "storeId": "uuid",
  "items": [
    {
      "productId": "uuid",
      "quantity": 2
    }
  ]
}
```

**변경 후**:
```json
{
  "storeId": "uuid",
  "items": [
    {
      "productId": "uuid",
      "productName": "상품명",
      "quantity": 2,
      "unitPrice": 10000
    }
  ]
}
```

> **Note**: 클라이언트가 상품 정보(이름, 가격)를 함께 전달해야 합니다.
> 이는 Order Service가 Product Service에 직접 접근하지 않기 때문입니다.
> 클라이언트는 사전에 Product Service API를 통해 상품 정보를 조회해야 합니다.

---

## 5. 빌드 검증

```bash
$ ./gradlew :order-api:compileKotlin --no-daemon

BUILD SUCCESSFUL in 5s
```

---

## 6. 제거된 의존성

Order Service에서 아래 의존성들이 완전히 제거되었습니다:

| 제거된 의존성 | 역할 | 대체 방안 |
|--------------|------|-----------|
| `ProductPort` | 상품 정보 조회 | 클라이언트에서 전달 |
| `StorePort` | 스토어 검증 | Product Service에서 검증 |
| `StockReservationManager` | 재고 예약/복원 | 이벤트 기반 처리 |
| `StockReservationService` | 재고 복원 (취소/타임아웃) | 이벤트 기반 처리 |

---

## 7. 다음 단계 (Step 3)

### 필요 작업: StockReservationFailedKafkaListener 추가

현재 Order Service에는 다음 Consumer가 있습니다:
- ✅ `StockReservedKafkaListener` - 재고 예약 성공 처리
- ❌ `StockReservationFailedKafkaListener` - **누락** (재고 예약 실패 처리)

### Step 3 작업 목록

1. **OrderEventHandler 확장**
   - `handleStockReservationFailed()` 메서드 추가

2. **OrderEventHandlerService 구현**
   - 재고 예약 실패 시 ORDER_CANCELLED 상태로 변경

3. **StockReservationFailedKafkaListener 생성**
   - `stock.reservation.failed` 토픽 구독
   - Avro 스키마 역직렬화

4. **Avro 스키마 확인**
   - `StockReservationFailed.avsc` 스키마 확인/생성

---

## 변경 이력

| 날짜 | 작성자 | 내용 |
|------|--------|------|
| 2025-12-05 | Claude | Step 2 검증 보고서 작성 |
