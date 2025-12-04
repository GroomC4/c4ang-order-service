# Contract Test 가이드

## 개요

Consumer Driven Contract(CDC) 테스트에서 Consumer(Order Service)가 Producer(Product/Store Service)의 Stub을 사용하여 API 계약을 검증하는 방법을 설명합니다.

---

## Contract Test란?

```
Producer (Product Service)           Consumer (Order Service)
──────────────────────────           ─────────────────────────
Contract 기반 Stub JAR 생성    →     Stub Runner로 Stub 로드
         ↓                                    ↓
   Maven 저장소에 배포          →     WireMock 서버 자동 시작
                                              ↓
                                    FeignClient가 WireMock 호출
                                              ↓
                                    Contract 준수 여부 검증
```

**목적**: Producer API가 변경되어도 Consumer가 기대하는 계약이 유지되는지 검증

---

## contract-test 프로필이 필요한 이유

### 문제 1: 불필요한 인프라 초기화

`test` 프로필 사용 시 Testcontainers가 PostgreSQL, Redis를 시작합니다.

```
Contract Test 실행 (test 프로필)
    → Testcontainers 시작 (PostgreSQL, Redis)
    → 30초+ 소요
    → 실제로 DB를 사용하지 않음
    → 리소스 낭비
```

### 문제 2: TestClient와 FeignClient 충돌

`test` 프로필에서는 `TestProductClient`가 `@Primary`로 등록되어 실제 FeignClient를 대체합니다.

```kotlin
// test 프로필에서 활성화
@Component
@Profile("test")
@Primary
class TestProductClient : ProductClient {
    // Stub 데이터 반환 (WireMock 호출 안 함)
}
```

Contract Test는 **실제 FeignClient가 WireMock을 호출**해야 하므로 충돌이 발생합니다.

```
test 프로필                          contract-test 프로필
─────────────                        ────────────────────
ProductClient                        ProductClient
      ↓                                    ↓
TestProductClient (@Primary)         ProductFeignClient
      ↓                                    ↓
Stub 데이터 반환                      WireMock 서버 호출
      ↓                                    ↓
❌ Contract 검증 불가                 ✅ Contract 검증 가능
```

### 해결: contract-test 프로필 분리

| 프로필 | 용도 | 인프라 | Client |
|--------|------|--------|--------|
| `test` | 단위/통합 테스트 | DB, Redis 필요 | TestClient (stub) |
| `contract-test` | Contract 검증 | 불필요 | FeignClient (WireMock) |

---

## 설정 파일

### application-contract-test.yml

```yaml
# ====================================
# Contract Test 프로필
# ====================================
# Stub Runner가 WireMock 서버를 띄우고, FeignClient가 해당 서버로 요청합니다.

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 5000
  # JPA/DataSource 비활성화 (Contract Test는 DB 불필요)
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.redisson.spring.starter.RedissonAutoConfigurationV2

# Feign Client 설정 (Stub Runner가 제공하는 WireMock 서버 포트 사용)
feign:
  clients:
    product-service:
      url: http://localhost:8083
    store-service:
      url: http://localhost:8084

# Stub Runner 설정
stubrunner:
  ids-to-service-ids:
    product-api: product-service
    store-api: store-service

logging:
  level:
    org.springframework.cloud.contract: DEBUG
    com.groom.order.adapter.outbound.client: DEBUG
```

### 주요 설정 설명

| 설정 | 목적 |
|------|------|
| `spring.autoconfigure.exclude` | DB, Redis, JPA 자동 설정 비활성화 |
| `feign.clients.*.url` | WireMock 서버 주소 (Stub Runner가 해당 포트에 서버 시작) |
| `stubrunner.ids-to-service-ids` | Stub artifact ID와 Feign Client name 매핑 |

---

## 테스트 코드 구조

### ProductClientContractTest.kt

```kotlin
@Tag("contract-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("contract-test")  // contract-test 프로필 사용
@AutoConfigureStubRunner(
    ids = ["io.github.groomc4:product-api:+:stubs:8083"],
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
)
class ProductClientContractTest {

    @Autowired
    private lateinit var productFeignClient: ProductFeignClient  // 실제 FeignClient 주입

    @Test
    fun `상품 단건 조회 - 성공`() {
        val response = productFeignClient.getProduct(EXISTING_PRODUCT_ID)

        response.shouldNotBeNull()
        response.id shouldBe EXISTING_PRODUCT_ID
    }
}
```

### @AutoConfigureStubRunner 설정

```kotlin
@AutoConfigureStubRunner(
    ids = ["io.github.groomc4:product-api:+:stubs:8083"],
    //     ─────────────────────────────────────────────
    //     groupId:artifactId:version:classifier:port
    //
    //     +: 최신 버전
    //     stubs: stub classifier
    //     8083: WireMock 서버 포트

    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    // LOCAL: ~/.m2/repository에서 stub 조회
    // REMOTE: repositoryRoot에서 stub 다운로드
)
```

---

## 실행 방법

### Gradle 태스크

```bash
# Contract Test만 실행
./gradlew contractTest

# 일반 테스트 실행 (Contract Test 제외)
./gradlew test
```

### build.gradle.kts 설정

```kotlin
// Contract Test 전용 태스크 (Stub Runner 기반)
val contractTest by tasks.registering(Test::class) {
    description = "Runs contract tests with Stub Runner"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("contract-test")  // @Tag("contract-test") 테스트만 실행
    }
}
```

---

## 사전 조건

### Producer가 Stub JAR 배포 필요

```bash
# Product Service에서 실행
./gradlew publishStubsPublicationToMavenLocal

# 또는 원격 저장소에 배포
./gradlew publish
```

### Stub JAR 위치 확인

```bash
# 로컬 Maven 저장소
ls ~/.m2/repository/io/github/groomc4/product-api/*/product-api-*-stubs.jar
```

---

## 프로필별 비교

| 항목 | test | contract-test |
|------|------|---------------|
| **용도** | 비즈니스 로직 테스트 | API 계약 검증 |
| **DB 초기화** | O (Testcontainers) | X |
| **Redis 초기화** | O (Testcontainers) | X |
| **Client** | TestClient (stub) | FeignClient (WireMock) |
| **실행 시간** | 느림 (30초+) | 빠름 (5초) |
| **Gradle 태스크** | `test` | `contractTest` |
| **JUnit 태그** | - | `contract-test` |

---

## 트러블슈팅

### Stub JAR를 찾을 수 없음

```
No stub found for io.github.groomc4:product-api:+:stubs
```

**해결**: Producer에서 Stub JAR 배포 확인

```bash
ls ~/.m2/repository/io/github/groomc4/product-api/
```

### WireMock 포트 충돌

```
Port 8083 is already in use
```

**해결**: 다른 포트 사용 또는 기존 프로세스 종료

```kotlin
@AutoConfigureStubRunner(
    ids = ["io.github.groomc4:product-api:+:stubs:18083"],  // 포트 변경
)
```

### TestClient가 주입됨

```
Expected WireMock call but got stub data
```

**해결**: `@ActiveProfiles("contract-test")` 확인 (test가 아닌 contract-test)

---

## 파일 위치

```
order-api/
├── build.gradle.kts                          # contractTest 태스크, stub-runner 의존성
└── src/test/
    ├── kotlin/.../contract/
    │   ├── ProductClientContractTest.kt      # Product 계약 테스트
    │   └── StoreClientContractTest.kt        # Store 계약 테스트
    └── resources/
        ├── application-test.yml              # 일반 테스트 설정
        └── application-contract-test.yml     # Contract 테스트 설정
```
