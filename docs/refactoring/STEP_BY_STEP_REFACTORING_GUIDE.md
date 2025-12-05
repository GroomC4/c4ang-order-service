# Product 도메인 분리 - 단계별 리팩토링 가이드

> **목적**: Order Service와 Product Service 간 도메인 분리 작업을 단계별로 진행하기 위한 가이드
>
> **작업 원칙**: 컨텍스트 낭비를 최소화하기 위해 각 단계에서 필요한 문서만 참조

---

## 현재 상황 분석

### Product Service (c4ang-product-service)

**이미 구현된 기능** (FILE_INDEX.md 참조):
- `OrderCreatedConsumer.kt`: `order.created` 토픽 소비 → 재고 예약 → 이벤트 발행
- `PaymentCompletedConsumer.kt`: `payment.completed` 토픽 소비 → DB 재고 확정
- `StockReservationService.kt`: 재고 예약 도메인 서비스 (Redis 원자적 예약)
- `RedisStockReservationAdapter.kt`: Redisson 기반 재고 예약 포트 구현
- `StockEventProducer.kt`: `stock.reserved`, `stock.reservation.failed` 이벤트 발행

### Order Service (c4ang-order-service)

**주석 처리 완료된 파일** (Phase 1 완료):
- Product 관련: `ProductPort.kt`, `ProductInfo.kt`, `ProductClient.kt`, `ProductAdapter.kt`, `ProductFeignClient.kt`
- 재고 예약: `StockReservationService.kt`, `RedissonStockReservationService.kt`, `StockReservationScheduler.kt`, `StockReservationManager.kt`, `StockReservation.kt`, `ReservationResult.kt`
- 테스트: `TestProductClient.kt`, `StockReservationSchedulerTest.kt`, `StockReservationSchedulerIntegrationTest.kt`

---

## 리팩토링 단계 요약

| 단계 | 작업 내용 | 대상 서비스 | 필요 문서 |
|------|-----------|-------------|-----------|
| Step 1 | Product Service 재고 예약 로직 검증/보완 | Product | FILE_INDEX.md, MIGRATION_INDEX.md |
| Step 2 | Order Service CreateOrderService 리팩토링 | Order | DECOUPLING_PLAN.md |
| Step 3 | Order Service Kafka Consumer 추가 | Order | event-flows/success.md |
| Step 4 | 테스트 코드 수정 (레거시 제거) | Order | - |
| Step 5 | 통합 테스트 재작성 | Both | event-flows/*.md |
| Step 6 | 정리 (주석 파일 삭제) | Order | - |

---

## Step 1: Product Service 재고 예약 로직 검증/보완

### 목표
Order Service에서 이관된 재고 예약 로직이 Product Service에 제대로 구현되어 있는지 검증하고 누락된 부분 보완

### 참조 문서
```
읽어야 할 문서:
1. /Users/groom/IdeaProjects/c4ang-product-service/docs/FILE_INDEX.md
2. /Users/groom/IdeaProjects/c4ang-order-service/docs/refactoring/PRODUCT_SERVICE_MIGRATION_INDEX.md
```

### 체크리스트

#### 1.1 OrderCreatedConsumer 검증
- [ ] `order.created` 토픽 구독 확인
- [ ] 멱등성 보장 (ProcessedEvent) 확인
- [ ] 재고 예약 후 이벤트 발행 확인

**확인할 파일**:
- `adapter/inbound/event/OrderCreatedConsumer.kt`

#### 1.2 Redis 재고 예약 로직 비교
- [ ] CAS 기반 원자적 재고 차감 구현 확인
- [ ] 예약 정보 저장 (TTL 포함) 확인
- [ ] 만료 인덱스 관리 확인
- [ ] 롤백 로직 확인

**비교 대상**:
| Order Service (이관 대상) | Product Service (현재 구현) |
|---------------------------|----------------------------|
| `RedissonStockReservationService.kt` | `RedisStockReservationAdapter.kt` |
| `StockReservationManager.kt` | `StockReservationService.kt` |

**확인할 Redis Key 구조**:
```
product:remaining-stock:{productId}     → AtomicLong (재고)
product:reservation-stock:{reservationId} → String (예약 정보, TTL)
product:reservation-expiry-index        → ScoredSortedSet (만료 인덱스)
```

#### 1.3 만료 예약 스케줄러 확인
- [ ] 5분 주기 스케줄러 존재 확인
- [ ] ShedLock 분산 락 적용 확인
- [ ] 만료 예약 자동 복구 로직 확인

#### 1.4 이벤트 발행 확인
- [ ] `stock.reserved` 이벤트 발행 확인
- [ ] `stock.reservation.failed` 이벤트 발행 확인
- [ ] Avro 스키마 일치 확인

### 예상 보완 작업
1. Redis Key 구조가 다를 경우 통일
2. 만료 예약 스케줄러 누락 시 추가
3. 이벤트 스키마 불일치 시 조정

---

## Step 2: Order Service CreateOrderService 리팩토링

### 목표
주문 생성 서비스에서 Product/Stock 관련 의존성을 제거하고 이벤트 기반 플로우로 변경

### 참조 문서
```
읽어야 할 문서:
1. /Users/groom/IdeaProjects/c4ang-order-service/docs/refactoring/PRODUCT_DECOUPLING_PLAN.md
   (## 수정 대상 파일 목록 섹션)
```

### 체크리스트

#### 2.1 CreateOrderService.kt 수정
- [ ] `ProductPort` 의존성 제거
- [ ] `StorePort` 의존성 제거 (또는 유지 결정)
- [ ] `StockReservationManager` 의존성 제거
- [ ] 상품 조회 로직 제거
- [ ] 재고 예약 로직 제거
- [ ] `StockReservedEvent` 발행 제거

**수정 후 플로우**:
```kotlin
@Transactional
fun createOrder(command: CreateOrderCommand): CreateOrderResult {
    // 1. 멱등성 확인
    // 2. 주문 생성 (status: ORDER_CREATED)
    // 3. OrderCreatedEvent 발행 (Kafka)
    // 4. 결과 반환
}
```

#### 2.2 CreateOrderCommand.kt 수정
- [ ] `OrderItemDto`에 `productName`, `unitPrice` 필드 추가

```kotlin
data class OrderItemDto(
    val productId: UUID,
    val productName: String,  // 추가
    val quantity: Int,
    val unitPrice: BigDecimal, // 추가
)
```

#### 2.3 CreateOrderRequest.kt 수정
- [ ] `OrderItemRequest`에 `productName`, `unitPrice` 필드 추가
- [ ] Validation 어노테이션 추가

#### 2.4 OrderManager.kt 수정
- [ ] `ProductInfo` 파라미터 제거
- [ ] 클라이언트에서 받은 가격으로 총액 계산

#### 2.5 OrderPolicy.kt 수정
- [ ] `validateProductsBelongToStore()` 메서드 제거

---

## Step 3: Order Service Kafka Consumer 추가

### 목표
Product Service에서 발행한 재고 이벤트를 소비하여 주문 상태 업데이트

### 참조 문서
```
읽어야 할 문서:
1. /Users/groom/IdeaProjects/c4ang-contract-hub/event-flows/order-creation/success.md
2. /Users/groom/IdeaProjects/c4ang-contract-hub/event-flows/order-creation/stock-reservation-failed.md
```

### 체크리스트

#### 3.1 StockReservedEventConsumer.kt 신규 생성
- [ ] `stock.reserved` 토픽 구독
- [ ] 주문 상태 `ORDER_CREATED` → `ORDER_CONFIRMED` 변경
- [ ] `order.confirmed` 이벤트 발행
- [ ] 멱등성 보장 (eventId 기반)

**구현 템플릿**:
```kotlin
@Component
class StockReservedEventConsumer(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @KafkaListener(topics = ["stock.reserved"])
    fun consume(event: StockReserved) {
        // 1. 멱등성 체크
        // 2. 주문 조회
        // 3. 상태 변경 (ORDER_CONFIRMED)
        // 4. 저장
        // 5. order.confirmed 이벤트 발행
    }
}
```

#### 3.2 StockReservationFailedEventConsumer.kt 신규 생성
- [ ] `stock.reservation.failed` 토픽 구독
- [ ] 주문 상태 `ORDER_CREATED` → `ORDER_CANCELLED` 변경
- [ ] 취소 사유 기록 ("재고 부족")
- [ ] `order.cancelled` 이벤트 발행
- [ ] 멱등성 보장

**구현 템플릿**:
```kotlin
@Component
class StockReservationFailedEventConsumer(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @KafkaListener(topics = ["stock.reservation.failed"])
    fun consume(event: StockReservationFailed) {
        // 1. 멱등성 체크
        // 2. 주문 조회
        // 3. 상태 변경 (ORDER_CANCELLED)
        // 4. 취소 사유 기록
        // 5. 저장
        // 6. order.cancelled 이벤트 발행
    }
}
```

#### 3.3 Avro 스키마 확인/추가
- [ ] `StockReserved.avsc` 스키마 확인
- [ ] `StockReservationFailed.avsc` 스키마 확인
- [ ] 필요 시 `c4ang-contract-hub`에서 동기화

---

## Step 4: 테스트 코드 수정 (레거시 제거)

### 목표
레거시 상태 코드 및 제거된 의존성을 참조하는 테스트 코드 수정

### 배경
Step 2에서 다음 변경이 발생:
- `OrderStatus.PENDING` → `OrderStatus.ORDER_CREATED`
- `OrderStatus.STOCK_RESERVED` → `OrderStatus.ORDER_CONFIRMED`
- `order.markStockReserved()` → `order.confirm()`
- `ProductPort`, `StorePort`, `StockReservationManager/Service` 의존성 제거

### 체크리스트

#### 4.1 단위 테스트 수정
- [ ] `OrderTest.kt`: `markStockReserved()` → `confirm()` 변경
- [ ] `OrderPolicyTest.kt`: 레거시 상태 참조 및 `validateProductsBelongToStore()` 테스트 제거
- [ ] `OrderManagerTest.kt`: `ProductInfo` 의존성 제거
- [ ] `CreateOrderServiceTest.kt`: 레거시 의존성 제거 (ProductPort, StorePort, StockReservationManager)
- [ ] `CancelOrderServiceTest.kt`: `StockReservationService` 의존성 제거
- [ ] `OrderEventHandlerServiceTest.kt`: `markStockReserved()` → `confirm()` 변경

#### 4.2 Fixture 수정
- [ ] `OrderTestFixture.kt`: `createStockReservedOrder()` → `createOrderConfirmedOrder()` 변경
- [ ] `OrderTestFixture.kt`: `createOrderCreatedOrder()` 메서드 추가

#### 4.3 통합 테스트 Disable 처리
레거시 아키텍처 기반 통합 테스트는 Step 6에서 재작성 예정:
- [ ] `CreateOrderServiceIntegrationTest.kt`: `@Disabled` 추가
- [ ] 기타 레거시 의존성 참조 통합 테스트

#### 4.4 Kafka Listener 테스트 추가
- [ ] `StockReservationFailedKafkaListenerTest.kt` 신규 생성

### 일괄 변경 명령어
```bash
# 레거시 상태 일괄 변경
find order-api/src/test -name "*.kt" -exec sed -i '' \
  's/OrderStatus\.PENDING/OrderStatus.ORDER_CREATED/g; \
   s/OrderStatus\.STOCK_RESERVED/OrderStatus.ORDER_CONFIRMED/g; \
   s/createStockReservedOrder/createOrderConfirmedOrder/g' {} \;
```

---

## Step 5: 통합 테스트 재작성

### 목표
이벤트 기반 아키텍처에 맞는 새로운 통합 테스트 작성

### 배경
기존 통합 테스트는 동기식 아키텍처(Order → Product 직접 호출) 기반으로 작성됨.
새 아키텍처에서는 Kafka 이벤트 기반 비동기 플로우를 테스트해야 함.

### 참조 문서
```
읽어야 할 문서:
1. /Users/groom/IdeaProjects/c4ang-contract-hub/event-flows/order-creation/success.md
2. /Users/groom/IdeaProjects/c4ang-contract-hub/event-flows/order-creation/stock-reservation-failed.md
```

### 체크리스트

#### 6.1 새로운 통합 테스트 작성
**신규 작성 대상**:
- [ ] `CreateOrderServiceIntegrationTest.kt`: ORDER_CREATED 상태로 생성, OrderCreatedEvent 발행 검증
- [ ] `OrderEventHandlerServiceIntegrationTest.kt`: Kafka 이벤트 수신 → 상태 변경 검증
- [ ] `StockReservedKafkaListenerIntegrationTest.kt`: stock.reserved 이벤트 처리 검증
- [ ] `StockReservationFailedKafkaListenerIntegrationTest.kt`: stock.reservation.failed 이벤트 처리 검증

**테스트 방식**:
```kotlin
// 1. 테스트 Kafka Producer로 이벤트 발행
// 2. Consumer가 이벤트 처리 후 DB 상태 확인
// 3. 후속 이벤트 발행 여부 확인 (MockKafkaProducer)
```

#### 6.2 정상 플로우 테스트
```
1. 클라이언트 → Order Service: POST /api/v1/orders
2. Order Service → Kafka: order.created 이벤트
3. Product Service ← Kafka: order.created 소비
4. Product Service → Redis: 재고 예약
5. Product Service → Kafka: stock.reserved 이벤트
6. Order Service ← Kafka: stock.reserved 소비
7. Order Service: 주문 상태 ORDER_CONFIRMED
8. Order Service → Kafka: order.confirmed 이벤트
```

#### 6.3 재고 부족 플로우 테스트
```
1. 클라이언트 → Order Service: POST /api/v1/orders
2. Order Service → Kafka: order.created 이벤트
3. Product Service ← Kafka: order.created 소비
4. Product Service → Redis: 재고 부족 확인
5. Product Service → Kafka: stock.reservation.failed 이벤트
6. Order Service ← Kafka: stock.reservation.failed 소비
7. Order Service: 주문 상태 ORDER_CANCELLED
8. Order Service → Kafka: order.cancelled 이벤트
```

#### 5.4 멱등성 테스트
- [ ] 동일 이벤트 중복 소비 시 정상 처리 확인
- [ ] ProcessedEvent 테이블 기록 확인

#### 5.5 타임아웃 테스트
- [ ] 재고 예약 만료 시 자동 복구 확인 (Product Service 스케줄러)

---

## Step 6: 정리 (주석 파일 삭제)

### 목표
주석 처리된 파일 완전 삭제 및 불필요한 의존성 정리

### 체크리스트

#### 6.1 주석 처리된 파일 삭제
- [ ] `domain/port/ProductPort.kt`
- [ ] `domain/model/ProductInfo.kt`
- [ ] `domain/model/StockReservation.kt`
- [ ] `domain/model/ReservationResult.kt`
- [ ] `domain/service/StockReservationManager.kt`
- [ ] `adapter/outbound/stock/StockReservationService.kt`
- [ ] `adapter/outbound/stock/RedissonStockReservationService.kt`
- [ ] `adapter/outbound/scheduler/StockReservationScheduler.kt`
- [ ] `adapter/outbound/client/ProductClient.kt`
- [ ] `adapter/outbound/client/ProductAdapter.kt`
- [ ] `adapter/outbound/client/ProductFeignClient.kt`
- [ ] `test/.../client/TestProductClient.kt`
- [ ] `test/.../scheduler/StockReservationSchedulerTest.kt`
- [ ] `test/.../scheduler/StockReservationSchedulerIntegrationTest.kt`

#### 6.2 build.gradle 의존성 정리 (Order Service)
- [ ] 불필요한 Redisson 의존성 제거 여부 검토 (다른 용도로 사용 중인지 확인)
- [ ] 불필요한 ShedLock 의존성 제거 여부 검토

#### 6.3 application.yml 정리
- [ ] 불필요한 Redis 설정 제거 여부 검토
- [ ] `feign.clients.product-service.url` 설정 제거

---

## 프롬프트 템플릿

### Step 1 실행 프롬프트
```
Product Service의 재고 예약 로직을 검증하고 Order Service에서 이관된 로직과 비교해주세요.

참조 문서:
1. Product Service 파일 인덱스: /Users/groom/IdeaProjects/c4ang-product-service/docs/FILE_INDEX.md
2. 이관 대상 코드: /Users/groom/IdeaProjects/c4ang-order-service/docs/refactoring/PRODUCT_SERVICE_MIGRATION_INDEX.md

확인 사항:
- OrderCreatedConsumer의 재고 예약 플로우
- Redis 재고 예약 로직 (CAS, TTL, 만료 인덱스)
- 만료 예약 스케줄러 존재 여부
- stock.reserved / stock.reservation.failed 이벤트 발행
```

### Step 2 실행 프롬프트
```
Order Service의 CreateOrderService를 리팩토링해주세요.

참조 문서:
- 수정 계획: /Users/groom/IdeaProjects/c4ang-order-service/docs/refactoring/PRODUCT_DECOUPLING_PLAN.md

작업 내용:
1. ProductPort, StorePort, StockReservationManager 의존성 제거
2. 상품 조회/검증/재고예약 로직 제거
3. CreateOrderCommand, CreateOrderRequest에 productName, unitPrice 필드 추가
4. OrderManager, OrderPolicy 수정
```

### Step 3 실행 프롬프트
```
Order Service에 Kafka Consumer를 추가해주세요.

참조 문서:
- 정상 플로우: /Users/groom/IdeaProjects/c4ang-contract-hub/event-flows/order-creation/success.md
- 실패 플로우: /Users/groom/IdeaProjects/c4ang-contract-hub/event-flows/order-creation/stock-reservation-failed.md

구현 사항:
1. StockReservedEventConsumer: stock.reserved 토픽 → ORDER_CONFIRMED
2. StockReservationFailedEventConsumer: stock.reservation.failed 토픽 → ORDER_CANCELLED
3. 멱등성 보장 (eventId 기반)
```

---

## 변경 이력

| 날짜 | 작성자 | 내용 |
|------|--------|------|
| 2025-12-05 | Claude | 초기 문서 작성 |
| 2025-12-05 | Claude | Step 3.5 (테스트 코드 수정) 추가, Step 4 통합 테스트 재작성 내용 보완 |
