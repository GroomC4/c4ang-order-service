package contracts.internal.product

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "get_products_by_ids_success"
    description "상품 ID 목록으로 상품 다건 조회 - 성공"

    request {
        method GET()
        urlPath("/internal/api/v1/products") {
            queryParameters {
                parameter 'ids': "550e8400-e29b-41d4-a716-446655440000,550e8400-e29b-41d4-a716-446655440002"
            }
        }
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
            [
                id       : "550e8400-e29b-41d4-a716-446655440000",
                storeId  : "660e8400-e29b-41d4-a716-446655440001",
                name     : "아메리카노",
                storeName: "스타벅스 강남점",
                price    : 4500.00
            ],
            [
                id       : "550e8400-e29b-41d4-a716-446655440002",
                storeId  : "660e8400-e29b-41d4-a716-446655440001",
                name     : "카페라떼",
                storeName: "스타벅스 강남점",
                price    : 5000.00
            ]
        ])
        bodyMatchers {
            jsonPath('$[0].id', byRegex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))
            jsonPath('$[0].storeId', byRegex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))
            jsonPath('$[0].name', byType())
            jsonPath('$[0].storeName', byType())
            jsonPath('$[0].price', byType())
            jsonPath('$[1].id', byRegex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))
        }
    }
}
