# 통합 테스트 시나리오 정리

## 아키텍처 변경 요약

### 이전 방식 (동기)
```
API Request → Order Service → Product Service (HTTP) → Redis 재고 예약 → Response
```
- 주문 생성 시 즉시 재고 예약
- 상태: `PENDING` → `STOCK_RESERVED`

### 새로운 방식 (비동기 이벤트)
```
API Request → Order Service → Kafka (order.created)
                                      ↓
                              Product Service → Redis 재고 예약
                                      ↓
                              Kafka (stock.reserved)
                                      ↓
                              Order Service → 상태 업데이트
```
- 주문 생성 시 이벤트 발행만 수행
- 상태: `ORDER_CREATED` → (이벤트) → `ORDER_CONFIRMED`

---

## 통합 테스트 대상 및 시나리오

### 1. OrderQueryControllerIntegrationTest (유지)
**목적**: 주문 조회 API 비즈니스 로직 검증

| 시나리오 | 변경 사항 |
|---------|----------|
| 전체 주문 목록 조회 | 상태값 `STOCK_RESERVED` → `ORDER_CONFIRMED` |
| 상태별 필터링 | `ORDER_CONFIRMED`, `PAYMENT_COMPLETED` 등 |
| 주문 상세 조회 | 변경 없음 |
| 권한 검증 (다른 사용자 주문) | 변경 없음 |

**필요 작업**: SQL 스크립트의 상태값 변경 완료 ✅

---

### 2. OrderCommandControllerIntegrationTest (@Disabled)
**목적**: 주문 생성/취소/환불 API 비즈니스 로직 검증

**이전 시나리오**:
- POST /orders → 즉시 `STOCK_RESERVED` 반환
- PATCH /orders/{id}/cancel → 직접 Redis 예약 취소

**새로운 시나리오**:
- POST /orders → `ORDER_CREATED` 반환 (재고 예약은 비동기)
- PATCH /orders/{id}/cancel → 이벤트 발행 (재고 복구는 Product Service)

**필요 작업**:
- `@Disabled` 유지 또는 이벤트 기반 테스트로 재작성
- 주문 생성 시 `ORDER_CREATED` 상태 검증으로 변경

---

### 3. CreateOrderServiceIntegrationTest (@Disabled)
**목적**: 주문 생성 서비스 로직 검증

**이전 시나리오**:
- 동기 재고 예약 성공/실패
- Redis 재고 차감 검증

**새로운 시나리오**:
- 주문 생성 → `ORDER_CREATED` 상태
- `order.created` 이벤트 발행 검증
- 재고 예약은 Product Service 책임

**필요 작업**: 이벤트 발행 검증 테스트로 재작성

---

### 4. CancelOrderServiceIntegrationTest (수정 필요)
**목적**: 주문 취소 서비스 로직 검증

| 시나리오 | 이전 | 새로운 |
|---------|------|-------|
| 취소 가능 상태 | `STOCK_RESERVED`, `PAYMENT_PENDING` | `ORDER_CONFIRMED`, `PAYMENT_PENDING`, `PREPARING` |
| 재고 복구 | 직접 Redis 복구 | `order.cancelled` 이벤트 발행 |

**필요 작업**: 상태값 변경 완료 ✅, 재고 복구 검증 로직 제거 필요

---

### 5. GetOrderDetailServiceIntegrationTest (수정 완료)
**목적**: 주문 상세 조회 서비스 검증

**필요 작업**: 상태값 변경 완료 ✅

---

### 6. ListOrdersServiceIntegrationTest (수정 완료)
**목적**: 주문 목록 조회 서비스 검증

**필요 작업**: 상태값 변경 완료 ✅

---

### 7. OrderTimeoutSchedulerIntegrationTest (수정 필요)
**목적**: 타임아웃 스케줄러 검증

**이전 시나리오**:
- 만료 주문 → `PAYMENT_TIMEOUT` + 직접 Redis 예약 취소

**새로운 시나리오**:
- 만료 주문 → `PAYMENT_TIMEOUT` + `OrderTimeoutEvent` 발행
- 재고 복구는 Product Service 책임

**필요 작업**:
- Redis 예약 취소 검증 제거
- 이벤트 발행 검증 추가

---

### 8. Kafka Consumer 테스트 (새로 추가됨)
**목적**: Product/Payment Service 이벤트 소비 검증

| Consumer | 이벤트 | 검증 사항 |
|----------|--------|----------|
| StockReservedKafkaListener | `stock.reserved` | `ORDER_CREATED` → `ORDER_CONFIRMED` |
| StockReservationFailedKafkaListener | `stock.reservation.failed` | `ORDER_CREATED` → `ORDER_CANCELLED` |
| PaymentCompletedKafkaListener | `payment.completed` | `PAYMENT_PENDING` → `PREPARING` |
| PaymentFailedKafkaListener | `payment.failed` | 주문 취소 + 이벤트 발행 |

**필요 작업**: 단위 테스트 존재, 통합 테스트 선택적 추가

---

## 수정 우선순위

### 즉시 수정 (테스트 통과 목표)
1. ✅ DB 스키마 CHECK constraint 업데이트
2. ✅ SQL 스크립트 상태값 변경
3. **OrderTimeoutSchedulerIntegrationTest** - Redis 검증 제거

### 추후 재작성 (Step 5)
4. CreateOrderServiceIntegrationTest - 이벤트 발행 검증
5. OrderCommandControllerIntegrationTest - 이벤트 기반 플로우

---

## 테스트 데이터 상태값 매핑

| 이전 상태 | 새로운 상태 | 설명 |
|----------|------------|------|
| `PENDING` | `ORDER_CREATED` | 주문 생성됨 (재고 확인 대기) |
| `STOCK_RESERVED` | `ORDER_CONFIRMED` | 재고 예약 완료 |
| 나머지 | 동일 | 변경 없음 |
