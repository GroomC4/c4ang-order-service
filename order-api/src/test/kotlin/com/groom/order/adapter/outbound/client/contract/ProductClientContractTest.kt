package com.groom.order.adapter.outbound.client.contract

import com.groom.order.adapter.outbound.client.ProductClient
import com.groom.order.adapter.outbound.client.ProductFeignClient
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
 * Product Service Contract Test (Consumer Side)
 *
 * Producer(Product Service)가 배포한 Stub을 사용하여
 * Consumer(Order Service)의 Client가 Contract를 준수하는지 검증합니다.
 *
 * ## 실행 방법
 * ```
 * ./gradlew contractTest
 * ```
 *
 * ## 사전 조건
 * - Product Service가 Stub JAR를 Maven Repository에 배포해야 함
 * - stubsMode: REMOTE인 경우 repositoryRoot 설정 필요
 *
 * @see ProductFeignClient
 */
@Tag("contract-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("contract-test")
@AutoConfigureStubRunner(
    ids = ["io.github.groomc4:product-api:+:stubs:8083"],
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    // repositoryRoot = "https://repo.example.com/repository/maven-releases/" // REMOTE 모드 시 설정
)
class ProductClientContractTest {

    @Autowired
    private lateinit var productFeignClient: ProductFeignClient

    companion object {
        // Contract에 정의된 테스트 데이터
        private val EXISTING_PRODUCT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        private val NON_EXISTING_PRODUCT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999")
        private val EXISTING_STORE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
    }

    @Test
    fun `상품 단건 조회 - 성공`() {
        // when
        val response = productFeignClient.getProduct(EXISTING_PRODUCT_ID)

        // then
        response.shouldNotBeNull()
        response.id shouldBe EXISTING_PRODUCT_ID
        response.storeId shouldBe EXISTING_STORE_ID
        response.name.shouldNotBeNull()
        response.storeName.shouldNotBeNull()
        response.price.shouldNotBeNull()
    }

    @Test
    fun `상품 단건 조회 - 미존재시 null 반환`() {
        // when
        val response = productFeignClient.getProduct(NON_EXISTING_PRODUCT_ID)

        // then
        response.shouldBeNull()
    }

    @Test
    fun `상품 다건 조회 - 성공`() {
        // given
        val productId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val productId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

        // when
        val response = productFeignClient.getProducts(listOf(productId1, productId2))

        // then
        response.shouldNotBeNull()
        response.size shouldBe 2
        response.forEach { product ->
            product.id.shouldNotBeNull()
            product.storeId.shouldNotBeNull()
            product.name.shouldNotBeNull()
            product.price.shouldNotBeNull()
        }
    }

    @Test
    fun `상품 다건 조회 - 미존재시 빈 리스트 반환`() {
        // when
        val response = productFeignClient.getProducts(listOf(NON_EXISTING_PRODUCT_ID))

        // then
        response.shouldNotBeNull()
        response.size shouldBe 0
    }
}
