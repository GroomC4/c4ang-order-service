package com.groom.order.adapter.outbound.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

/**
 * Product Service Feign Client
 *
 * Product 도메인의 Internal REST API를 호출하기 위한 Feign Client 구현체입니다.
 * MSA 환경에서 Product Service와 HTTP로 통신합니다.
 *
 * Internal API는 서비스 간 통신 전용이므로 별도 인증이 필요하지 않습니다.
 *
 * 통합 테스트(test 프로필)에서는 TestProductClient가 @Primary로 주입되므로,
 * 이 FeignClient는 비활성화됩니다.
 * Consumer Contract Test(consumer-contract-test 프로필)에서는 활성화됩니다.
 *
 * ## 주의: getProducts 파라미터 형식 불일치
 *
 * 현재 [getProducts] 메서드는 `List<UUID>`를 받아 `ids=a&ids=b` 형식으로 전송합니다.
 * 하지만 Product Service의 Internal API는 쉼표로 구분된 단일 문자열(`ids=a,b`)을 기대합니다.
 *
 * **실제 서비스 연동 시 다음 중 하나의 조치가 필요합니다:**
 * 1. 이 인터페이스의 파라미터 타입을 `String`으로 변경하고, 호출부에서 쉼표로 조인
 * 2. Product Service의 컨트롤러가 `List<UUID>`도 받을 수 있도록 수정
 * 3. Feign의 QueryEncoder 커스터마이징으로 쉼표 구분 형식 사용
 *
 * Contract Test에서는 이 불일치를 해결하기 위해 별도의 테스트 전용 인터페이스를 사용합니다.
 * @see com.groom.order.adapter.outbound.client.contract.ProductClientContractTest.ContractTestProductFeignClient
 */
@Profile("!test | consumer-contract-test")
@FeignClient(
    name = "product-service",
    url = "\${feign.clients.product-service.url}",
)
interface ProductFeignClient : ProductClient {
    /**
     * 상품 단건 조회
     *
     * @param productId 상품 ID
     * @return 상품 정보 DTO (미존재 시 FeignException 404 발생)
     */
    @GetMapping("/internal/v1/products/{productId}")
    override fun getProduct(
        @PathVariable productId: UUID,
    ): ProductClient.ProductResponse?

    /**
     * 상품 다건 조회
     *
     * @param productIds 상품 ID 목록 (쿼리 파라미터)
     * @return 상품 정보 DTO 목록 (존재하는 상품만 반환)
     */
    @GetMapping("/internal/v1/products")
    override fun getProducts(
        @RequestParam("ids") productIds: List<UUID>,
    ): List<ProductClient.ProductResponse>
}
