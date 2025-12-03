package contracts.internal.product

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "get_product_by_id_success"
    description "상품 ID로 상품 단건 조회 - 성공"

    request {
        method GET()
        urlPath("/internal/v1/products/550e8400-e29b-41d4-a716-446655440000")
        headers {
            contentType(applicationJson())
        }
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            id       : "550e8400-e29b-41d4-a716-446655440000",
            storeId  : "660e8400-e29b-41d4-a716-446655440001",
            name     : "아메리카노",
            storeName: "스타벅스 강남점",
            price    : 4500.00
        ])
        bodyMatchers {
            jsonPath('$.id', byRegex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))
            jsonPath('$.storeId', byRegex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))
            jsonPath('$.name', byType())
            jsonPath('$.storeName', byType())
            jsonPath('$.price', byType())
        }
    }
}
