package com.groom.order.domain.port

import com.groom.order.domain.model.ProductInfo
import java.util.UUID

/**
 * Product 도메인 연동 Port
 *
 * Order 도메인이 Product 정보를 조회하기 위한 인터페이스입니다.
 * MSA 전환 시 HTTP 클라이언트로 쉽게 교체 가능합니다.
 */
interface ProductPort {
    /**
     * 상품 ID로 상품을 조회합니다.
     *
     * @param productId 상품 ID
     * @return 조회된 상품 정보 (없으면 null)
     */
    fun loadById(productId: UUID): ProductInfo?

    /**
     * 여러 상품 ID로 상품 목록을 조회합니다.
     *
     * @param productIds 상품 ID 목록
     * @return 조회된 상품 목록
     */
    fun loadAllById(productIds: List<UUID>): List<ProductInfo>
}
