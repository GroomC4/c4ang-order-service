package com.groom.order.adapter.outbound.client

import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * 테스트용 Product Client
 *
 * 통합 테스트에서 실제 Product Service HTTP 호출 대신 stub 데이터를 반환합니다.
 * @Profile("test")로 테스트 환경에서만 활성화되며, @Primary로 ProductFeignClient를 대체합니다.
 *
 * ## 테스트 데이터 규칙
 *
 * ### 존재하는 상품 (200 OK 응답)
 * | Product ID | Store ID | 상품명 | 가격 |
 * |------------|----------|--------|------|
 * | aaaaaaaa-aaaa-aaaa-aaaa-000000000001 | bbbbbbbb-bbbb-bbbb-bbbb-000000000001 | Gaming Mouse | 29,000 |
 * | aaaaaaaa-aaaa-aaaa-aaaa-000000000002 | bbbbbbbb-bbbb-bbbb-bbbb-000000000001 | Mechanical Keyboard | 89,000 |
 * | aaaaaaaa-aaaa-aaaa-aaaa-000000000003 | bbbbbbbb-bbbb-bbbb-bbbb-000000000002 | Limited Edition Mouse | 49,000 |
 *
 * ### 존재하지 않는 상품 (null 반환)
 * - 위 목록에 없는 모든 UUID
 * - 예: 99999999-9999-9999-9999-999999999999
 *
 * @see TestStoreClient 스토어 테스트 데이터
 * @see docs/test/TEST_FIXTURES.md 전체 테스트 데이터 문서
 */
@Component
@Profile("test")
@Primary
class TestProductClient : ProductClient {
    companion object {
        // ========================================
        // Test Product IDs
        // ========================================
        val PRODUCT_MOUSE: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        val PRODUCT_KEYBOARD: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")
        val PRODUCT_LOW_STOCK: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003")

        // ========================================
        // Test Store IDs (for product-store mapping)
        // ========================================
        val STORE_1: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        val STORE_2: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000002")

        // ========================================
        // Non-existent IDs (for not found scenarios)
        // ========================================
        val NON_EXISTENT_PRODUCT: UUID = UUID.fromString("99999999-9999-9999-9999-999999999999")

        // ========================================
        // Stub Product Data
        // ========================================
        private val STUB_PRODUCTS = mapOf(
            PRODUCT_MOUSE to ProductClient.ProductResponse(
                id = PRODUCT_MOUSE,
                storeId = STORE_1,
                storeName = "Test Store 1",
                name = "Gaming Mouse",
                price = BigDecimal("29000"),
            ),
            PRODUCT_KEYBOARD to ProductClient.ProductResponse(
                id = PRODUCT_KEYBOARD,
                storeId = STORE_1,
                storeName = "Test Store 1",
                name = "Mechanical Keyboard",
                price = BigDecimal("89000"),
            ),
            PRODUCT_LOW_STOCK to ProductClient.ProductResponse(
                id = PRODUCT_LOW_STOCK,
                storeId = STORE_2,
                storeName = "Test Store 2",
                name = "Limited Edition Mouse",
                price = BigDecimal("49000"),
            ),
        )

        /**
         * 테스트에서 사용할 수 있는 모든 상품 ID 목록
         */
        val ALL_PRODUCT_IDS: List<UUID> = STUB_PRODUCTS.keys.toList()
    }

    override fun getProduct(productId: UUID): ProductClient.ProductResponse? {
        return STUB_PRODUCTS[productId]
    }

    override fun getProducts(productIds: List<UUID>): List<ProductClient.ProductResponse> {
        return productIds.mapNotNull { STUB_PRODUCTS[it] }
    }
}
