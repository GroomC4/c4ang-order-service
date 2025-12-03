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
 *
 * 내부적으로 TestStoreClient의 stub 데이터를 사용하여 일관된 테스트 데이터를 제공합니다.
 *
 * @see TestStoreClient 테스트 데이터 정의
 * @see docs/test/TEST_FIXTURES.md 전체 테스트 데이터 문서
 */
@Component
@Profile("test")
@Primary
class TestStoreAdapter(
    private val testStoreClient: TestStoreClient,
) : StorePort {

    override fun loadById(storeId: UUID): StoreInfo? {
        return testStoreClient.getStore(storeId)?.toStoreInfo()
    }

    override fun existsById(storeId: UUID): Boolean {
        return testStoreClient.existsStore(storeId).exists
    }

    private fun StoreClient.StoreResponse.toStoreInfo(): StoreInfo {
        return StoreInfo(
            id = id,
            name = name,
            status = status,
        )
    }
}
