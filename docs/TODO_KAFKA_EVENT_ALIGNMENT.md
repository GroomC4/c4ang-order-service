# Kafka 이벤트 정렬 작업 문서

## 문서 개요

**작성일**: 2025-12-05
**목적**: contract-hub 문서 기준으로 Order Service의 Kafka 이벤트 발행/소비 구현 정렬
**참조 문서**:
- `c4ang-contract-hub/docs/interface/kafka-event-specifications.md` (v2.0)
- `c4ang-contract-hub/docs/interface/kafka-event-sequence.md`

---

## 1. 현재 구현 상태 요약

### 1.1 발행(Publish) 이벤트

| 토픽 | 이벤트 | 상태 | 비고 |
|------|--------|------|------|
| `order.created` | OrderCreated | ✅ 구현됨 | |
| `order.confirmed` | OrderConfirmed | ✅ 구현됨 | |
| `order.cancelled` | OrderCancelled | ✅ 구현됨 | |
| `order.expiration.notification` | OrderExpirationNotification | ✅ 구현됨 | |
| `analytics.daily.statistics` | DailyStatistics | ✅ 구현됨 | 토픽명 확인 필요 |
| `order.stock.confirmed` | StockConfirmed | ❌ 미구현 | Payment Saga 완료 알림 |
| `saga.stock-confirmation.failed` | StockConfirmFailed | ❌ 미구현 | SAGA 실패 이벤트 |
| `saga.order-confirmation.compensate` | OrderConfirmationCompensate | ❌ 미구현 | SAGA 보상 이벤트 |

### 1.2 소비(Consume) 이벤트

| 토픽 | 이벤트 | 상태 | 비고 |
|------|--------|------|------|
| `stock.reserved` | StockReserved | ✅ 구현됨 | |
| `stock.reservation.failed` | StockReservationFailed | ⚠️ 토픽명 불일치 | 문서: `saga.stock-reservation.failed` |
| `payment.completed` | PaymentCompleted | ✅ 구현됨 | |
| `payment.failed` | PaymentFailed | ✅ 구현됨 | |
| `payment.cancelled` | PaymentCancelled | ⚠️ 용도 확인 필요 | 문서: `saga.payment-completion.compensate` |
| `saga.payment-initialization.failed` | PaymentInitializationFailed | ❌ 미구현 | 결제 대기 생성 실패 처리 |

---

## 2. 문서 기준 차이점 상세

### 2.1 토픽 네이밍 불일치

문서 v2.0에서는 SAGA 패턴 이벤트에 `saga.*` 접두사를 사용합니다.

| 현재 구현 | 문서 기준 (v2.0) | 변경 필요 |
|----------|-----------------|----------|
| `stock.reservation.failed` | `saga.stock-reservation.failed` | ✅ Yes |
| `payment.cancelled` | `saga.payment-completion.compensate` (보상 시) | ⚠️ 검토 필요 |
| `daily.statistics` | `analytics.daily.statistics` | ✅ Yes |

### 2.2 미구현 발행 이벤트

#### 2.2.1 `order.stock.confirmed` (StockConfirmed)

**용도**: Payment Saga 완료 시 재고 확정 성공 알림

**시나리오**:
```
payment.completed 수신 → Order Service: 재고 확정 → order.stock.confirmed 발행 → Payment Service: Saga 완료 처리
```

**필요한 구현**:
- `OrderEventPublisher.publishStockConfirmed()` 메서드 추가
- `KafkaOrderEventPublisher`에 구현체 추가
- `OrderEventHandlerService.handlePaymentCompleted()`에서 발행

#### 2.2.2 `saga.stock-confirmation.failed` (StockConfirmFailed)

**용도**: 재고 확정 실패 시 Payment Service에 보상 트랜잭션 트리거

**시나리오**:
```
payment.completed 수신 → Order Service: 재고 확정 실패 → saga.stock-confirmation.failed 발행 → Payment Service: 결제 취소
```

**필요한 구현**:
- `OrderEventPublisher.publishStockConfirmationFailed()` 메서드 추가
- `KafkaOrderEventPublisher`에 구현체 추가
- `OrderEventHandlerService.handlePaymentCompleted()`에서 실패 시 발행

#### 2.2.3 `saga.order-confirmation.compensate`

**용도**: 결제 실패 시 Product Service에 재고 복원 요청

**현재 상황**: `order.cancelled` 이벤트를 통해 처리 중

**문서 권장**: SAGA 보상 이벤트로 분리

### 2.3 미구현 소비 이벤트

#### 2.3.1 `saga.payment-initialization.failed`

**용도**: Payment Service에서 결제 대기 생성 실패 시 처리

**시나리오**:
```
order.confirmed 발행 → Payment Service: 결제 대기 생성 실패 → saga.payment-initialization.failed 발행 → Order Service: 주문 취소 + 재고 복원
```

**필요한 구현**:
- `PaymentInitializationFailedKafkaListener` 클래스 생성
- `OrderEventHandler.handlePaymentInitializationFailed()` 메서드 추가
- `OrderEventHandlerService`에 구현체 추가

---

## 3. 작업 목록

### 3.1 Phase 1: 토픽 네이밍 정렬 (우선순위: 높음) ✅ 완료

| # | 작업 | 파일 | 상태 |
|---|------|------|------|
| 1.1 | `stock.reservation.failed` → `saga.stock-reservation.failed` 변경 | `StockReservationFailedKafkaListener.kt` | ✅ |
| 1.2 | `daily.statistics` → `analytics.daily.statistics` 변경 | `KafkaTopicProperties.kt`, `KafkaOrderEventPublisher.kt` | ✅ |
| 1.3 | KafkaTopicProperties에 SAGA 토픽 추가 | `KafkaTopicProperties.kt` | ✅ |

### 3.2 Phase 2: Payment Saga 이벤트 구현 (우선순위: 높음) ✅ 완료

| # | 작업 | 파일 | 상태 |
|---|------|------|------|
| 2.1 | `publishStockConfirmed()` 인터페이스 추가 | `OrderEventPublisher.kt` | ✅ |
| 2.2 | `publishStockConfirmed()` 구현체 추가 | `KafkaOrderEventPublisher.kt` | ✅ |
| 2.3 | `publishStockConfirmationFailed()` 인터페이스 추가 | `OrderEventPublisher.kt` | ✅ |
| 2.4 | `publishStockConfirmationFailed()` 구현체 추가 | `KafkaOrderEventPublisher.kt` | ✅ |
| 2.5 | `handlePaymentCompleted()` 수정 - 재고 확정 이벤트 발행 | `OrderEventHandlerService.kt` | ✅ |

### 3.3 Phase 3: SAGA 보상 이벤트 구현 (우선순위: 중간) ✅ 완료

| # | 작업 | 파일 | 상태 |
|---|------|------|------|
| 3.1 | `PaymentInitializationFailedKafkaListener` 생성 | 새 파일 | ✅ |
| 3.2 | `handlePaymentInitializationFailed()` 인터페이스 추가 | `OrderEventHandler.kt` | ✅ |
| 3.3 | `handlePaymentInitializationFailed()` 구현체 추가 | `OrderEventHandlerService.kt` | ✅ |
| 3.4 | `publishOrderConfirmationCompensate()` 구현 검토 | `OrderEventPublisher.kt` | ⏸️ (order.cancelled로 처리) |

### 3.4 Phase 4: Consumer Group 분리 (우선순위: 낮음) ⏸️ 보류

문서 권장:
- `order-service-saga-compensation`: SAGA 보상 이벤트 처리
- `order-service-saga-payment`: Payment Saga 이벤트 처리

| # | 작업 | 파일 | 상태 |
|---|------|------|------|
| 4.1 | Consumer Group 분리 전략 수립 | - | ⏸️ TODO로 남김 |
| 4.2 | KafkaConsumerConfig 수정 | `KafkaConsumerConfig.kt` | ⏸️ TODO로 남김 |

> **참고**: 현재 단일 Consumer Group(`order-service`)으로도 정상 동작합니다.
> 추후 성능 최적화 또는 장애 격리가 필요할 때 분리를 검토합니다.

---

## 4. 이벤트 플로우 다이어그램

### 4.1 Order Creation Saga ✅ 구현 완료

```
Client → Order Service: POST /orders
         │
         ├─ order.created ─────────────────→ Product Service
         │                                       │
         │ ← stock.reserved ─────────────────────┘ (성공)
         │ ← saga.stock-reservation.failed ──────┘ (실패) ✅
         │
         ├─ order.confirmed ───────────────→ Payment Service
         │                                       │
         │ ← saga.payment-initialization.failed ─┘ (실패) ✅
         │
         └─ [주문 확정 대기]
```

### 4.2 Payment Saga ✅ 구현 완료

```
Payment Service: payment.completed ────→ Order Service
                                              │
                                              ├─ 재고 확정 성공
                                              │   └─ order.stock.confirmed ────→ Payment Service ✅
                                              │
                                              └─ 재고 확정 실패
                                                  └─ saga.stock-confirmation.failed ─→ Payment Service ✅
                                                         │
                                                         └─ saga.payment-completion.compensate ─→ Order Service
                                                                │
                                                                └─ saga.order-confirmation.compensate ─→ Product Service
```

---

## 5. Avro 스키마 참조

구현 시 contract-hub의 Avro 스키마를 참조하세요:

| 이벤트 | 스키마 경로 |
|--------|------------|
| StockConfirmed | `kafka-schemas/src/main/avro/order/StockConfirmed.avsc` |
| StockConfirmFailed | `kafka-schemas/src/main/avro/order/StockConfirmFailed.avsc` |
| PaymentInitializationFailed | `kafka-schemas/src/main/avro/saga/PaymentInitializationFailed.avsc` |

---

## 6. 테스트 시나리오

### 6.1 필수 테스트

- [ ] 재고 확정 성공 시 `order.stock.confirmed` 발행 확인
- [ ] 재고 확정 실패 시 `saga.stock-confirmation.failed` 발행 확인
- [ ] `saga.payment-initialization.failed` 수신 시 주문 취소 + 재고 복원 확인
- [ ] 토픽 네이밍 변경 후 기존 Consumer 정상 동작 확인

### 6.2 통합 테스트

- [ ] Payment Saga 전체 흐름 (성공 케이스)
- [ ] Payment Saga 보상 트랜잭션 (실패 케이스)

---

## 7. 참고 사항

### 7.1 하위 호환성

토픽 네이밍 변경 시 기존 토픽과의 하위 호환성을 고려해야 합니다:
- 마이그레이션 기간 동안 양쪽 토픽 모두 구독 가능하도록 설정
- 점진적 마이그레이션 권장

### 7.2 문서 참조

자세한 SAGA 패턴 및 보상 트랜잭션 설계는 다음 문서를 참조하세요:
- `c4ang-contract-hub/docs/interface/kafka-event-specifications.md` Section 2.8
- `c4ang-contract-hub/docs/interface/kafka-event-sequence.md` Section 4-6

---

**문서 업데이트**: 이 문서는 작업 진행에 따라 업데이트됩니다.
**담당자**: Order Service 개발팀
**문의**: #order-service 채널
