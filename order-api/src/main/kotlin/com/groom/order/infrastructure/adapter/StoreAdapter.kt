package com.groom.order.infrastructure.adapter

import com.groom.order.domain.model.StoreInfo
import com.groom.order.domain.port.StorePort
import com.groom.store.domain.port.StoreReader
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Store 도메인 연동 Adapter (모놀리식 구현)
 *
 * StoreReader를 통해 Store 도메인의 정보를 조회하고,
 * Order 도메인의 StoreInfo로 변환합니다.
 *
 * MSA 전환 시: StoreReader → HTTP Client로 교체
 */
@Component
class StoreAdapter(
    private val storeReader: StoreReader,
) : StorePort {
    override fun findById(storeId: UUID): StoreInfo? =
        storeReader
            .findById(storeId)
            .map { store ->
                StoreInfo(
                    id = store.id,
                    name = store.name,
                    status = store.status.name,
                )
            }.orElse(null)

    override fun existsById(storeId: UUID): Boolean = storeReader.findById(storeId).isPresent
}
