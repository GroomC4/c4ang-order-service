package com.groom.order.adapter.outbound.client.contract

import com.groom.order.adapter.outbound.client.StoreFeignClient
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Store Service Contract Test (Consumer Side)
 *
 * Producer(Store Service)가 배포한 Stub을 사용하여
 * Consumer(Order Service)의 Client가 Contract를 준수하는지 검증합니다.
 *
 * ## 실행 방법
 * ```
 * ./gradlew contractTest
 * ```
 *
 * ## 사전 조건
 * - Store Service가 Stub JAR를 Maven Repository에 배포해야 함
 * - stubsMode: REMOTE인 경우 repositoryRoot 설정 필요
 *
 * @see StoreFeignClient
 */
@Tag("contract-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("consumer-contract-test")
@AutoConfigureStubRunner(
    ids = ["io.github.groomc4:store-api:+:stubs:8084"],
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    // repositoryRoot = "https://repo.example.com/repository/maven-releases/" // REMOTE 모드 시 설정
)
class StoreClientContractTest {

    @Autowired
    private lateinit var storeFeignClient: StoreFeignClient

    companion object {
        // Contract에 정의된 테스트 데이터
        private val EXISTING_STORE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
        private val NON_EXISTING_STORE_ID = UUID.fromString("999e8400-e29b-41d4-a716-446655440999")
    }

    @Test
    fun `스토어 조회 - 성공`() {
        // when
        val response = storeFeignClient.getStore(EXISTING_STORE_ID)

        // then
        response.shouldNotBeNull()
        response.storeId shouldBe EXISTING_STORE_ID
        response.name.shouldNotBeNull()
        response.status shouldBe "REGISTERED"
    }

    @Test
    fun `스토어 조회 - 미존재시 null 반환`() {
        // when
        val response = storeFeignClient.getStore(NON_EXISTING_STORE_ID)

        // then
        response.shouldBeNull()
    }

    @Test
    fun `스토어 존재 여부 확인 - 존재함`() {
        // when
        val response = storeFeignClient.existsStore(EXISTING_STORE_ID)

        // then
        response.shouldNotBeNull()
        response.exists shouldBe true
    }

    @Test
    fun `스토어 존재 여부 확인 - 존재하지 않음`() {
        // when
        val response = storeFeignClient.existsStore(NON_EXISTING_STORE_ID)

        // then
        response.shouldNotBeNull()
        response.exists shouldBe false
    }
}
