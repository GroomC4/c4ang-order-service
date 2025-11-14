package com.groom.order.infrastructure.adapter

import com.groom.order.domain.model.ProductInfo
import com.groom.order.domain.port.ProductPort
import com.groom.product.infrastructure.repository.ProductRepositoryImpl
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Product 도메인 연동 Adapter (모놀리식 구현)
 *
 * ProductRepositoryImpl을 통해 Product 도메인의 정보를 조회하고,
 * Order 도메인의 ProductInfo로 변환합니다.
 *
 * MSA 전환 시: ProductRepositoryImpl → HTTP Client로 교체
 */
@Component
class ProductAdapter(
    private val productRepository: ProductRepositoryImpl,
) : ProductPort {
    override fun findById(productId: UUID): ProductInfo? =
        productRepository
            .findById(productId)
            .map { product ->
                ProductInfo(
                    id = product.id,
                    storeId = product.storeId,
                    name = product.name,
                    storeName = product.storeName,
                    price = product.price,
                )
            }.orElse(null)

    override fun findAllById(productIds: List<UUID>): List<ProductInfo> =
        productRepository
            .findAllById(productIds)
            .map { product ->
                ProductInfo(
                    id = product.id,
                    storeId = product.storeId,
                    name = product.name,
                    storeName = product.storeName,
                    price = product.price,
                )
            }
}
