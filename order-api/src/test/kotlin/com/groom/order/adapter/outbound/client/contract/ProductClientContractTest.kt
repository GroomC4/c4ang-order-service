package com.groom.order.adapter.outbound.client.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.groom.order.adapter.outbound.client.ProductClient
import feign.Feign
import feign.FeignException
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.kotest.matchers.collections.shouldBeEmpty
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

/**
 * Product Service Consumer Contract Test
 *
 * Producer(Product Service)가 배포한 Stub을 사용하여
 * Consumer(Order Service)의 ProductFeignClient가 Contract를 준수하는지 검증합니다.
 *
 * ## Contract 파일 위치 (product-service)
 * ```
 * product-api/src/test/resources/contracts.internal/order-service/
 * ├── getProductById_success.yml     - 상품 조회 성공
 * ├── getProductById_notFound.yml    - 상품 조회 실패 (404)
 * ├── getProductsByIds_success.yml   - 상품 다건 조회 성공 (POST)
 * └── getProductsByIds_empty.yml     - 상품 다건 조회 빈 결과 (POST)
 * ```
 *
 * ## 실행 방법
 * ```bash
 * ./gradlew contractTest
 * ```
 *
 * ## 사전 조건
 * - Product Service가 Stub JAR를 GitHub Packages에 배포해야 함
 * - GITHUB_ACTOR, GITHUB_TOKEN 환경변수 설정 필요
 *
 * @see com.groom.order.adapter.outbound.client.ProductFeignClient
 */
@Tag("contract-test")
@SpringJUnitConfig
@ActiveProfiles("consumer-contract-test")
@AutoConfigureStubRunner(
    ids = ["com.groom:product-service-contract-stubs:+:stubs"],
    stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "https://maven.pkg.github.com/GroomC4/c4ang-packages-hub",
)
@DisplayName("Product Service Consumer Contract Test")
class ProductClientContractTest {

    @Autowired
    private lateinit var stubFinder: StubFinder

    private lateinit var productFeignClient: ContractTestProductFeignClient

    companion object {
        // Contract에 정의된 테스트 데이터
        // product-api/src/test/resources/contracts.internal/order-service/ 참조
        private val EXISTING_PRODUCT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        private val EXISTING_PRODUCT_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
        private val NON_EXISTING_PRODUCT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999")
        private val EXISTING_STORE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
    }

    /**
     * Contract Test 전용 Feign Client 인터페이스
     *
     * ProductFeignClient와 동일한 엔드포인트를 호출합니다.
     */
    interface ContractTestProductFeignClient {
        @GetMapping("/internal/v1/products/{productId}")
        fun getProduct(
            @PathVariable productId: UUID,
        ): ProductClient.ProductResponse?

        /**
         * 상품 다건 조회
         *
         * POST /internal/v1/products/search
         * Request Body: { "ids": ["uuid1", "uuid2", ...] }
         */
        @PostMapping("/internal/v1/products/search")
        fun searchProducts(
            @RequestBody request: ProductClient.ProductSearchRequest,
        ): List<ProductClient.ProductResponse>
    }

    @BeforeEach
    fun setup() {
        val objectMapper = ObjectMapper().registerKotlinModule()

        // StubFinder를 통해 동적으로 할당된 포트 조회
        val stubUrl = stubFinder.findStubUrl("product-service-contract-stubs")

        // Feign Client를 Stub Runner가 실행한 WireMock 서버에 연결
        productFeignClient =
            Feign
                .builder()
                .contract(SpringMvcContract())
                .encoder(JacksonEncoder(objectMapper))
                .decoder(JacksonDecoder(objectMapper))
                .requestInterceptor { template ->
                    template.header("Content-Type", "application/json")
                }.target(ContractTestProductFeignClient::class.java, stubUrl.toString())
    }

    @Test
    @DisplayName("getProductById_success - 상품 조회 성공")
    fun `상품 단건 조회 - 성공`() {
        // given: Contract에 정의된 존재하는 상품 ID

        // when
        val response = productFeignClient.getProduct(EXISTING_PRODUCT_ID)

        // then: Contract 응답과 일치하는지 검증
        response.shouldNotBeNull()
        response.id shouldBe EXISTING_PRODUCT_ID
        response.storeId shouldBe EXISTING_STORE_ID
        response.name.shouldNotBeNull()
        response.storeName.shouldNotBeNull()
        response.price.shouldNotBeNull()
    }

    @Test
    @DisplayName("getProductById_notFound - 존재하지 않는 상품 조회 시 404 응답")
    fun `상품 단건 조회 - 미존재시 404 응답`() {
        // given: Contract에 정의된 존재하지 않는 상품 ID

        // when & then: 404 응답 검증
        try {
            productFeignClient.getProduct(NON_EXISTING_PRODUCT_ID)
            throw AssertionError("Contract에 따라 404 예외가 발생해야 합니다")
        } catch (e: FeignException.NotFound) {
            // Contract에 정의된 404 응답 검증
            e.status() shouldBe 404
        }
    }

    @Test
    @DisplayName("getProductsByIds_success - 상품 다건 조회 성공")
    fun `상품 다건 조회 - 성공`() {
        // given: Contract에 정의된 존재하는 상품 ID 목록
        val request = ProductClient.ProductSearchRequest(
            ids = listOf(EXISTING_PRODUCT_ID, EXISTING_PRODUCT_ID_2)
        )

        // when
        val response = productFeignClient.searchProducts(request)

        // then: Contract 응답과 일치하는지 검증
        response.shouldNotBeNull()
        response.size shouldBe 2
        response.forEach { product ->
            product.id.shouldNotBeNull()
            product.storeId.shouldNotBeNull()
            product.name.shouldNotBeNull()
            product.storeName.shouldNotBeNull()
            product.price.shouldNotBeNull()
        }
    }

    @Test
    @DisplayName("getProductsByIds_empty - 존재하지 않는 상품 ID 목록 조회 시 빈 배열 반환")
    fun `상품 다건 조회 - 미존재시 빈 리스트 반환`() {
        // given: Contract에 정의된 존재하지 않는 상품 ID
        val request = ProductClient.ProductSearchRequest(
            ids = listOf(NON_EXISTING_PRODUCT_ID)
        )

        // when
        val response = productFeignClient.searchProducts(request)

        // then: Contract에 따라 빈 배열 반환
        response.shouldNotBeNull()
        response.shouldBeEmpty()
    }
}
