package com.groom.order.adapter.outbound.client

import com.groom.order.domain.model.ProductInfo
import com.groom.order.domain.port.ProductPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Product 도메인 연동 Adapter (MSA 구현)
 *
 * ProductClient(Feign)를 통해 Product Service의 REST API를 호출하고,
 * Order 도메인의 ProductInfo로 변환합니다.
 */
@Component
class ProductAdapter(
    private val productClient: ProductClient,
) : ProductPort {
    /**
     * 상품 단건 조회
     *
     * @param productId 상품 ID
     * @return 상품 정보 (없으면 null)
     */
    override fun loadById(productId: UUID): ProductInfo? {
        // TODO: Product Service API 호출 후 ProductInfo로 변환
        // val response = productClient.getProduct(productId) ?: return null
        // return ProductInfo(
        //     id = response.id,
        //     storeId = response.storeId,
        //     name = response.name,
        //     storeName = response.storeName,
        //     price = response.price,
        // )
        TODO("Product Service HTTP 호출 구현 필요")
    }

    /**
     * 상품 다건 조회
     *
     * @param productIds 상품 ID 목록
     * @return 상품 정보 목록
     */
    override fun loadAllById(productIds: List<UUID>): List<ProductInfo> {
        // TODO: Product Service API 호출 후 ProductInfo 리스트로 변환
        // return productClient.getProducts(productIds).map { response ->
        //     ProductInfo(
        //         id = response.id,
        //         storeId = response.storeId,
        //         name = response.name,
        //         storeName = response.storeName,
        //         price = response.price,
        //     )
        // }
        TODO("Product Service HTTP 호출 구현 필요")
    }
}
