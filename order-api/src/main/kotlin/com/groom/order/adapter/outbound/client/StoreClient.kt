package com.groom.order.adapter.outbound.client

import java.util.UUID

/**
 * Store Service Client 인터페이스
 *
 * Store 도메인과의 통신을 추상화한 인터페이스입니다.
 * HTTP(Feign), gRPC 등 다양한 구현체로 교체 가능합니다.
 */
interface StoreClient {
    /**
     * 스토어 단건 조회
     *
     * @param storeId 스토어 ID
     * @return 스토어 정보 DTO (미존재 시 null 또는 예외 발생)
     */
    fun getStore(storeId: UUID): StoreResponse?

    /**
     * 스토어 존재 여부 확인
     *
     * @param storeId 스토어 ID
     * @return 존재 여부 응답
     */
    fun existsStore(storeId: UUID): ExistsResponse

    /**
     * Store Service 응답 DTO
     */
    data class StoreResponse(
        val id: UUID,
        val name: String,
        val status: String,
    )

    /**
     * 존재 여부 응답 DTO
     */
    data class ExistsResponse(
        val exists: Boolean,
    )
}
