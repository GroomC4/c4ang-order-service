package com.groom.order.common.extension

import com.groom.platform.testSupport.BaseContainerExtension
import java.io.File

/**
 * Customer Service용 통합 테스트 컨테이너 Extension
 *
 * c4ang-platform-core의 BaseContainerExtension을 상속받아 Customer Service에 필요한
 * Docker Compose 파일 경로를 제공합니다.
 */
class SharedContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File = resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")

    override fun getSchemaFile(): File {
        // JPA를 사용하므로 스키마 파일 불필요
        return resolveComposeFile("sql/schema.sql")
    }
}
