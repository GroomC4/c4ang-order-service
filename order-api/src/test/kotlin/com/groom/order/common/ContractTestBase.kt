package com.groom.order.common

import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import com.groom.order.infrastructure.kafka.OrderEventPublisher
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.TestPropertySource
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Spring Cloud Contract Test를 위한 Base 클래스
 *
 * Contract 파일을 기반으로 자동 생성된 테스트가 이 클래스를 상속받습니다.
 * - Provider 측(order-service)에서 Contract를 검증
 * - IntegrationTestBase를 상속받아 실제 통합 테스트 환경 사용
 * - 실제 DB, Redis 등 테스트 컨테이너 활용
 * - Embedded Kafka를 사용하여 이벤트 발행 검증
 */
@AutoConfigureMockMvc
@EmbeddedKafka(
    partitions = 1,
    topics = ["order.created", "order.confirmed", "order.cancelled"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:9092", "port=9092"],
)
@TestPropertySource(
    properties = [
        "kafka.bootstrap-servers=localhost:9092",
        "kafka.schema-registry.url=mock://test-registry",
    ],
)
abstract class ContractTestBase : IntegrationTestBase() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    protected lateinit var orderEventPublisher: OrderEventPublisher

    @BeforeEach
    fun setup() {
        // RestAssured MockMvc 설정 (전체 애플리케이션 컨텍스트 사용)
        RestAssuredMockMvc.webAppContextSetup(webApplicationContext)
    }

    /**
     * Contract Test: OrderCreated 이벤트 발행 트리거
     */
    fun triggerOrderCreatedEvent() {
        val testOrder =
            Order(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                userId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001"),
                storeId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002"),
                status = OrderStatus.ORDER_CREATED,
                totalAmount = BigDecimal("20.00"),
                items =
                    listOf(
                        OrderItem(
                            id = UUID.randomUUID(),
                            orderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                            productId = UUID.fromString("880e8400-e29b-41d4-a716-446655440003"),
                            productName = "Test Product",
                            quantity = 2,
                            unitPrice = BigDecimal("10.00"),
                        ),
                    ),
                createdAt = Instant.ofEpochMilli(1700000000000),
                updatedAt = Instant.now(),
            )

        orderEventPublisher.publishOrderCreated(testOrder)
    }

    /**
     * Contract Test: OrderConfirmed 이벤트 발행 트리거
     */
    fun triggerOrderConfirmedEvent() {
        val testOrder =
            Order(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                userId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001"),
                storeId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002"),
                status = OrderStatus.STOCK_RESERVED,
                totalAmount = BigDecimal("20.00"),
                items =
                    listOf(
                        OrderItem(
                            id = UUID.randomUUID(),
                            orderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                            productId = UUID.fromString("880e8400-e29b-41d4-a716-446655440003"),
                            productName = "Test Product",
                            quantity = 2,
                            unitPrice = BigDecimal("10.00"),
                        ),
                    ),
                createdAt = Instant.ofEpochMilli(1700000000000),
                updatedAt = Instant.now(),
            )

        orderEventPublisher.publishOrderConfirmed(testOrder)
    }

    /**
     * Contract Test: OrderCancelled 이벤트 발행 트리거
     */
    fun triggerOrderCancelledEvent() {
        val testOrder =
            Order(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                userId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001"),
                storeId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002"),
                status = OrderStatus.ORDER_CANCELLED,
                totalAmount = BigDecimal("20.00"),
                items =
                    listOf(
                        OrderItem(
                            id = UUID.randomUUID(),
                            orderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                            productId = UUID.fromString("880e8400-e29b-41d4-a716-446655440003"),
                            productName = "Test Product",
                            quantity = 2,
                            unitPrice = BigDecimal("10.00"),
                        ),
                    ),
                failureReason = "Stock reservation failed",
                createdAt = Instant.ofEpochMilli(1700000000000),
                updatedAt = Instant.now(),
            )

        orderEventPublisher.publishOrderCancelled(testOrder)
    }
}

