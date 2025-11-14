# 로컬 개발환경 가이드

## 개요

Order Service는 MSA 환경에서 Istio API Gateway를 통한 인증/인가를 사용합니다.
로컬 개발 시 Istio 없이도 개발할 수 있도록 `LocalDevAuthFilter`가 자동으로 인증 헤더를 주입합니다.

## 로컬 개발환경 설정

### 1. 프로파일 활성화

로컬 개발 시 `local` 프로파일을 활성화합니다:

```bash
# Gradle 사용
./gradlew bootRun --args='--spring.profiles.active=local'

# 또는 IDE에서 실행 시
Active profiles: local
```

### 2. LocalDevAuthFilter 동작 방식

`local` 프로파일이 활성화되면 `LocalDevAuthFilter`가 자동으로 활성화되어 다음과 같이 동작합니다:

#### 기본 동작 (헤더 없이 요청)
```bash
curl http://localhost:8082/api/v1/orders
```

자동으로 다음 헤더가 주입됩니다:
- `X-User-Id: 00000000-0000-0000-0000-000000000001`
- `X-User-Role: CUSTOMER`

#### 커스텀 헤더 사용 (테스트용)
```bash
curl -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
     -H "X-User-Role: OWNER" \
     http://localhost:8082/api/v1/orders
```

기존 헤더가 있으면 그대로 사용됩니다.

### 3. 로그 확인

`local` 프로파일에서는 DEBUG 레벨 로그가 활성화됩니다:

```yaml
logging:
  level:
    com.groom.order: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
```

헤더 주입 로그 예시:
```
DEBUG c.g.o.c.c.LocalDevAuthFilter - Injecting mock Istio headers for local development: userId=00000000-0000-0000-0000-000000000001, role=CUSTOMER
```

## 프로파일별 설정

### local 프로파일
- **활성화**: `--spring.profiles.active=local`
- **특징**:
  - LocalDevAuthFilter 활성화 (자동 헤더 주입)
  - DEBUG 레벨 로그
  - 로컬 데이터베이스 연결 (localhost:15432)
  - Feign Client: localhost 서비스 호출

### dev/staging/prod 프로파일
- **특징**:
  - LocalDevAuthFilter 비활성화
  - Istio API Gateway 필수
  - X-User-Id, X-User-Role 헤더가 없으면 500 에러 발생

## 테스트 사용자

### 기본 Mock 사용자
```yaml
User ID: 00000000-0000-0000-0000-000000000001
Role: CUSTOMER
```

### 커스텀 테스트 사용자
필요에 따라 다른 사용자 ID와 역할로 테스트할 수 있습니다:

```bash
# OWNER 역할 테스트
curl -H "X-User-Id: 22222222-2222-2222-2222-222222222222" \
     -H "X-User-Role: OWNER" \
     http://localhost:8082/api/v1/orders

# 다른 CUSTOMER 테스트
curl -H "X-User-Id: 33333333-3333-3333-3333-333333333333" \
     -H "X-User-Role: CUSTOMER" \
     http://localhost:8082/api/v1/orders
```

## API 테스트 예시

### Swagger UI
로컬 환경에서 Swagger UI를 통해 테스트할 수 있습니다:

```
http://localhost:8082/swagger-ui/index.html
```

자동으로 Mock 헤더가 주입되므로 별도의 인증 없이 API 테스트가 가능합니다.

### cURL 테스트

#### 주문 생성
```bash
curl -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": "bbbbbbbb-bbbb-bbbb-bbbb-000000000001",
    "items": [
      {
        "productId": "aaaaaaaa-aaaa-aaaa-aaaa-000000000001",
        "quantity": 2
      }
    ],
    "idempotencyKey": "test-key-123",
    "note": "문 앞에 놔주세요"
  }'
```

#### 주문 목록 조회
```bash
curl http://localhost:8082/api/v1/orders
```

#### 주문 상세 조회
```bash
curl http://localhost:8082/api/v1/orders/{orderId}
```

## 주의사항

### ⚠️ LocalDevAuthFilter는 local 프로파일에서만 활성화됩니다

운영 환경에서는 반드시 Istio API Gateway를 통해 요청이 들어와야 하며,
X-User-Id, X-User-Role 헤더가 없으면 500 Internal Server Error가 발생합니다.

### ⚠️ 인가는 Istio Gateway에서 처리

현재 구현에서는 인가(Authorization) 로직이 없습니다.
- @PreAuthorize 어노테이션 제거됨
- 역할 기반 접근 제어는 Istio Gateway에서 처리
- 로컬 개발 시 모든 역할로 테스트 가능

### ⚠️ 테스트 코드

통합 테스트에서도 Istio 헤더를 명시적으로 주입해야 합니다:

```kotlin
mockMvc.perform(
    get("/api/v1/orders")
        .header("X-User-Id", userId.toString())
        .header("X-User-Role", "CUSTOMER")
)
```

## 문제 해결

### LocalDevAuthFilter가 동작하지 않을 때
1. 프로파일 확인: `spring.profiles.active=local`이 설정되었는지 확인
2. 로그 확인: `LocalDevAuthFilter` 로그가 출력되는지 확인
3. 필터 순서: `@Order(Ordered.HIGHEST_PRECEDENCE)`로 최우선 실행됨

### 500 에러가 발생할 때
- Istio 헤더가 없는 경우: LocalDevAuthFilter 활성화 여부 확인
- IllegalStateException: IstioHeaderExtractor에서 헤더를 찾지 못함

### 의존성 오류
Product, Store 도메인 의존성이 필요한 경우 해당 모듈을 포함하거나 Mock 구현을 사용합니다.

## 관련 파일

- `LocalDevAuthFilter.kt`: 로컬 개발용 인증 필터
- `IstioHeaderExtractor.kt`: Istio 헤더 추출 유틸리티
- `application-local.yml`: 로컬 프로파일 설정
- `application.yml`: 기본 설정
