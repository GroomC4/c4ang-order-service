import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.spring") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.jpa") version "2.0.21" apply false
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
}

allprojects {
    group = "com.groom"
    // GitHub Actions에서 태그를 푸시하면 GITHUB_REF_NAME 환경변수로 버전을 가져옴
    // 예: v1.0.0 -> 1.0.0
    version = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()

        // Confluent Maven Repository (Kafka Avro Serializer, Schema Registry)
        maven {
            name = "Confluent"
            url = uri("https://packages.confluent.io/maven/")
        }

        // GitHub Packages Hub (platform-core, contract stubs 등 모든 내부 패키지)
        maven {
            name = "GitHubPackagesHub"
            url = uri("https://maven.pkg.github.com/GroomC4/c4ang-packages-hub")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String?
            }
        }
    }

    extensions.findByType<KotlinJvmProjectExtension>()?.apply {
        jvmToolchain(21)
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.7.1")
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(true)
    }
}

tasks.register("bootRun") {
    dependsOn(":order-api:bootRun")
}
