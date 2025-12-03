package com.groom.order.adapter.outbound.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.UUID

/**
 * Store Service Feign Client
 *
 * Store 도메인의 Internal REST API를 호출하기 위한 Feign Client 구현체입니다.
 * MSA 환경에서 Store Service와 HTTP로 통신합니다.
 *
 * Internal API는 서비스 간 통신 전용이므로 별도 인증이 필요하지 않습니다.
 */
@FeignClient(
    name = "store-service",
    url = "\${feign.clients.store-service.url}",
)
interface StoreFeignClient : StoreClient {
    /**
     * 스토어 단건 조회
     *
     * @param storeId 스토어 ID
     * @return 스토어 정보 DTO (미존재 시 FeignException 404 발생)
     */
    @GetMapping("/internal/api/v1/stores/{storeId}")
    override fun getStore(
        @PathVariable storeId: UUID,
    ): StoreClient.StoreResponse?

    /**
     * 스토어 존재 여부 확인
     *
     * @param storeId 스토어 ID
     * @return 존재 여부 응답
     */
    @GetMapping("/internal/api/v1/stores/{storeId}/exists")
    override fun existsStore(
        @PathVariable storeId: UUID,
    ): StoreClient.ExistsResponse
}
