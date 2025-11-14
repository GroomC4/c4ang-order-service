package com.groom.order.domain.port

import com.groom.order.domain.model.StoreInfo
import java.util.UUID

/**
 * Store 도메인 연동 Port
 *
 * Order 도메인이 Store 정보를 조회하기 위한 인터페이스입니다.
 * MSA 전환 시 HTTP 클라이언트로 쉽게 교체 가능합니다.
 */
interface StorePort {
    /**
     * 스토어 ID로 스토어를 조회합니다.
     *
     * @param storeId 스토어 ID
     * @return 조회된 스토어 정보 (없으면 null)
     */
    fun findById(storeId: UUID): StoreInfo?

    /**
     * 스토어 존재 여부를 확인합니다.
     *
     * @param storeId 스토어 ID
     * @return 존재 여부
     */
    fun existsById(storeId: UUID): Boolean
}
