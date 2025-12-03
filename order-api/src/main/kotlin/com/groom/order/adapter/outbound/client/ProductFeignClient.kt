package com.groom.order.adapter.outbound.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

/**
 * Product Service Feign Client
 *
 * Product 도메인의 Internal REST API를 호출하기 위한 Feign Client 구현체입니다.
 * MSA 환경에서 Product Service와 HTTP로 통신합니다.
 *
 * Internal API는 서비스 간 통신 전용이므로 별도 인증이 필요하지 않습니다.
 */
@FeignClient(
    name = "product-service",
    url = "\${feign.clients.product-service.url}",
)
interface ProductFeignClient : ProductClient {
    /**
     * 상품 단건 조회
     *
     * @param productId 상품 ID
     * @return 상품 정보 DTO (미존재 시 FeignException 404 발생)
     */
    @GetMapping("/internal/v1/products/{productId}")
    override fun getProduct(
        @PathVariable productId: UUID,
    ): ProductClient.ProductResponse?

    /**
     * 상품 다건 조회
     *
     * @param productIds 상품 ID 목록 (쿼리 파라미터)
     * @return 상품 정보 DTO 목록 (존재하는 상품만 반환)
     */
    @GetMapping("/internal/v1/products")
    override fun getProducts(
        @RequestParam("ids") productIds: List<UUID>,
    ): List<ProductClient.ProductResponse>
}
