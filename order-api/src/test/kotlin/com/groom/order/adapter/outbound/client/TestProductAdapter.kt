package com.groom.order.adapter.outbound.client

import com.groom.order.domain.model.ProductInfo
import com.groom.order.domain.port.ProductPort
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 테스트용 Product Adapter
 *
 * 통합 테스트에서 실제 Product Service 호출 대신 stub 데이터를 반환합니다.
 * @Profile("test")로 테스트 환경에서만 활성화되며, @Primary로 production ProductAdapter를 대체합니다.
 *
 * 내부적으로 TestProductClient의 stub 데이터를 사용하여 일관된 테스트 데이터를 제공합니다.
 *
 * @see TestProductClient 테스트 데이터 정의
 * @see docs/test/TEST_FIXTURES.md 전체 테스트 데이터 문서
 */
@Component
@Profile("test")
@Primary
class TestProductAdapter(
    private val testProductClient: TestProductClient,
) : ProductPort {

    override fun loadById(productId: UUID): ProductInfo? {
        return testProductClient.getProduct(productId)?.toProductInfo()
    }

    override fun loadAllById(productIds: List<UUID>): List<ProductInfo> {
        return testProductClient.getProducts(productIds).map { it.toProductInfo() }
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
