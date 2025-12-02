package com.groom.order.adapter.outbound.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.UUID

/**
 * Store Service HTTP Client (Feign)
 *
 * Store 도메인의 REST API를 호출하기 위한 Feign Client 인터페이스입니다.
 * MSA 환경에서 Store Service와 통신합니다.
 */
@FeignClient(
    name = "store-service",
    url = "\${feign.clients.store-service.url}",
)
interface StoreClient {
    /**
     * 스토어 단건 조회
     *
     * @param storeId 스토어 ID
     * @return 스토어 정보 DTO
     */
    @GetMapping("/api/v1/stores/{storeId}")
    fun getStore(
        @PathVariable storeId: UUID,
    ): StoreResponse?

    /**
     * 스토어 존재 여부 확인
     *
     * @param storeId 스토어 ID
     * @return 존재 여부 응답
     */
    @GetMapping("/api/v1/stores/{storeId}/exists")
    fun existsStore(
        @PathVariable storeId: UUID,
    ): ExistsResponse

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
