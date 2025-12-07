package com.groom.order.common

import com.groom.platform.testcontainers.annotation.IntegrationTest
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import org.springframework.web.context.WebApplicationContext

/**
 * Spring Cloud Contract Test를 위한 Base 클래스
 *
 * Contract 파일(YAML)을 기반으로 자동 생성된 테스트가 이 클래스를 상속받습니다.
 * - Provider 측(order-service)에서 Contract를 검증
 * - Testcontainers를 사용하여 실제 DB, Redis, Kafka 환경에서 테스트
 * - 각 테스트 전에 테스트 데이터를 로드하고, 후에 정리
 */
@IntegrationTest
@SpringBootTest(
    properties = [
        "spring.profiles.active=test",
        "testcontainers.postgres.enabled=true",
        "testcontainers.postgres.replica-enabled=true",
        "testcontainers.postgres.schema-location=project:sql/schema.sql",
        "testcontainers.redis.enabled=true",
        "testcontainers.kafka.enabled=true",
        "testcontainers.kafka.auto-create-topics=true",
        "testcontainers.kafka.topics[0].name=order.created",
        "testcontainers.kafka.topics[0].partitions=3",
        "testcontainers.kafka.topics[0].replication-factor=1",
        "testcontainers.kafka.topics[1].name=order.confirmed",
        "testcontainers.kafka.topics[1].partitions=3",
        "testcontainers.kafka.topics[1].replication-factor=1",
        "testcontainers.kafka.topics[2].name=order.cancelled",
        "testcontainers.kafka.topics[2].partitions=3",
        "testcontainers.kafka.topics[2].replication-factor=1",
        "testcontainers.kafka.topics[3].name=stock.reserved",
        "testcontainers.kafka.topics[3].partitions=3",
        "testcontainers.kafka.topics[3].replication-factor=1",
        "testcontainers.kafka.topics[4].name=stock.reservation.failed",
        "testcontainers.kafka.topics[4].partitions=1",
        "testcontainers.kafka.topics[4].replication-factor=1",
        "testcontainers.kafka.topics[5].name=payment.completed",
        "testcontainers.kafka.topics[5].partitions=3",
        "testcontainers.kafka.topics[5].replication-factor=1",
        "testcontainers.kafka.topics[6].name=payment.failed",
        "testcontainers.kafka.topics[6].partitions=1",
        "testcontainers.kafka.topics[6].replication-factor=1",

        // Kafka Topics - Additional Producer Topics
        "testcontainers.kafka.topics[7].name=order.expiration.notification",
        "testcontainers.kafka.topics[7].partitions=1",
        "testcontainers.kafka.topics[7].replication-factor=1",

        "testcontainers.kafka.topics[8].name=analytics.daily.statistics",
        "testcontainers.kafka.topics[8].partitions=1",
        "testcontainers.kafka.topics[8].replication-factor=1",

        "testcontainers.kafka.topics[9].name=order.stock.confirmed",
        "testcontainers.kafka.topics[9].partitions=3",
        "testcontainers.kafka.topics[9].replication-factor=1",

        // Kafka Topics - SAGA Events
        "testcontainers.kafka.topics[10].name=saga.stock-confirmation.failed",
        "testcontainers.kafka.topics[10].partitions=1",
        "testcontainers.kafka.topics[10].replication-factor=1",

        "testcontainers.kafka.topics[11].name=saga.order-confirmation.compensate",
        "testcontainers.kafka.topics[11].partitions=1",
        "testcontainers.kafka.topics[11].replication-factor=1",

        "testcontainers.kafka.topics[12].name=saga.stock-reservation.failed",
        "testcontainers.kafka.topics[12].partitions=1",
        "testcontainers.kafka.topics[12].replication-factor=1",

        "testcontainers.kafka.topics[13].name=saga.payment-initialization.failed",
        "testcontainers.kafka.topics[13].partitions=1",
        "testcontainers.kafka.topics[13].replication-factor=1",

        "testcontainers.kafka.topics[14].name=saga.payment-completion.compensate",
        "testcontainers.kafka.topics[14].partitions=1",
        "testcontainers.kafka.topics[14].replication-factor=1",
    ],
)
@AutoConfigureMockMvc
@SqlGroup(
    Sql(scripts = ["/sql/contract-test-data.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
    Sql(scripts = ["/sql/cleanup.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD),
)
abstract class ContractTestBase {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @BeforeEach
    fun setup() {
        // RestAssured MockMvc 설정 (전체 애플리케이션 컨텍스트 사용)
        RestAssuredMockMvc.webAppContextSetup(webApplicationContext)
    }
}
