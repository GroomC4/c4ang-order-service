package com.groom.order.domain.model

import java.util.UUID

/**
 * 스토어 정보 (Value Object)
 *
 * Order 도메인에서 사용하는 Store 도메인의 정보입니다.
 * Store 도메인의 엔티티와 분리되어 Order 도메인의 독립성을 보장합니다.
 */
data class StoreInfo(
    val id: UUID,
    val name: String,
    val status: String,
)
