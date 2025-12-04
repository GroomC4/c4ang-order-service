package com.groom.order.adapter.outbound.client

import com.groom.order.domain.model.ProductInfo
import com.groom.order.domain.port.ProductPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Product 도메인 연동 Adapter (MSA 구현)
 *
 * ProductClient를 통해 Product Service와 통신하고,
 * Order 도메인의 ProductInfo로 변환합니다.
 *
 * ProductClient는 인터페이스로 추상화되어 있어,
 * 프로덕션에서는 ProductFeignClient(HTTP), 테스트에서는 TestProductClient(stub)가 주입됩니다.
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
        return productClient.getProduct(productId)?.toProductInfo()
    }

    /**
     * 상품 다건 조회
     *
     * @param productIds 상품 ID 목록
     * @return 상품 정보 목록
     */
    override fun loadAllById(productIds: List<UUID>): List<ProductInfo> {
        val request = ProductClient.ProductSearchRequest(ids = productIds)
        return productClient.searchProducts(request).map { it.toProductInfo() }
    }

    private fun ProductClient.ProductResponse.toProductInfo(): ProductInfo {
        return ProductInfo(
            id = id,
            storeId = storeId,
            storeName = storeName,
            name = name,
            price = price,
        )
    }
}
