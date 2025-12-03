package contracts.internal.store

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "check_store_exists_true"
    description "스토어 존재 여부 확인 - 존재함"

    request {
        method GET()
        urlPath("/internal/v1/stores/660e8400-e29b-41d4-a716-446655440001/exists")
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
            exists: true
        ])
        bodyMatchers {
            jsonPath('$.exists', byEquality())
        }
    }
}
