# Order Service

주문 생성, 조회, 취소, 환불 등 주문 생명주기 전반을 관리하는 마이크로서비스입니다.

## 기술 스택

- **Language**: Kotlin 1.9+
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL (Master-Replica 구성)
- **Cache**: Redis (Redisson)
- **Messaging**: Apache Kafka (Avro + Schema Registry)
- **Build**: Gradle (Kotlin DSL)

## 아키텍처

헥사고날 아키텍처(Ports & Adapters)를 기반으로 설계되었습니다.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Adapter Layer                                   │
│  ┌─────────────────────────┐              ┌─────────────────────────────┐   │
│  │     Inbound Adapters    │              │     Outbound Adapters       │   │
│  │  - REST Controllers     │              │  - JPA Repositories         │   │
│  │                         │              │  - Feign Clients            │   │
│  │                         │              │  - Kafka Publisher          │   │
│  │                         │              │  - Redis (Stock)            │   │
│  │                         │              │  - Schedulers               │   │
│  └───────────┬─────────────┘              └─────────────┬───────────────┘   │
└──────────────┼──────────────────────────────────────────┼───────────────────┘
               │                                          │
               ▼                                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Application Layer                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Use Cases (Services)           │  Event Handlers                   │    │
│  │  - CreateOrderService           │  - OrderCreatedEventHandler       │    │
│  │  - CancelOrderService           │  - OrderConfirmedEventHandler     │    │
│  │  - RefundOrderService           │  - OrderCancelledEventHandler     │    │
│  │  - GetOrderDetailService        │  - OrderTimeoutEventHandler       │    │
│  │  - ListOrdersService            │  - PaymentCompletedEventHandler   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────┘
               │                                          ▲
               ▼                                          │
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Domain Layer                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │  Aggregates     │  │  Domain Services│  │  Ports (Interfaces)         │  │
│  │  - Order        │  │  - OrderManager │  │  - LoadOrderPort            │  │
│  │  - OrderItem    │  │  - OrderPolicy  │  │  - SaveOrderPort            │  │
│  │  - OrderAudit   │  │  - OrderAudit   │  │  - ProductPort              │  │
│  │                 │  │    Recorder     │  │  - StorePort                │  │
│  │  Domain Events  │  │                 │  │  - OrderEventPublisher      │  │
│  │  - OrderCreated │  │                 │  │                             │  │
│  │  - OrderTimeout │  │                 │  │                             │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 패키지 구조

```
com.groom.order
├── adapter/                          # 외부 시스템과의 연결 어댑터
│   ├── inbound/                      # 들어오는 요청 처리
│   │   └── web/                      # REST API 컨트롤러
│   │       ├── OrderCommandController.kt   # 주문 생성/취소/환불 API
│   │       ├── OrderQueryController.kt     # 주문 조회 API
│   │       └── dto/                        # Request/Response DTO
│   │
│   └── outbound/                     # 외부 시스템 호출
│       ├── client/                   # 외부 서비스 호출 (Feign)
│       │   ├── ProductClient.kt          # Product Service 호출
│       │   ├── StoreClient.kt            # Store Service 호출
│       │   └── *Adapter.kt               # Port 구현체
│       │
│       ├── persistence/              # 데이터베이스 접근
│       │   ├── OrderJpaRepository.kt     # Order JPA Repository
│       │   ├── OrderPersistenceAdapter.kt # Port 구현체
│       │   └── OrderAudit*.kt            # 감사 로그 관련
│       │
│       ├── scheduler/                # 스케줄링 작업
│       │   ├── OrderTimeoutScheduler.kt      # 결제 타임아웃 처리
│       │   ├── StockReservationScheduler.kt  # 재고 예약 복구
│       │   └── DailyStatisticsScheduler.kt   # 일일 통계 집계
│       │
│       └── stock/                    # Redis 재고 관리
│           └── RedissonStockReservationService.kt
│
├── application/                      # 유스케이스 및 비즈니스 오케스트레이션
│   ├── dto/                          # Command/Query/Result DTO
│   │   ├── CreateOrderCommand.kt
│   │   ├── CreateOrderResult.kt
│   │   └── ...
│   │
│   ├── service/                      # 유스케이스 구현
│   │   ├── CreateOrderService.kt         # 주문 생성
│   │   ├── CancelOrderService.kt         # 주문 취소
│   │   ├── RefundOrderService.kt         # 주문 환불
│   │   ├── GetOrderDetailService.kt      # 주문 상세 조회
│   │   └── ListOrdersService.kt          # 주문 목록 조회
│   │
│   └── event/                        # 도메인 이벤트 핸들러
│       ├── OrderCreatedEventHandler.kt   # 주문 생성 이벤트 처리
│       ├── OrderCancelledEventHandler.kt # 주문 취소 이벤트 처리
│       ├── OrderTimeoutEventHandler.kt   # 주문 타임아웃 이벤트 처리
│       └── ...
│
├── domain/                           # 핵심 비즈니스 로직
│   ├── model/                        # 도메인 엔티티 및 값 객체
│   │   ├── Order.kt                      # 주문 애그리게이트 루트
│   │   ├── OrderItem.kt                  # 주문 상품
│   │   ├── OrderStatus.kt                # 주문 상태 enum
│   │   ├── OrderAudit.kt                 # 주문 감사 로그
│   │   └── ...
│   │
│   ├── event/                        # 도메인 이벤트
│   │   ├── OrderCreatedEvent.kt
│   │   ├── OrderConfirmedEvent.kt
│   │   ├── OrderCancelledEvent.kt
│   │   ├── OrderTimeoutEvent.kt
│   │   └── ...
│   │
│   ├── service/                      # 도메인 서비스
│   │   ├── OrderManager.kt               # 주문 상태 관리
│   │   ├── OrderPolicy.kt                # 주문 정책 검증
│   │   ├── OrderAuditRecorder.kt         # 감사 로그 기록
│   │   └── OrderNumberGenerator.kt       # 주문번호 생성
│   │
│   └── port/                         # 포트 인터페이스 (추상화)
│       ├── LoadOrderPort.kt              # 주문 조회
│       ├── SaveOrderPort.kt              # 주문 저장
│       ├── ProductPort.kt                # 상품 정보 조회
│       ├── StorePort.kt                  # 가게 정보 조회
│       └── OrderEventPublisher.kt        # 외부 이벤트 발행
│
├── configuration/                    # 설정 클래스
│   ├── jpa/                          # JPA/DataSource 설정
│   ├── kafka/                        # Kafka Producer 설정
│   └── event/                        # 도메인 이벤트 발행 설정
│
└── common/                           # 공통 유틸리티
    ├── exception/                    # 예외 처리
    ├── domain/                       # 공통 도메인 인터페이스
    └── util/                         # 유틸리티 클래스
```

## 주요 기능

### 주문 생명주기

```
PENDING → STOCK_RESERVED → PAYMENT_PENDING → PAYMENT_COMPLETED → PREPARING → SHIPPED → DELIVERED
                                    ↓
                            PAYMENT_TIMEOUT (자동 취소)
                                    ↓
                            ORDER_CANCELLED
```

### API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/orders` | 주문 생성 |
| GET | `/api/v1/orders` | 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 조회 |
| POST | `/api/v1/orders/{orderId}/cancel` | 주문 취소 |
| POST | `/api/v1/orders/{orderId}/refund` | 주문 환불 |

### 발행 이벤트 (Kafka)

| Topic | 설명 | 구독자 |
|-------|------|--------|
| `order.created` | 주문 생성됨 | Product Service (재고 예약) |
| `order.confirmed` | 주문 확정됨 | Payment Service (결제 대기) |
| `order.cancelled` | 주문 취소됨 | Product Service (재고 복원) |
| `order.expiration.notification` | 주문 만료 알림 | Notification Service |
| `daily.statistics` | 일일 통계 | Analytics Service |

### 스케줄러

| 스케줄러 | 주기 | 설명 |
|---------|------|------|
| `OrderTimeoutScheduler` | 1분마다 | 결제 대기 만료 주문 자동 취소 |
| `StockReservationScheduler` | 5분마다 | 고아 재고 예약 복구 |
| `DailyStatisticsScheduler` | 매일 자정 | 일일 주문 통계 집계 및 발행 |

## 레이어별 책임

### Domain Layer
- **비즈니스 규칙**: 주문 상태 전이, 정책 검증
- **외부 의존성 없음**: 순수 Kotlin 코드, 프레임워크 독립적
- **Port 정의**: 외부 시스템과의 계약(인터페이스) 정의

### Application Layer
- **유스케이스 조율**: 도메인 서비스, Port 호출 조합
- **트랜잭션 관리**: `@Transactional` 경계 설정
- **이벤트 핸들링**: 도메인 이벤트 수신 및 후속 처리

### Adapter Layer
- **기술 구현**: JPA, Feign, Kafka, Redis 등
- **Port 구현**: 도메인 Port 인터페이스의 구체적 구현
- **외부 시스템 연동**: HTTP 통신, 메시지 발행, DB 접근

## 실행 방법

```bash
# 개발 환경 실행
./gradlew :order-api:bootRun

# 테스트 실행
./gradlew :order-api:test

# 빌드
./gradlew :order-api:build
```

## 환경 변수

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `SPRING_DATASOURCE_URL` | PostgreSQL URL | `jdbc:postgresql://localhost:5432/order` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커 주소 | `localhost:9092` |
| `REDIS_HOST` | Redis 호스트 | `localhost` |

## 의존 서비스

- **Product Service**: 상품 정보 조회, 재고 확인
- **Store Service**: 가게 정보 조회
- **Payment Service**: 결제 처리 (이벤트 기반)
- **Notification Service**: 알림 발송 (이벤트 기반)
