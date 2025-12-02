package com.groom.order.adapter.outbound.client

import com.groom.order.domain.model.StoreInfo
import com.groom.order.domain.port.StorePort
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 테스트용 Store Adapter
 *
 * 통합 테스트에서 실제 Store Service 호출 대신 stub 데이터를 반환합니다.
 * @Profile("test")로 테스트 환경에서만 활성화되며, @Primary로 production StoreAdapter를 대체합니다.
 */
@Component
@Profile("test")
@Primary
class TestStoreAdapter : StorePort {
    companion object {
        // Test Store IDs
        private val STORE_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val STORE_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000002")

        // Stub Store Data
        private val STUB_STORES =
            mapOf(
                STORE_1 to
                    StoreInfo(
                        id = STORE_1,
                        name = "Test Store 1",
                        status = "ACTIVE",
                    ),
                STORE_2 to
                    StoreInfo(
                        id = STORE_2,
                        name = "Test Store 2",
                        status = "ACTIVE",
                    ),
            )
    }

    override fun loadById(storeId: UUID): StoreInfo? {
        return STUB_STORES[storeId]
    }

    override fun existsById(storeId: UUID): Boolean {
        return STUB_STORES.containsKey(storeId)
    }
}
