# Order Service 외부 서비스 의존성

## 개요

Order Service가 다른 도메인 서비스를 호출하는 부분을 정리한 문서입니다.

서비스 간 호출은 **Internal API**를 통해 이루어집니다. Internal API는 외부 공개 API와 분리된 서비스 간 통신 전용 API입니다.

> **인증**: Internal API 호출 시 별도의 인증 과정이 필요하지 않습니다. API Gateway (Istio)를 통해 이미 인증을 거친 후이므로, 서비스 간 호출에는 인증 헤더 없이 요청합니다.

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                       Order Service                             │
├─────────────────────────────────────────────────────────────────┤
│  Application Layer (CreateOrderService)                         │
│     ↓                                                           │
│  Port Interface (ProductPort, StorePort) [Domain Boundary]      │
│     ↓                                                           │
│  Adapter (ProductAdapter, StoreAdapter)                         │
│     ↓                                                           │
│  Feign Client (ProductClient, StoreClient) [HTTP Communication] │
└─────────────────────────────────────────────────────────────────┘
            ↓                              ↓
    Product Service API          Store Service API
```

**Hexagonal Architecture (포트 & 어댑터 패턴)**을 적용하여 도메인 로직이 외부 서비스 구현에 의존하지 않도록 설계되어 있습니다.

---

## 1. Product Service 의존성

### 호출 목적

주문 생성 시 **상품 정보 조회** 및 **상품 유효성 검증**을 위해 Product Service를 호출합니다.

### 사용 위치

| 계층 | 파일 | 용도 |
|------|------|------|
| Domain Port | `domain/port/ProductPort.kt` | 상품 조회 인터페이스 정의 |
| Application | `application/service/CreateOrderService.kt` | 주문 생성 시 상품 검증 |
| Adapter | `adapter/outbound/client/ProductAdapter.kt` | Port 구현체 |
| Adapter | `adapter/outbound/client/ProductClient.kt` | Feign Client |

### Port 인터페이스

```kotlin
interface ProductPort {
    fun loadById(productId: UUID): ProductInfo?
    fun loadAllById(productIds: List<UUID>): List<ProductInfo>
}
```

### 호출 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/products/{productId}` | 상품 단건 조회 |
| GET | `/api/v1/products?ids=id1,id2,...` | 상품 다건 조회 |

### 응답 DTO

```
ProductResponse
├── id: UUID              # 상품 ID
├── storeId: UUID         # 스토어 ID
├── name: String          # 상품명
├── storeName: String     # 스토어명
└── price: BigDecimal     # 상품 가격
```

### 도메인 모델 변환

```
ProductInfo (Value Object)
├── id: UUID
├── storeId: UUID
├── storeName: String
├── name: String
└── price: BigDecimal
```

### 환경별 설정

| 환경 | 설정값 |
|------|--------|
| 로컬 (local) | `http://localhost:8083` |
| k3d (dev) | `http://product-api:8083` |
| 운영 (prod) | `${PRODUCT_SERVICE_URL}` |
| 테스트 (test) | `TestProductAdapter` 사용 |

### 비즈니스 로직

1. 주문 생성 요청 수신
2. `ProductPort.loadById(productId)` 호출
3. 상품 존재 여부 확인
4. 상품이 해당 스토어에 속하는지 검증
5. 상품 가격으로 주문 총액 계산
6. 주문 항목에 상품 정보 저장

### 에러 처리

| 상황 | 처리 |
|------|------|
| 상품 미존재 (404) | `ProductException.ProductNotFound` 발생 |
| 스토어 불일치 | `IllegalArgumentException` 발생 |
| 통신 오류 | 로그 기록 후 예외 전파 |

---

## 2. Store Service 의존성

### 호출 목적

주문 생성 시 **스토어 존재 여부 확인** 및 **스토어 정보 조회**를 위해 Store Service를 호출합니다.

### 사용 위치

| 계층 | 파일 | 용도 |
|------|------|------|
| Domain Port | `domain/port/StorePort.kt` | 스토어 조회 인터페이스 정의 |
| Application | `application/service/CreateOrderService.kt` | 주문 생성 시 스토어 검증 |
| Adapter | `adapter/outbound/client/StoreAdapter.kt` | Port 구현체 |
| Adapter | `adapter/outbound/client/StoreClient.kt` | Feign Client |

### Port 인터페이스

```kotlin
interface StorePort {
    fun loadById(storeId: UUID): StoreInfo?
    fun existsById(storeId: UUID): Boolean
}
```

### 호출 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/stores/{storeId}` | 스토어 조회 |
| GET | `/api/v1/stores/{storeId}/exists` | 스토어 존재 여부 확인 |

### 응답 DTO

```
StoreResponse
├── id: UUID          # 스토어 ID
├── name: String      # 스토어명
└── status: String    # 스토어 상태 (ACTIVE, INACTIVE 등)
```

### 도메인 모델 변환

```
StoreInfo (Value Object)
├── id: UUID
├── name: String
└── status: String
```

### 환경별 설정

| 환경 | 설정값 |
|------|--------|
| 로컬 (local) | `http://localhost:8084` |
| k3d (dev) | `http://store-api:8084` |
| 운영 (prod) | `${STORE_SERVICE_URL}` |
| 테스트 (test) | `TestStoreAdapter` 사용 |

### 비즈니스 로직

1. 주문 생성 요청 수신
2. `StorePort.existsById(storeId)` 호출
3. 스토어 존재 여부 확인
4. 미존재 시 예외 발생
5. 존재 시 주문 진행

### 에러 처리

| 상황 | 처리 |
|------|------|
| 스토어 미존재 (404) | `StoreException.StoreNotFound` 발생 |
| 스토어 비활성 | 비즈니스 정책에 따라 처리 |
| 통신 오류 | 로그 기록 후 예외 전파 |

---

## 3. 주문 생성 플로우

```
                         CreateOrderService
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
   StorePort              ProductPort         StockReservation
   (스토어 검증)           (상품 조회)           (재고 예약)
        │                      │                      │
        ▼                      ▼                      ▼
 Store Service API     Product Service API      Redis
```

### 상세 단계

1. **멱등성 확인**: 중복 주문 요청 방지 (`idempotencyKey`)
2. **스토어 검증**: `storePort.existsById(storeId)` - 스토어 존재 확인
3. **상품 조회**: `productPort.loadById(productId)` - 각 주문 항목의 상품 정보 조회
4. **상품 검증**: 모든 상품이 해당 스토어에 속하는지 확인
5. **재고 예약**: Redis를 통한 원자적 재고 차감 (10분 TTL)
6. **주문 생성**: 주문 엔티티 생성 및 저장
7. **이벤트 발행**: `OrderCreatedEvent` 도메인 이벤트 발행

---

## 4. Feign Client 설정

### 공통 설정 (application.yml)

```yaml
feign:
  clients:
    product-service:
      url: http://localhost:8083
      connect-timeout: 5000
      read-timeout: 5000
      logger-level: basic
    store-service:
      url: http://localhost:8084
      connect-timeout: 5000
      read-timeout: 5000
      logger-level: basic
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 5000
        loggerLevel: basic
```

### 개발 환경 (application-dev.yml)

```yaml
feign:
  clients:
    product-service:
      url: ${PRODUCT_SERVICE_URL:http://product-api:8083}
      logger-level: full  # 상세 로깅
    store-service:
      url: ${STORE_SERVICE_URL:http://store-api:8084}
      logger-level: full
```

### 운영 환경 (application-prod.yml)

```yaml
feign:
  clients:
    product-service:
      url: ${PRODUCT_SERVICE_URL}  # 환경변수 필수
      connect-timeout: 3000  # 더 짧은 타임아웃
      read-timeout: 3000
      logger-level: basic
    store-service:
      url: ${STORE_SERVICE_URL}
      connect-timeout: 3000
      read-timeout: 3000
      logger-level: basic
```

---

## 5. 테스트 전략

### 단위 테스트

Port 인터페이스를 MockK로 모킹하여 테스트합니다:

```kotlin
val productPort = mockk<ProductPort>()
val storePort = mockk<StorePort>()

every { storePort.existsById(storeId) } returns true
every { productPort.loadById(productId) } returns mockProduct
```

### 통합 테스트

테스트 전용 Adapter를 사용합니다:

| 테스트 Adapter | 설명 |
|---------------|------|
| `TestProductAdapter` | 고정된 상품 정보 반환 |
| `TestStoreAdapter` | 고정된 스토어 정보 반환 |

---

## 6. 향후 개선 사항

### Consumer Contract Test (예정)

Product Service와 Store Service의 Internal API 계약을 검증하는 Consumer Contract Test 추가 예정

**예상 Contract 정의 위치**:
- Product Service: `product-api/src/test/resources/contracts.internal/`
- Store Service: `store-api/src/test/resources/contracts.internal/`

### Circuit Breaker (예정)

Resilience4j를 활용한 서킷 브레이커 패턴 적용 예정

```kotlin
@CircuitBreaker(name = "productService", fallbackMethod = "fallback")
fun loadById(productId: UUID): ProductInfo?
```

### Retry 정책 (예정)

일시적 오류에 대한 재시도 정책 적용 예정

```yaml
feign:
  client:
    config:
      default:
        retryer: feign.Retryer.Default
```

---

## 참고

- Product Service 저장소: https://github.com/GroomC4/c4ang-product-service
- Store Service 저장소: https://github.com/GroomC4/c4ang-store-service
- Spring Cloud OpenFeign 문서: https://spring.io/projects/spring-cloud-openfeign
