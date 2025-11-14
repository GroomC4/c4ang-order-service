# Order Domain - 비동기 주문-결제 플로우

## 개요
비동기 주문-결제 플로우를 구현한 Order 도메인입니다.
Redis 기반 재고 예약 시스템과 Spring Events를 활용한 이벤트 기반 아키텍처로 구성되어 있습니다.

## 주요 테이블 구조

### p_order (주문 메인)
```sql
- id (UUID PK)
- user_id (UUID NOT NULL)
- store_id (UUID NOT NULL)
- order_number (TEXT UNIQUE)
- status (18개 상태 - OrderStatus enum 참조)
-- 비동기 플로우 추가 컬럼
- reservation_id (TEXT) -- Redis 예약 ID
- payment_id (UUID) -- 결제 레코드 ID
- expires_at (TIMESTAMPTZ) -- 결제 제한 시간 (10분)
- confirmed_at (TIMESTAMPTZ) -- 주문 확정 시각
- cancelled_at (TIMESTAMPTZ) -- 취소 시각
- failure_reason (TEXT) -- 실패/취소 사유
- refund_id (TEXT) -- PG 환불 ID
```

### p_order_audit (감사 로그)
```sql
- id (UUID PK)
- order_id (UUID NOT NULL)
- event_type (TEXT) -- ORDER_CREATED, STOCK_RESERVED, ...
- change_summary (TEXT) -- 변경 사항 요약
- actor_user_id (UUID) -- 변경 수행자
- recorded_at (TIMESTAMPTZ)
- metadata (JSONB) -- 추가 정보
```

### p_stock_reservation_log (재고 예약 백업)
```sql
- id (UUID PK)
- reservation_id (TEXT UNIQUE)
- order_id (UUID NOT NULL)
- store_id (UUID NOT NULL)
- products (JSONB) -- 예약 상품 목록
- status (TEXT) -- RESERVED, CONFIRMED, RELEASED, EXPIRED
- expires_at (TIMESTAMPTZ)
```

## 패키지 구조

### domain/model/
- **Order.kt**: 애그리게이트 루트, 비즈니스 메서드 (reserveStock, cancel, refund, timeout 등)
- **OrderItem.kt**: 주문 상품 엔티티
- **OrderStatus.kt**: 18개 상태 enum (PENDING → STOCK_RESERVED → PAYMENT_PENDING → ...)
- **OrderAudit.kt**: 감사 로그 엔티티
- **OrderAuditEventType.kt**: 감사 이벤트 타입 enum
- **ReservationResult.kt**: 재고 예약 결과 value object

### domain/event/ (8개 도메인 이벤트)
- **OrderCreatedEvent.kt**: 주문 생성
- **StockReservedEvent.kt**: 재고 예약
- **PaymentRequestedEvent.kt**: 결제 요청
- **PaymentCompletedEvent.kt**: 결제 완료
- **OrderConfirmedEvent.kt**: 주문 확정
- **OrderCancelledEvent.kt**: 주문 취소
- **OrderRefundedEvent.kt**: 환불 완료
- **OrderTimeoutEvent.kt**: 결제 타임아웃

### domain/service/
- **OrderAuditRecorder.kt**: 감사 로그 기록 (독립 트랜잭션)

### application/service/
- **CreateOrderService.kt**: 주문 생성 + Redis 재고 예약
- **GetOrderDetailService.kt**: 주문 상세 조회
- **ListOrdersService.kt**: 주문 목록 조회
- **CancelOrderService.kt**: 주문 취소 + 재고 복구
- **RefundOrderService.kt**: 환불 처리

### application/event/ (8개 이벤트 핸들러)
- **OrderCreatedEventHandler.kt**
- **StockReservedEventHandler.kt**
- **PaymentRequestedEventHandler.kt**
- **PaymentCompletedEventHandler.kt**
- **OrderConfirmedEventHandler.kt**
- **OrderCancelledEventHandler.kt**
- **OrderRefundedEventHandler.kt**
- **OrderTimeoutEventHandler.kt**

### infrastructure/repository/
- **OrderRepositoryImpl.kt**: JpaRepository + findExpiredOrders()
- **OrderAuditRepositoryImpl.kt**: JpaRepository

### infrastructure/scheduler/
- **OrderTimeoutScheduler.kt**: 결제 타임아웃 자동 처리 (매 1분)
- **StockReservationScheduler.kt**: 만료 예약 자동 복구 (매 5분)

### infrastructure/stock/
- **StockReservationService.kt**: 재고 예약 인터페이스
- **RedisStockReservationService.kt**: Redis 기반 구현 (Lua 스크립트)

## 비동기 플로우

```
1. 주문 생성 → Redis 재고 예약 (10분 TTL) → STOCK_RESERVED
2. 결제 요청 → PAYMENT_PENDING
3. 결제 완료 → PAYMENT_COMPLETED → 예약 확정
4. 주문 확정 → PREPARING (상점 알림)
5-A. 타임아웃 → PAYMENT_TIMEOUT (재고 자동 복구)
5-B. 취소 요청 → ORDER_CANCELLED (재고 복구)
5-C. 환불 요청 → REFUND_COMPLETED (배송 완료 후)
```

## 감사 로그 자동 기록
모든 도메인 이벤트는 p_order_audit 테이블에 자동으로 기록됩니다.
- 이벤트 타입, 변경 사항 요약, 수행자, 메타데이터 포함
- 독립 트랜잭션(REQUIRES_NEW)으로 실패해도 기록 보장
