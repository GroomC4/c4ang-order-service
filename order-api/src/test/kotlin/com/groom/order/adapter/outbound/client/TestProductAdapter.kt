package com.groom.order.adapter.outbound.client

import com.groom.order.domain.model.ProductInfo
import com.groom.order.domain.port.ProductPort
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * 테스트용 Product Adapter
 *
 * 통합 테스트에서 실제 Product Service 호출 대신 stub 데이터를 반환합니다.
 * @Profile("test")로 테스트 환경에서만 활성화되며, @Primary로 production ProductAdapter를 대체합니다.
 */
@Component
@Profile("test")
@Primary
class TestProductAdapter : ProductPort {
    companion object {
        // Test Product IDs (from OrderCommandControllerIntegrationTest)
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        private val PRODUCT_KEYBOARD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")
        private val PRODUCT_LOW_STOCK = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003")

        // Test Store IDs
        private val STORE_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val STORE_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000002")

        // Stub Product Data
        private val STUB_PRODUCTS =
            mapOf(
                PRODUCT_MOUSE to
                    ProductInfo(
                        id = PRODUCT_MOUSE,
                        storeId = STORE_1,
                        storeName = "Test Store 1",
                        name = "Gaming Mouse",
                        price = BigDecimal("29000"),
                    ),
                PRODUCT_KEYBOARD to
                    ProductInfo(
                        id = PRODUCT_KEYBOARD,
                        storeId = STORE_1,
                        storeName = "Test Store 1",
                        name = "Mechanical Keyboard",
                        price = BigDecimal("89000"),
                    ),
                PRODUCT_LOW_STOCK to
                    ProductInfo(
                        id = PRODUCT_LOW_STOCK,
                        storeId = STORE_2,
                        storeName = "Test Store 2",
                        name = "Limited Edition Mouse",
                        price = BigDecimal("49000"),
                    ),
            )
    }

    override fun loadById(productId: UUID): ProductInfo? {
        return STUB_PRODUCTS[productId]
    }

    override fun loadAllById(productIds: List<UUID>): List<ProductInfo> {
        return productIds.mapNotNull { STUB_PRODUCTS[it] }
    }
}
