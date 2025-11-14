package com.groom.order.domain.model

import java.math.BigDecimal
import java.util.UUID

/**
 * 상품 정보 (Value Object)
 *
 * Order 도메인에서 사용하는 Product 도메인의 정보입니다.
 * Product 도메인의 엔티티와 분리되어 Order 도메인의 독립성을 보장합니다.
 */
data class ProductInfo(
    val id: UUID,
    val storeId: UUID,
    val storeName: String,
    val name: String,
    val price: BigDecimal,
)
