package contracts.internal.store

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "get_store_by_id_success"
    description "스토어 ID로 스토어 조회 - 성공"

    request {
        method GET()
        urlPath("/internal/api/v1/stores/660e8400-e29b-41d4-a716-446655440001")
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
            id    : "660e8400-e29b-41d4-a716-446655440001",
            name  : "스타벅스 강남점",
            status: "ACTIVE"
        ])
        bodyMatchers {
            jsonPath('$.id', byRegex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))
            jsonPath('$.name', byType())
            jsonPath('$.status', byRegex('ACTIVE|INACTIVE|SUSPENDED'))
        }
    }
}
