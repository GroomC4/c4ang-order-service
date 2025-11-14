package com.groom.order.common.exception

/**
 * API 에러 코드 상수
 *
 * 클라이언트가 에러 타입을 프로그래밍적으로 구분할 수 있도록 제공하는 에러 코드입니다.
 * 각 코드는 구체적인 비즈니스 에러 상황을 표현합니다.
 */
object ErrorCode {
    // ========== 인증/인가 관련 에러 ==========

    // 토큰 관련
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED" // 토큰 만료
    const val INVALID_TOKEN_SIGNATURE = "INVALID_TOKEN_SIGNATURE" // 토큰 서명 검증 실패
    const val INVALID_TOKEN_FORMAT = "INVALID_TOKEN_FORMAT" // 토큰 형식 오류
    const val INVALID_TOKEN_ALGORITHM = "INVALID_TOKEN_ALGORITHM" // 토큰 알고리즘 불일치
    const val INVALID_TOKEN_ISSUER = "INVALID_TOKEN_ISSUER" // 토큰 발급자 불일치
    const val MISSING_TOKEN_CLAIM = "MISSING_TOKEN_CLAIM" // 토큰 필수 클레임 누락
    const val MISSING_TOKEN = "MISSING_TOKEN" // 토큰 없음

    // 사용자 인증
    const val USER_NOT_FOUND_BY_EMAIL = "USER_NOT_FOUND_BY_EMAIL" // 이메일로 사용자를 찾을 수 없음
    const val INVALID_PASSWORD = "INVALID_PASSWORD" // 비밀번호 불일치
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS" // 인증 정보 불일치

    // 리프레시 토큰
    const val REFRESH_TOKEN_NOT_FOUND = "REFRESH_TOKEN_NOT_FOUND" // 리프레시 토큰 없음
    const val REFRESH_TOKEN_EXPIRED = "REFRESH_TOKEN_EXPIRED" // 리프레시 토큰 만료
    const val REFRESH_TOKEN_MISMATCH = "REFRESH_TOKEN_MISMATCH" // 리프레시 토큰 불일치

    // 권한
    const val ACCESS_DENIED = "ACCESS_DENIED" // 접근 권한 없음
    const val INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS" // 권한 부족

    // ========== 사용자 관련 에러 ==========
    const val DUPLICATE_EMAIL = "DUPLICATE_EMAIL" // 이메일 중복
    const val USER_NOT_FOUND = "USER_NOT_FOUND" // 사용자 없음
    const val USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS" // 사용자 이미 존재
    const val INSUFFICIENT_PERMISSION = "INSUFFICIENT_PERMISSION" // 권한 부족

    // ========== 스토어 관련 에러 ==========
    const val DUPLICATE_STORE = "DUPLICATE_STORE" // 스토어 중복
    const val STORE_NOT_FOUND = "STORE_NOT_FOUND" // 스토어 없음
    const val STORE_ALREADY_DELETED = "STORE_ALREADY_DELETED" // 스토어 이미 삭제됨
    const val STORE_ALREADY_SUSPENDED = "STORE_ALREADY_SUSPENDED" // 스토어 이미 정지됨
    const val INVALID_STORE_STATUS_TRANSITION = "INVALID_STORE_STATUS_TRANSITION" // 스토어 상태 변경 불가
    const val STORE_ACCESS_DENIED = "STORE_ACCESS_DENIED" // 스토어 접근 권한 없음
    const val CANNOT_UPDATE_SUSPENDED_STORE = "CANNOT_UPDATE_SUSPENDED_STORE" // 일시정지된 스토어 수정 불가
    const val CANNOT_UPDATE_DELETED_STORE = "CANNOT_UPDATE_DELETED_STORE" // 삭제된 스토어 수정 불가
    const val STORE_REGISTRATION_REQUIRED = "STORE_REGISTRATION_REQUIRED" // 스토어 등록 필요

    // ========== 상품 관련 에러 ==========
    const val PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND" // 상품 없음
    const val PRODUCT_ACCESS_DENIED = "PRODUCT_ACCESS_DENIED" // 상품 접근 권한 없음
    const val PRODUCT_ALREADY_DELETED = "PRODUCT_ALREADY_DELETED" // 상품 이미 삭제됨
    const val PRODUCT_ALREADY_HIDDEN = "PRODUCT_ALREADY_HIDDEN" // 상품 이미 숨김 처리됨
    const val DUPLICATE_PRODUCT_NAME = "DUPLICATE_PRODUCT_NAME" // 상품명 중복
    const val PRODUCT_DESCRIPTION_GENERATION_FAILED = "PRODUCT_DESCRIPTION_GENERATION_FAILED" // AI 상품 설명 생성 실패
    const val INVALID_PRODUCT_DESCRIPTION_PROMPT = "INVALID_PRODUCT_DESCRIPTION_PROMPT" // 잘못된 상품 설명 프롬프트
    const val PROMPT_TOO_LONG = "PROMPT_TOO_LONG" // 프롬프트 길이 초과

    // ========== 주문 관련 에러 ==========
    const val DUPLICATE_ORDER_REQUEST = "DUPLICATE_ORDER_REQUEST" // 중복 주문 요청
    const val INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK" // 재고 부족
    const val STORE_CLOSED = "STORE_CLOSED" // 스토어 영업 중단
    const val ORDER_NOT_FOUND = "ORDER_NOT_FOUND" // 주문 없음
    const val CANNOT_CANCEL_ORDER = "CANNOT_CANCEL_ORDER" // 주문 취소 불가
    const val CANNOT_REFUND_ORDER = "CANNOT_REFUND_ORDER" // 주문 환불 불가
    const val ORDER_ACCESS_DENIED = "ORDER_ACCESS_DENIED" // 주문 접근 권한 없음

    // ========== 리소스 관련 에러 ==========
    const val RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND" // 리소스 없음
    const val RESOURCE_ALREADY_EXISTS = "RESOURCE_ALREADY_EXISTS" // 리소스 이미 존재
    const val RESOURCE_CONFLICT = "RESOURCE_CONFLICT" // 리소스 충돌

    // ========== 요청 관련 에러 ==========
    const val INVALID_REQUEST_PARAMETER = "INVALID_REQUEST_PARAMETER" // 요청 파라미터 오류
    const val INVALID_REQUEST_BODY = "INVALID_REQUEST_BODY" // 요청 바디 오류
    const val MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD" // 필수 필드 누락
    const val INVALID_REQUEST_STATE = "INVALID_REQUEST_STATE" // 잘못된 요청 상태

    // ========== 서버 에러 ==========
    const val INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR" // 내부 서버 오류
    const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE" // 서비스 이용 불가
}
