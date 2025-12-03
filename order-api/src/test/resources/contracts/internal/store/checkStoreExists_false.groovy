package contracts.internal.store

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "check_store_exists_false"
    description "스토어 존재 여부 확인 - 존재하지 않음"

    request {
        method GET()
        urlPath("/internal/v1/stores/999e8400-e29b-41d4-a716-446655440999/exists")
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
            exists: false
        ])
        bodyMatchers {
            jsonPath('$.exists', byEquality())
        }
    }
}
