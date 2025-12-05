# Kafka Consumer 구현 작업 목록

## 현재 상태
- **브랜치**: `feature/adding-kafka-consumer-event`
- **상태**: ✅ 구현 완료 (빌드 및 테스트 통과)

---

## 완료된 작업

### 1. KafkaConsumerConfig.kt ✅
- 위치: `order-api/src/main/kotlin/com/groom/order/configuration/kafka/`
- Avro SpecificRecord 역직렬화 설정
- Schema Registry 연동
- 수동 커밋 모드 (MANUAL_IMMEDIATE)
- DefaultErrorHandler (3회 재시도, 1초 간격)

### 2. KafkaTopicProperties.kt 업데이트 ✅
- Consumer 토픽 추가:
  - `stock-reserved: stock.reserved`
  - `payment-completed: payment.completed`
  - `payment-failed: payment.failed`
  - `payment-cancelled: payment.cancelled`

### 3. OrderEventHandler 포트 인터페이스 ✅
- 위치: `order-api/src/main/kotlin/com/groom/order/domain/port/`
- 메서드:
  - `handleStockReserved()`
  - `handlePaymentCompleted()`
  - `handlePaymentFailed()`
  - `handlePaymentCancelled()`

### 4. OrderEventHandlerService 구현 ✅
- 위치: `order-api/src/main/kotlin/com/groom/order/application/service/`
- OrderEventHandler 인터페이스 구현
- 주문 상태 업데이트 및 후속 이벤트 발행

### 5. Kafka Listener 클래스 ✅
- 위치: `order-api/src/main/kotlin/com/groom/order/adapter/inbound/messaging/`
- `StockReservedKafkaListener.kt`
- `PaymentCompletedKafkaListener.kt`
- `PaymentFailedKafkaListener.kt`
- `PaymentCancelledKafkaListener.kt`

### 6. application.yml 업데이트 ✅
- Consumer 설정 추가 (group-id, auto-offset-reset 등)
- Consumer 토픽 설정 추가

### 7. 단위 테스트 작성 ✅
- `OrderEventHandlerServiceTest.kt` (19개 테스트)
- `StockReservedKafkaListenerTest.kt`
- `PaymentCompletedKafkaListenerTest.kt`
- `PaymentFailedKafkaListenerTest.kt`
- `PaymentCancelledKafkaListenerTest.kt`

---

## 향후 개선 사항 (Optional)

### 1. 통합 테스트 (우선순위: 중간)
- Embedded Kafka를 사용한 통합 테스트
- 또는 Testcontainers Kafka 사용

### 2. 에러 핸들링 개선 (우선순위: 낮음)
- Dead Letter Queue (DLQ) 설정
- 재시도 실패 시 알림 발송

### 3. 모니터링 설정 (우선순위: 낮음)
- Consumer lag 모니터링
- 처리 성공/실패 메트릭

---

## 이벤트 플로우 요약

```
Product Service                    Order Service                     Payment Service
     │                                  │                                  │
     │    StockReserved                 │                                  │
     ├─────────────────────────────────>│                                  │
     │                                  │ PENDING → STOCK_RESERVED         │
     │                                  │                                  │
     │                                  │    OrderConfirmed                │
     │                                  ├─────────────────────────────────>│
     │                                  │                                  │
     │                                  │    PaymentCompleted              │
     │                                  │<─────────────────────────────────┤
     │                                  │ PAYMENT_PENDING → PREPARING      │
     │                                  │                                  │
     │                                  │    PaymentFailed/Cancelled       │
     │                                  │<─────────────────────────────────┤
     │    OrderCancelled                │ → ORDER_CANCELLED                │
     │<─────────────────────────────────┤                                  │
     │    (재고 복원)                    │                                  │
```

---

## 참고 파일

### Avro 스키마 (contract-hub)
- `src/main/events/avro/product/StockReserved.avsc`
- `src/main/events/avro/payment/PaymentCompleted.avsc`
- `src/main/events/avro/payment/PaymentFailed.avsc`
- `src/main/events/avro/payment/PaymentCancelled.avsc`

### 관련 문서
- `docs/test/CONTRACT_TEST_GUIDE.md`

---

## 명령어 참고

```bash
# 빌드 (테스트 제외)
./gradlew clean build -x test

# 전체 테스트
./gradlew test

# 로컬 실행
SPRING_PROFILES_ACTIVE=local ./gradlew :order-api:bootRun
```
