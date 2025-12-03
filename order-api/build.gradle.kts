plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

// Platform Core 버전 관리
val platformCoreVersion = "2.4.2"

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

    // Redisson (Redis 클라이언트 with 원자적 연산 지원)
    implementation("org.redisson:redisson-spring-boot-starter:3.24.3")

    // ShedLock (분산 스케줄러 락)
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.10.0")

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

    // Spring Cloud Contract Stub Runner (Consumer Contract Test)
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")

    // Platform Core - Testcontainers (테스트 전용)
    testImplementation("io.github.groomc4:testcontainers-starter:$platformCoreVersion")
}

// 모든 Test 태스크에 공통 설정 적용
tasks.withType<Test> {
    // 메모리 설정 (통합테스트 Testcontainers 실행을 위해)
    minHeapSize = "512m"
    maxHeapSize = "2048m"

    systemProperty("user.timezone", "KST")
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")

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
    useJUnitPlatform {
        // Contract 테스트는 별도 task(contractTest)로 실행
        excludeTags("contract-test")
    }
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

// Contract Test 전용 태스크 (Stub Runner 기반)
val contractTest by tasks.registering(Test::class) {
    description = "Runs contract tests with Stub Runner"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("contract-test")
    }

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    shouldRunAfter(tasks.test)
}

// Docker Compose 연동 태스크 추가
val dockerComposeUp by tasks.registering(Exec::class) {
    group = "docker"
    description = "Run docker compose up for local infrastructure."
    commandLine(
        "sh",
        "-c",
        "command -v docker >/dev/null 2>&1 && docker compose up -d || echo 'docker not found, skipping docker compose up'",
    )
    workingDir = project.projectDir
}
val dockerComposeDown by tasks.registering(Exec::class) {
    group = "docker"
    description = "Run docker compose down for local infrastructure."
    commandLine(
        "sh",
        "-c",
        "command -v docker >/dev/null 2>&1 && docker compose down || echo 'docker not found, skipping docker compose down'",
    )
    workingDir = project.projectDir
}
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(dockerComposeUp)
    finalizedBy(dockerComposeDown)
}
