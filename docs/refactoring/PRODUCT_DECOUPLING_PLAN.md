# Order Service - Product 도메인 분리 리팩토링 계획

## 개요

이벤트 흐름 문서(`c4ang-contract-hub/event-flows/order-creation`)에 따르면, **Order Service는 Product 도메인이나 데이터에 직접 접근하지 않습니다.**

### 현재 아키텍처 vs 목표 아키텍처

**현재 (As-Is):**
```
Client → Order Service → [Product Service 직접 조회] → 재고 예약 → 주문 생성
```

**목표 (To-Be):**
```
Client → Order Service → 주문 생성 (ORDER_CREATED 이벤트 발행)
                              ↓
         Product Service ← Kafka (order.created) → 재고 확인/예약
                              ↓
         Order Service ← Kafka (stock.reserved / stock.reservation.failed)
```

---

## 제거 대상 파일 목록

### 1단계: Product 관련 Port/Adapter 제거

| 파일 경로 | 설명 | 상태 |
|-----------|------|------|
| `domain/port/ProductPort.kt` | Product 도메인 조회 인터페이스 | **주석처리** |
| `domain/model/ProductInfo.kt` | Product 정보 Value Object | **주석처리** |
| `adapter/outbound/client/ProductClient.kt` | Product Service 클라이언트 인터페이스 | **주석처리** |
| `adapter/outbound/client/ProductAdapter.kt` | ProductPort 구현체 | **주석처리** |
| `adapter/outbound/client/ProductFeignClient.kt` | Product Service Feign 클라이언트 | **주석처리** |
| `test/.../client/TestProductClient.kt` | 테스트용 Product 클라이언트 | **주석처리** |

### 2단계: 재고 예약 관련 코드 제거 (Product Service로 이관)

| 파일 경로 | 설명 | 상태 |
|-----------|------|------|
| `adapter/outbound/stock/StockReservationService.kt` | 재고 예약 서비스 인터페이스 | **주석처리** |
| `adapter/outbound/stock/RedissonStockReservationService.kt` | Redisson 기반 재고 예약 구현체 | **주석처리** |
| `adapter/outbound/scheduler/StockReservationScheduler.kt` | 재고 예약 만료 처리 스케줄러 | **주석처리** |
| `domain/service/StockReservationManager.kt` | 재고 예약 도메인 서비스 | **주석처리** |
| `domain/model/StockReservation.kt` | 재고 예약 도메인 모델 | **주석처리** |
| `domain/model/ReservationResult.kt` | 재고 예약 결과 모델 | **주석처리** |

### 3단계: Store 관련 Port/Adapter 제거 (선택적)

> ⚠️ **확인 필요**: Store 존재 확인이 Order Service에서 필요한지 이벤트 흐름 검토 필요

| 파일 경로 | 설명 | 상태 |
|-----------|------|------|
| `domain/port/StorePort.kt` | Store 도메인 조회 인터페이스 | 검토 필요 |
| `domain/model/StoreInfo.kt` | Store 정보 Value Object | 검토 필요 |
| `adapter/outbound/client/StoreClient.kt` | Store Service 클라이언트 인터페이스 | 검토 필요 |
| `adapter/outbound/client/StoreAdapter.kt` | StorePort 구현체 | 검토 필요 |
| `adapter/outbound/client/StoreFeignClient.kt` | Store Service Feign 클라이언트 | 검토 필요 |
| `test/.../client/TestStoreClient.kt` | 테스트용 Store 클라이언트 | 검토 필요 |

### 4단계: 관련 테스트 파일 수정/제거

| 파일 경로 | 설명 | 상태 |
|-----------|------|------|
| `test/.../scheduler/StockReservationSchedulerTest.kt` | 스케줄러 단위 테스트 | **주석처리** |
| `test/.../scheduler/StockReservationSchedulerIntegrationTest.kt` | 스케줄러 통합 테스트 | **주석처리** |
| `test/.../stock/RedissonStockReservationServiceIntegrationTest.kt` | Redis 재고 예약 통합 테스트 | **주석처리** |
| `test/.../event/StockReservedEventHandlerTest.kt` | 이벤트 핸들러 테스트 | 수정 필요 |

---

## 수정 대상 파일 목록

### 주문 생성 서비스 수정 (`CreateOrderService.kt`)

**제거할 로직:**
1. `ProductPort` 의존성 제거
2. `StorePort` 의존성 제거 (검토 필요)
3. `StockReservationManager` 의존성 제거
4. 상품 조회 로직 제거 (라인 85-90)
5. 상품 검증 로직 제거 (라인 93-94)
6. 재고 예약 로직 제거 (라인 96-110)
7. `StockReservedEvent` 발행 제거 (라인 156-171) → Product Service에서 발행

**수정 후 플로우:**
```kotlin
@Transactional
fun createOrder(command: CreateOrderCommand): CreateOrderResult {
    // 1. 멱등성 확인
    // 2. 주문 생성 (status: ORDER_CREATED)
    // 3. OrderCreatedEvent 발행 (Kafka로 Product Service에 전달)
    // 4. 결과 반환
}
```

### 주문 관리자 수정 (`OrderManager.kt`)

**제거할 로직:**
1. `ProductInfo` 파라미터 제거
2. 상품 가격 기반 총액 계산 로직 수정 → 클라이언트에서 받은 가격 사용 또는 이벤트로 수신

### 주문 정책 수정 (`OrderPolicy.kt`)

**제거할 로직:**
1. `validateProductsBelongToStore()` 메서드 제거 (라인 90-99)
2. `ProductInfo` import 제거

### 주문 Command DTO 수정 (`CreateOrderCommand.kt`)

**추가할 필드:**
```kotlin
data class OrderItemDto(
    val productId: UUID,
    val productName: String,  // 추가
    val quantity: Int,
    val unitPrice: BigDecimal, // 추가
)
```

### Web Request DTO 수정 (`CreateOrderRequest.kt`)

**추가할 필드:**
```kotlin
data class OrderItemRequest(
    val productId: UUID,
    val productName: String,  // 추가
    val quantity: Int,
    val unitPrice: BigDecimal, // 추가
)
```

---

## 새로 추가해야 할 Kafka Consumer

### `StockReservedEventConsumer.kt` (신규)

`stock.reserved` 토픽을 구독하여 주문 상태를 `ORDER_CONFIRMED`로 변경

```kotlin
@KafkaListener(topics = ["stock.reserved"])
fun handleStockReserved(event: StockReservedEvent) {
    // 1. orderId로 주문 조회
    // 2. 주문 상태를 ORDER_CONFIRMED로 변경
    // 3. order.confirmed 이벤트 발행
}
```

### `StockReservationFailedEventConsumer.kt` (신규)

`stock.reservation.failed` 토픽을 구독하여 주문을 취소 (보상 트랜잭션)

```kotlin
@KafkaListener(topics = ["stock.reservation.failed"])
fun handleStockReservationFailed(event: StockReservationFailedEvent) {
    // 1. orderId로 주문 조회
    // 2. 주문 상태를 ORDER_CANCELLED로 변경
    // 3. order.cancelled 이벤트 발행
    // 4. 고객 알림 발송
}
```

---

## 마이그레이션 단계

### Phase 1: 코드 주석 처리 (현재 작업)
- [x] 제거 대상 파일 식별
- [ ] Product 관련 파일 주석 처리
- [ ] Stock Reservation 관련 파일 주석 처리
- [ ] 관련 테스트 파일 주석 처리

### Phase 2: CreateOrderService 리팩토링
- [ ] Product/Store 의존성 제거
- [ ] 재고 예약 로직 제거
- [ ] 주문 생성 플로우 단순화

### Phase 3: Kafka Consumer 구현
- [ ] StockReservedEventConsumer 구현
- [ ] StockReservationFailedEventConsumer 구현
- [ ] 이벤트 스키마 정의 (Avro)

### Phase 4: 테스트 및 검증
- [ ] 단위 테스트 수정/추가
- [ ] 통합 테스트 수정/추가
- [ ] E2E 테스트

### Phase 5: 정리
- [ ] 주석 처리된 파일 완전 삭제
- [ ] 불필요한 의존성 제거 (build.gradle)

---

## 주의사항

1. **Store 검증**: 이벤트 흐름에서 Store 존재 확인이 어디서 이루어지는지 확인 필요
2. **가격 정보**: 클라이언트에서 가격을 받을 경우 검증 로직 필요 (Product Service에서 이벤트로 전달하는 방식 권장)
3. **멱등성**: Kafka Consumer에서 멱등성 보장 필요 (eventId 기반)
4. **타임아웃**: 재고 예약 대기 타임아웃 처리 로직 필요

---

## 관련 문서

- [Order Creation Event Flow](../../../c4ang-contract-hub/event-flows/order-creation/README.md)
- [Success Flow](../../../c4ang-contract-hub/event-flows/order-creation/success.md)
- [Stock Reservation Failed Flow](../../../c4ang-contract-hub/event-flows/order-creation/stock-reservation-failed.md)
