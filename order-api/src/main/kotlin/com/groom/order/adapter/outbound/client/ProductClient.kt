package com.groom.order.adapter.outbound.client

import java.math.BigDecimal
import java.util.UUID

/**
 * Product Service Client 인터페이스
 *
 * Product 도메인과의 통신을 추상화한 인터페이스입니다.
 * HTTP(Feign), gRPC 등 다양한 구현체로 교체 가능합니다.
 */
interface ProductClient {
    /**
     * 상품 단건 조회
     *
     * @param productId 상품 ID
     * @return 상품 정보 DTO (미존재 시 null 또는 예외 발생)
     */
    fun getProduct(productId: UUID): ProductResponse?

    /**
     * 상품 다건 조회
     *
     * @param request 상품 ID 목록을 담은 요청 객체
     * @return 상품 정보 DTO 목록 (존재하는 상품만 반환)
     */
    fun searchProducts(request: ProductSearchRequest): List<ProductResponse>

    /**
     * 상품 다건 조회 요청 DTO
     */
    data class ProductSearchRequest(
        val ids: List<UUID>,
    )

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
