plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.cloud.contract") version "4.1.4"
    `maven-publish`
}

// Platform Core 버전 관리
val platformCoreVersion = "2.5.0"
// Spring Cloud Contract 버전
val springCloudContractVersion = "4.1.4"

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.confluent:kafka-avro-serializer:7.5.1")
    implementation("io.confluent:kafka-schema-registry-client:7.5.1")

    // Contract Hub (Avro 스키마)
    implementation("io.github.groomc4:c4ang-contract-hub:1.1.0")

    // Spring Cloud BOM (Spring Boot 3.3.4와 호환)
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2023.0.3"))

    // Spring Cloud OpenFeign (버전은 BOM에서 관리)
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.3")

    // Platform Core - DataSource (프로덕션 환경)
    implementation("io.github.groomc4:platform-core:$platformCoreVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.14.5")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

    // Platform Core - Testcontainers (테스트 전용)
    testImplementation("io.github.groomc4:testcontainers-starter:$platformCoreVersion")

    // Spring Cloud Contract (Provider-side testing)
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier:$springCloudContractVersion")
    testImplementation("io.rest-assured:rest-assured:5.3.2")
    testImplementation("io.rest-assured:spring-mock-mvc:5.3.2")

    // Spring Cloud Contract Stub Runner (Consumer Contract Test)
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
    // Feign Jackson for contract tests
    testImplementation("io.github.openfeign:feign-jackson:13.3")
}

// 모든 Test 태스크에 공통 설정 적용
tasks.withType<Test> {
    // 메모리 설정 (통합테스트 Testcontainers 실행을 위해)
    minHeapSize = "512m"
    maxHeapSize = "2048m"

    systemProperty("user.timezone", "KST")
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")

    // Stub Runner가 GitHub Packages에서 Stub을 다운로드하기 위한 인증 설정
    systemProperty("stubrunner.username", System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") ?: "")
    systemProperty("stubrunner.password", System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") ?: "")

    // 테스트 실행 로깅
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.test {
    useJUnitPlatform()
}

// 통합 테스트 전용 태스크 (Docker Compose 기반)
val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests with Docker Compose"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("integration-test")
    }

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    shouldRunAfter(tasks.test)
}

// Spring Cloud Contract 설정 (Provider Contract Test)
contracts {
    testMode.set(org.springframework.cloud.contract.verifier.config.TestMode.MOCKMVC)
    baseClassForTests.set("com.groom.order.common.ContractTestBase")
    contractsDslDir.set(file("src/test/resources/contracts"))
    baseClassMappings.apply {
        baseClassMapping(".*internal.*", "com.groom.order.common.ContractTestBase")
    }
}

// Contract Stub 발행 설정 (Consumer가 사용할 수 있도록 GitHub Packages에 발행)
publishing {
    publications {
        create<MavenPublication>("stubs") {
            groupId = "com.groom"
            artifactId = "order-service-contract-stubs"
            version = project.version.toString()

            // Contract Stub JAR을 발행
            artifact(tasks.named("verifierStubsJar"))
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GroomC4/c4ang-packages-hub")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String?
            }
        }
    }
}
