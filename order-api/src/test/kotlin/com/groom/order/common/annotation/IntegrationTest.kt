package com.groom.order.common.annotation

import com.groom.platform.testcontainers.annotation.IntegrationTest as PlatformIntegrationTest

/**
 * Order Service 통합 테스트용 어노테이션
 *
 * platform-core의 testcontainers-starter를 사용합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @IntegrationTest
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * class OrderControllerIntegrationTest {
 *     @Test
 *     fun `통합 테스트`() {
 *         // 테스트 로직
 *     }
 * }
 * ```
 *
 * 또는 IntegrationTestBase를 상속받아 사용:
 * ```kotlin
 * class OrderServiceIntegrationTest : IntegrationTestBase() {
 *     @Test
 *     fun `테스트`() { ... }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PlatformIntegrationTest
annotation class IntegrationTest
