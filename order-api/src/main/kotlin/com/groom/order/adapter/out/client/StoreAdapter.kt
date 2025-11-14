package com.groom.order.adapter.out.client

import com.groom.order.domain.model.StoreInfo
import com.groom.order.domain.port.StorePort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Store 도메인 연동 Adapter (MSA 구현)
 *
 * StoreClient(Feign)를 통해 Store Service의 REST API를 호출하고,
 * Order 도메인의 StoreInfo로 변환합니다.
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
    override fun loadById(storeId: UUID): StoreInfo? {
        // TODO: Store Service API 호출 후 StoreInfo로 변환
        // val response = storeClient.getStore(storeId) ?: return null
        // return StoreInfo(
        //     id = response.id,
        //     name = response.name,
        //     status = response.status,
        // )
        TODO("Store Service HTTP 호출 구현 필요")
    }

    /**
     * 스토어 존재 여부 확인
     *
     * @param storeId 스토어 ID
     * @return 존재 여부
     */
    override fun existsById(storeId: UUID): Boolean {
        // TODO: Store Service API 호출
        // return storeClient.existsStore(storeId).exists
        TODO("Store Service HTTP 호출 구현 필요")
    }
}
