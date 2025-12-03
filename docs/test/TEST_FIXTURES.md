# 테스트 Fixtures 가이드

## 개요

통합 테스트에서 외부 서비스(Product Service, Store Service) 호출을 대체하는 stub 데이터 정의입니다.

테스트 환경(`@Profile("test")`)에서는 실제 HTTP 호출 대신 미리 정의된 테스트 데이터를 반환합니다.

---

## 아키텍처

```
Production 환경                          Test 환경
─────────────────                        ─────────────────
ProductClient (interface)                ProductClient (interface)
       ↑                                        ↑
ProductFeignClient                       TestProductClient (@Primary)
(실제 HTTP 호출)                          (stub 데이터 반환)
       ↓                                        ↓
ProductAdapter ─────────────────────── ProductAdapter (동일)
       ↓                                        ↓
ProductPort                              ProductPort
```

> **핵심**: Adapter는 프로덕션/테스트 동일하게 사용되며, Client만 교체됩니다.
> TestClient가 `@Primary`로 주입되어 자동으로 stub 데이터를 반환합니다.

---

## 테스트 데이터

### Product (상품)

| Product ID | Store ID | 상품명 | 가격 | 설명 |
|------------|----------|--------|------|------|
| `aaaaaaaa-aaaa-aaaa-aaaa-000000000001` | `bbbbbbbb-...-000000000001` | Gaming Mouse | 29,000원 | Store 1 소속 |
| `aaaaaaaa-aaaa-aaaa-aaaa-000000000002` | `bbbbbbbb-...-000000000001` | Mechanical Keyboard | 89,000원 | Store 1 소속 |
| `aaaaaaaa-aaaa-aaaa-aaaa-000000000003` | `bbbbbbbb-...-000000000002` | Limited Edition Mouse | 49,000원 | Store 2 소속 |

**미존재 상품 테스트용**: `99999999-9999-9999-9999-999999999999`

### Store (스토어)

| Store ID | 스토어명 | 상태 | 설명 |
|----------|----------|------|------|
| `bbbbbbbb-bbbb-bbbb-bbbb-000000000001` | Test Store 1 | ACTIVE | 정상 영업 중 |
| `bbbbbbbb-bbbb-bbbb-bbbb-000000000002` | Test Store 2 | ACTIVE | 정상 영업 중 |
| `bbbbbbbb-bbbb-bbbb-bbbb-000000000003` | Inactive Store | INACTIVE | 휴업 중 |

**미존재 스토어 테스트용**: `99999999-9999-9999-9999-999999999999`

---

## 사용 방법

### 1. 통합 테스트에서 사용

통합 테스트는 자동으로 `TestProductClient`, `TestStoreClient`를 사용합니다.

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class OrderIntegrationTest {

    @Test
    fun `주문 생성 성공`() {
        // TestProductClient.PRODUCT_MOUSE 사용
        val productId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        val storeId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")

        // 테스트 로직...
    }
}
```

### 2. 상수 직접 참조 (권장)

```kotlin
import com.groom.order.adapter.outbound.client.TestProductClient
import com.groom.order.adapter.outbound.client.TestStoreClient

class MyTest {
    @Test
    fun `상품 조회 테스트`() {
        // 상수로 정의된 테스트 ID 사용
        val productId = TestProductClient.PRODUCT_MOUSE
        val storeId = TestStoreClient.STORE_1

        // 미존재 케이스 테스트
        val notFoundId = TestProductClient.NON_EXISTENT_PRODUCT
    }
}
```

### 3. 단위 테스트에서 Mocking

단위 테스트에서는 MockK 등으로 직접 mocking합니다:

```kotlin
@Test
fun `상품 조회 - 미존재`() {
    val productClient = mockk<ProductClient>()
    every { productClient.getProduct(any()) } returns null

    // 테스트 로직...
}
```

---

## 테스트 시나리오별 가이드

### 주문 생성 성공 테스트

```kotlin
// Store 1에 속한 상품으로 주문 생성
val storeId = TestStoreClient.STORE_1
val productId = TestProductClient.PRODUCT_MOUSE  // Store 1 소속

// 주문 생성 요청...
```

### 상품 미존재 테스트

```kotlin
// 존재하지 않는 상품 ID 사용
val productId = TestProductClient.NON_EXISTENT_PRODUCT

// 주문 생성 시 ProductNotFoundException 예상
```

### 스토어 미존재 테스트

```kotlin
// 존재하지 않는 스토어 ID 사용
val storeId = TestStoreClient.NON_EXISTENT_STORE

// 주문 생성 시 StoreNotFoundException 예상
```

### 상품-스토어 불일치 테스트

```kotlin
// Store 2의 상품을 Store 1 주문에 넣기
val storeId = TestStoreClient.STORE_1
val productId = TestProductClient.PRODUCT_LOW_STOCK  // Store 2 소속!

// 주문 생성 시 스토어 불일치 예외 예상
```

### 비활성 스토어 테스트

```kotlin
// 휴업 중인 스토어
val storeId = TestStoreClient.STORE_INACTIVE

// 비즈니스 정책에 따라 처리
```

---

## 파일 위치

| 파일 | 설명 |
|------|------|
| `src/main/.../client/ProductClient.kt` | Product Client 인터페이스 |
| `src/main/.../client/ProductFeignClient.kt` | Product Feign 구현체 (HTTP) |
| `src/main/.../client/ProductAdapter.kt` | ProductPort 구현체 |
| `src/test/.../client/TestProductClient.kt` | Product stub 구현체 |
| `src/main/.../client/StoreClient.kt` | Store Client 인터페이스 |
| `src/main/.../client/StoreFeignClient.kt` | Store Feign 구현체 (HTTP) |
| `src/main/.../client/StoreAdapter.kt` | StorePort 구현체 |
| `src/test/.../client/TestStoreClient.kt` | Store stub 구현체 |

---

## 주의사항

1. **ID 형식**: 테스트 ID는 패턴을 따릅니다
   - Product: `aaaaaaaa-aaaa-aaaa-aaaa-00000000000X`
   - Store: `bbbbbbbb-bbbb-bbbb-bbbb-00000000000X`
   - 미존재: `99999999-9999-9999-9999-999999999999`

2. **데이터 일관성**: `TestProductClient`와 `TestStoreClient`의 Store ID가 일치해야 합니다.

3. **새 테스트 데이터 추가**: 필요시 `TestProductClient.Companion` 또는 `TestStoreClient.Companion`에 추가하고 이 문서도 업데이트하세요.
