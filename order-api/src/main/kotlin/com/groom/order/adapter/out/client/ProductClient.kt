package com.groom.order.adapter.out.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigDecimal
import java.util.UUID

/**
 * Product Service HTTP Client (Feign)
 *
 * Product 도메인의 REST API를 호출하기 위한 Feign Client 인터페이스입니다.
 * MSA 환경에서 Product Service와 통신합니다.
 */
@FeignClient(
    name = "product-service",
    url = "\${feign.clients.product-service.url}",
)
interface ProductClient {
    /**
     * 상품 단건 조회
     *
     * @param productId 상품 ID
     * @return 상품 정보 DTO
     */
    @GetMapping("/api/v1/products/{productId}")
    fun getProduct(
        @PathVariable productId: UUID,
    ): ProductResponse?

    /**
     * 상품 다건 조회
     *
     * @param productIds 상품 ID 목록 (쿼리 파라미터)
     * @return 상품 정보 DTO 목록
     */
    @GetMapping("/api/v1/products")
    fun getProducts(
        @RequestParam("ids") productIds: List<UUID>,
    ): List<ProductResponse>

    /**
     * Product Service 응답 DTO
     */
    data class ProductResponse(
        val id: UUID,
        val storeId: UUID,
        val name: String,
        val storeName: String,
        val price: BigDecimal,
    )
}
