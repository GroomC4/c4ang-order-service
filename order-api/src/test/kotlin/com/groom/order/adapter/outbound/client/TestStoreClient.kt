package com.groom.order.adapter.outbound.client

import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 테스트용 Store Client
 *
 * 통합 테스트에서 실제 Store Service HTTP 호출 대신 stub 데이터를 반환합니다.
 * @Profile("test")로 테스트 환경에서만 활성화되며, @Primary로 StoreFeignClient를 대체합니다.
 *
 * ## 테스트 데이터 규칙
 *
 * ### 존재하는 스토어 (200 OK 응답)
 * | Store ID | 스토어명 | 상태 |
 * |----------|----------|------|
 * | bbbbbbbb-bbbb-bbbb-bbbb-000000000001 | Test Store 1 | ACTIVE |
 * | bbbbbbbb-bbbb-bbbb-bbbb-000000000002 | Test Store 2 | ACTIVE |
 * | bbbbbbbb-bbbb-bbbb-bbbb-000000000003 | Inactive Store | INACTIVE |
 *
 * ### 존재하지 않는 스토어 (null 반환 / exists: false)
 * - 위 목록에 없는 모든 UUID
 * - 예: 99999999-9999-9999-9999-999999999999
 *
 * @see TestProductClient 상품 테스트 데이터
 * @see docs/test/TEST_FIXTURES.md 전체 테스트 데이터 문서
 */
@Component
@Profile("test")
@Primary
class TestStoreClient : StoreClient {
    companion object {
        // ========================================
        // Test Store IDs
        // ========================================
        val STORE_1: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        val STORE_2: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000002")
        val STORE_INACTIVE: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000003")

        // ========================================
        // Non-existent IDs (for not found scenarios)
        // ========================================
        val NON_EXISTENT_STORE: UUID = UUID.fromString("99999999-9999-9999-9999-999999999999")

        // ========================================
        // Stub Store Data
        // ========================================
        private val STUB_STORES = mapOf(
            STORE_1 to StoreClient.StoreResponse(
                storeId = STORE_1,
                name = "Test Store 1",
                status = "ACTIVE",
            ),
            STORE_2 to StoreClient.StoreResponse(
                storeId = STORE_2,
                name = "Test Store 2",
                status = "ACTIVE",
            ),
            STORE_INACTIVE to StoreClient.StoreResponse(
                storeId = STORE_INACTIVE,
                name = "Inactive Store",
                status = "INACTIVE",
            ),
        )

        /**
         * 테스트에서 사용할 수 있는 모든 스토어 ID 목록
         */
        val ALL_STORE_IDS: List<UUID> = STUB_STORES.keys.toList()

        /**
         * 활성 상태의 스토어 ID 목록
         */
        val ACTIVE_STORE_IDS: List<UUID> = STUB_STORES
            .filter { it.value.status == "ACTIVE" }
            .keys
            .toList()
    }

    override fun getStore(storeId: UUID): StoreClient.StoreResponse? {
        return STUB_STORES[storeId]
    }

    override fun existsStore(storeId: UUID): StoreClient.ExistsResponse {
        return StoreClient.ExistsResponse(exists = STUB_STORES.containsKey(storeId))
    }
}
