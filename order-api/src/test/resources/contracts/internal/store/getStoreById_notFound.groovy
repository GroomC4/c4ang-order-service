package contracts.internal.store

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "get_store_by_id_not_found"
    description "스토어 ID로 스토어 조회 - 스토어 미존재"

    request {
        method GET()
        urlPath("/internal/api/v1/stores/999e8400-e29b-41d4-a716-446655440999")
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
            error  : "STORE_NOT_FOUND",
            message: "Store not found with id: 999e8400-e29b-41d4-a716-446655440999"
        ])
        bodyMatchers {
            jsonPath('$.error', byEquality())
            jsonPath('$.message', byType())
        }
    }
}
