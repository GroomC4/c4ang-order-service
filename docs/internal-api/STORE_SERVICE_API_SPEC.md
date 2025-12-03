# Store Service Internal API Specification

## 개요

Order Service가 Store Service를 호출하기 위한 Internal API 스펙입니다.

> **인증**: Internal API는 서비스 간 통신 전용이므로 별도 인증이 필요하지 않습니다.

---

## API 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/internal/api/v1/stores/{storeId}` | 스토어 조회 |
| GET | `/internal/api/v1/stores/{storeId}/exists` | 스토어 존재 여부 확인 |

---

## 1. 스토어 조회

### Request

```
GET /internal/api/v1/stores/{storeId}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| storeId | UUID | Yes | 스토어 ID |

### Response

#### 200 OK - 성공

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "name": "스타벅스 강남점",
  "status": "ACTIVE"
}
```

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | 스토어 ID |
| name | String | 스토어명 |
| status | String | 스토어 상태 (ACTIVE, INACTIVE, SUSPENDED) |

#### 404 Not Found - 스토어 미존재

```json
{
  "error": "STORE_NOT_FOUND",
  "message": "Store not found with id: 660e8400-e29b-41d4-a716-446655440001"
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

## 2. 스토어 존재 여부 확인

### Request

```
GET /internal/api/v1/stores/{storeId}/exists
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| storeId | UUID | Yes | 스토어 ID |

### Response

#### 200 OK - 존재함

```json
{
  "exists": true
}
```

#### 200 OK - 존재하지 않음

```json
{
  "exists": false
}
```

| Field | Type | Description |
|-------|------|-------------|
| exists | Boolean | 스토어 존재 여부 |

#### 500 Internal Server Error - 서버 오류

```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred"
}
```

---

## 응답 케이스 정리

### 스토어 조회 (GET /internal/api/v1/stores/{storeId})

| 케이스 | HTTP Status | Error Code | 설명 |
|--------|-------------|------------|------|
| 성공 | 200 | - | 스토어 정보 반환 |
| 스토어 미존재 | 404 | STORE_NOT_FOUND | 해당 ID의 스토어가 없음 |
| 잘못된 UUID 형식 | 400 | BAD_REQUEST | storeId 형식 오류 |
| 서버 오류 | 500 | INTERNAL_SERVER_ERROR | 예상치 못한 오류 |

### 스토어 존재 여부 확인 (GET /internal/api/v1/stores/{storeId}/exists)

| 케이스 | HTTP Status | Error Code | 설명 |
|--------|-------------|------------|------|
| 존재함 | 200 | - | exists: true 반환 |
| 존재하지 않음 | 200 | - | exists: false 반환 |
| 잘못된 UUID 형식 | 400 | BAD_REQUEST | storeId 형식 오류 |
| 서버 오류 | 500 | INTERNAL_SERVER_ERROR | 예상치 못한 오류 |

---

## Order Service 에러 처리

| Store Service 응답 | Order Service 처리 |
|---------------------|-------------------|
| 200 OK | 정상 처리 |
| 404 Not Found | `StoreException.StoreNotFound` 예외 발생 |
| 400 Bad Request | `IllegalArgumentException` 예외 발생 |
| 5xx Server Error | 로그 기록 후 예외 전파 |
| 타임아웃 | `FeignException` 예외 전파 |

---

## DTO 정의

### StoreResponse

```kotlin
data class StoreResponse(
    val id: UUID,
    val name: String,
    val status: String
)
```

### StoreExistsResponse

```kotlin
data class StoreExistsResponse(
    val exists: Boolean
)
```

### ErrorResponse

```kotlin
data class ErrorResponse(
    val error: String,
    val message: String
)
```

---

## Store Status 값

| Status | Description |
|--------|-------------|
| ACTIVE | 영업 중인 스토어 |
| INACTIVE | 휴업 중인 스토어 |
| SUSPENDED | 정지된 스토어 |
