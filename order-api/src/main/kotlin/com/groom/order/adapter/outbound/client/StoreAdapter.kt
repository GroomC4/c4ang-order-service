package com.groom.order.adapter.outbound.client

import com.groom.order.domain.model.StoreInfo
import com.groom.order.domain.port.StorePort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Store 도메인 연동 Adapter (MSA 구현)
 *
 * StoreClient를 통해 Store Service와 통신하고,
 * Order 도메인의 StoreInfo로 변환합니다.
 *
 * StoreClient는 인터페이스로 추상화되어 있어,
 * 프로덕션에서는 StoreFeignClient(HTTP), 테스트에서는 TestStoreClient(stub)가 주입됩니다.
 */
@Component
class StoreAdapter(
    private val storeClient: StoreClient,
) : StorePort {
    /**
     * 스토어 단건 조회
     *
     * @param storeId 스토어 ID
     * @return 스토어 정보 (없으면 null)
     */
    override fun loadById(storeId: UUID): StoreInfo? = storeClient.getStore(storeId)?.toStoreInfo()

    /**
     * 스토어 존재 여부 확인
     *
     * @param storeId 스토어 ID
     * @return 존재 여부
     */
    override fun existsById(storeId: UUID): Boolean = storeClient.existsStore(storeId).exists

    private fun StoreClient.StoreResponse.toStoreInfo(): StoreInfo =
        StoreInfo(
            id = storeId,
            name = name,
            status = status,
        )
}
