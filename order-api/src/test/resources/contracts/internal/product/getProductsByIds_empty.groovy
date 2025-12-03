package contracts.internal.product

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "get_products_by_ids_empty"
    description "상품 ID 목록으로 상품 다건 조회 - 빈 목록 (모든 상품 미존재)"

    request {
        method GET()
        urlPath("/internal/v1/products") {
            queryParameters {
                parameter 'ids': "99999999-9999-9999-9999-999999999999"
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
        body([])
    }
}
