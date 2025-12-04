# Product Service Internal API Specification

## 개요

Order Service가 Product Service를 호출하기 위한 Internal API 스펙입니다.

> **인증**: Internal API는 서비스 간 통신 전용이므로 별도 인증이 필요하지 않습니다.

---

## API 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/internal/v1/products/{productId}` | 상품 단건 조회 |
| POST | `/internal/v1/products/search` | 상품 다건 조회 |

---

## 1. 상품 단건 조회

### Request

```
GET /internal/v1/products/{productId}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| productId | UUID | Yes | 상품 ID |

### Response

#### 200 OK - 성공

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "storeId": "660e8400-e29b-41d4-a716-446655440001",
  "name": "아메리카노",
  "storeName": "스타벅스 강남점",
  "price": 4500.00
}
```

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | 상품 ID |
| storeId | UUID | 스토어 ID |
| name | String | 상품명 |
| storeName | String | 스토어명 |
| price | BigDecimal | 상품 가격 |

#### 404 Not Found - 상품 미존재

```json
{
  "error": "PRODUCT_NOT_FOUND",
  "message": "Product not found with id: 550e8400-e29b-41d4-a716-446655440000"
}
```

#### 500 Internal Server Error - 서버 오류

```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred"
}
```

---

## 2. 상품 다건 조회

### Request

```
POST /internal/v1/products/search
Content-Type: application/json
```

#### Request Body

```json
{
  "ids": [
    "550e8400-e29b-41d4-a716-446655440000",
    "550e8400-e29b-41d4-a716-446655440002"
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| ids | List\<UUID\> | Yes | 조회할 상품 ID 목록 |

### Response

#### 200 OK - 성공

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "storeId": "660e8400-e29b-41d4-a716-446655440001",
    "name": "아메리카노",
    "storeName": "스타벅스 강남점",
    "price": 4500.00
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "storeId": "660e8400-e29b-41d4-a716-446655440001",
    "name": "카페라떼",
    "storeName": "스타벅스 강남점",
    "price": 5000.00
  }
]
```

> **참고**: 요청한 상품 중 존재하지 않는 상품은 응답 목록에 포함되지 않습니다.

#### 200 OK - 빈 목록 (모든 상품 미존재)

```json
[]
```

#### 400 Bad Request - 잘못된 요청

```json
{
  "error": "BAD_REQUEST",
  "message": "Invalid product ID format"
}
```

#### 500 Internal Server Error - 서버 오류

```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred"
}
```

---

## 응답 케이스 정리

### 상품 단건 조회 (GET /internal/v1/products/{productId})

| 케이스 | HTTP Status | Error Code | 설명 |
|--------|-------------|------------|------|
| 성공 | 200 | - | 상품 정보 반환 |
| 상품 미존재 | 404 | PRODUCT_NOT_FOUND | 해당 ID의 상품이 없음 |
| 잘못된 UUID 형식 | 400 | BAD_REQUEST | productId 형식 오류 |
| 서버 오류 | 500 | INTERNAL_SERVER_ERROR | 예상치 못한 오류 |

### 상품 다건 조회 (POST /internal/v1/products/search)

| 케이스 | HTTP Status | Error Code | 설명 |
|--------|-------------|------------|------|
| 성공 (전체) | 200 | - | 모든 상품 정보 반환 |
| 성공 (일부) | 200 | - | 존재하는 상품만 반환 |
| 성공 (빈 목록) | 200 | - | 빈 배열 반환 |
| 잘못된 UUID 형식 | 400 | BAD_REQUEST | Request Body의 ids 형식 오류 |
| 서버 오류 | 500 | INTERNAL_SERVER_ERROR | 예상치 못한 오류 |

---

## Order Service 에러 처리

| Product Service 응답 | Order Service 처리 |
|---------------------|-------------------|
| 200 OK | 정상 처리 |
| 404 Not Found | `ProductException.ProductNotFound` 예외 발생 |
| 400 Bad Request | `IllegalArgumentException` 예외 발생 |
| 5xx Server Error | 로그 기록 후 예외 전파 |
| 타임아웃 | `FeignException` 예외 전파 |

---

## DTO 정의

### ProductSearchRequest

```kotlin
data class ProductSearchRequest(
    val ids: List<UUID>
)
```

### ProductResponse

```kotlin
data class ProductResponse(
    val id: UUID,
    val storeId: UUID,
    val name: String,
    val storeName: String,
    val price: BigDecimal
)
```

### ErrorResponse

```kotlin
data class ErrorResponse(
    val error: String,
    val message: String
)
```
