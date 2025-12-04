package com.groom.order.adapter.outbound.client.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.groom.order.adapter.outbound.client.StoreClient
import com.groom.order.adapter.outbound.client.StoreFeignClient
import feign.Feign
import feign.FeignException
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.contract.stubrunner.StubFinder
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.cloud.openfeign.support.SpringMvcContract
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.util.UUID

/**
 * Store Service Consumer Contract Test
 *
 * Producer(Store Service)가 배포한 Stub을 사용하여
 * Consumer(Order Service)의 StoreFeignClient가 Contract를 준수하는지 검증합니다.
 *
 * ## Contract 파일 위치 (store-service)
 * ```
 * store-api/src/test/resources/contracts/internal/order-service/
 * ├── getStoreById.yml           - 스토어 조회 성공
 * ├── getStoreByIdNotFound.yml   - 스토어 조회 실패 (404)
 * ├── checkStoreExistsTrue.yml   - 스토어 존재 확인 (true)
 * └── checkStoreExistsFalse.yml  - 스토어 존재 확인 (false)
 * ```
 *
 * ## 실행 방법
 * ```bash
 * ./gradlew contractTest
 * ```
 *
 * ## 사전 조건
 * - Store Service가 Stub JAR를 GitHub Packages에 배포해야 함
 * - GITHUB_ACTOR, GITHUB_TOKEN 환경변수 설정 필요
 *
 * @see StoreFeignClient
 */
@Tag("contract-test")
@SpringJUnitConfig
@ActiveProfiles("consumer-contract-test")
@AutoConfigureStubRunner(
    ids = ["com.groom:store-service-contract-stubs:+:stubs"],
    stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "https://maven.pkg.github.com/GroomC4/c4ang-packages-hub",
)
@DisplayName("Store Service Consumer Contract Test")
class StoreClientContractTest {

    @Autowired
    private lateinit var stubFinder: StubFinder

    private lateinit var storeFeignClient: StoreFeignClient

    companion object {
        // Contract에 정의된 테스트 데이터
        // store-api/src/test/resources/contracts/internal/order-service/ 참조
        private val EXISTING_STORE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
        private val NON_EXISTING_STORE_ID = UUID.fromString("999e8400-e29b-41d4-a716-446655440999")
    }

    @BeforeEach
    fun setup() {
        val objectMapper = ObjectMapper().registerKotlinModule()

        // StubFinder를 통해 동적으로 할당된 포트 조회
        val stubUrl = stubFinder.findStubUrl("store-service-contract-stubs")

        // Feign Client를 Stub Runner가 실행한 WireMock 서버에 연결
        storeFeignClient =
            Feign
                .builder()
                .contract(SpringMvcContract())
                .encoder(JacksonEncoder(objectMapper))
                .decoder(JacksonDecoder(objectMapper))
                .requestInterceptor { template ->
                    template.header("Content-Type", "application/json")
                }.target(StoreFeignClient::class.java, stubUrl.toString())
    }

    @Test
    @DisplayName("getStoreById - 스토어 조회 성공")
    fun `스토어 조회 - 성공`() {
        // given: Contract에 정의된 존재하는 스토어 ID

        // when
        val response = storeFeignClient.getStore(EXISTING_STORE_ID)

        // then: Contract 응답과 일치하는지 검증
        response.shouldNotBeNull()
        response.storeId shouldBe EXISTING_STORE_ID
        response.name.shouldNotBeNull()
        response.status shouldBe "REGISTERED"
    }

    @Test
    @DisplayName("getStoreByIdNotFound - 존재하지 않는 스토어 조회 시 404 응답")
    fun `스토어 조회 - 미존재시 404 응답`() {
        // given: Contract에 정의된 존재하지 않는 스토어 ID

        // when & then: 404 응답 검증
        try {
            storeFeignClient.getStore(NON_EXISTING_STORE_ID)
            throw AssertionError("Contract에 따라 404 예외가 발생해야 합니다")
        } catch (e: FeignException.NotFound) {
            // Contract에 정의된 404 응답 검증
            e.status() shouldBe 404
        }
    }

    @Test
    @DisplayName("checkStoreExistsTrue - 스토어 존재 여부 확인 (존재함)")
    fun `스토어 존재 여부 확인 - 존재함`() {
        // given: Contract에 정의된 존재하는 스토어 ID

        // when
        val response = storeFeignClient.existsStore(EXISTING_STORE_ID)

        // then
        response.shouldNotBeNull()
        response.exists shouldBe true
    }

    @Test
    @DisplayName("checkStoreExistsFalse - 스토어 존재 여부 확인 (존재하지 않음)")
    fun `스토어 존재 여부 확인 - 존재하지 않음`() {
        // given: Contract에 정의된 존재하지 않는 스토어 ID

        // when
        val response = storeFeignClient.existsStore(NON_EXISTING_STORE_ID)

        // then
        response.shouldNotBeNull()
        response.exists shouldBe false
    }
}
