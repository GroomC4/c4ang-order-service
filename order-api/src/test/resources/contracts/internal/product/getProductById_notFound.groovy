package contracts.internal.product

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "get_product_by_id_not_found"
    description "상품 ID로 상품 단건 조회 - 상품 미존재"

    request {
        method GET()
        urlPath("/internal/v1/products/99999999-9999-9999-9999-999999999999")
        headers {
            contentType(applicationJson())
        }
    }

    response {
        status NOT_FOUND()
        headers {
            contentType(applicationJson())
        }
        body([
            error  : "PRODUCT_NOT_FOUND",
            message: $(consumer("Product not found with id: 99999999-9999-9999-9999-999999999999"), producer(regex("Product not found.*")))
        ])
    }
}
