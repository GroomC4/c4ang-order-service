package com.groom.order.application.service

import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import java.math.BigDecimal
import java.util.UUID

/**
 * CreateOrderService 통합 테스트
 *
 * TODO: 이벤트 기반 아키텍처에 맞게 재작성 필요
 *
 * 재작성 방향:
 * 1. 주문 생성 테스트
 *    - CreateOrderService.createOrder() 호출
 *    - 주문이 ORDER_CREATED 상태로 DB에 저장됨
 *    - order.created 이벤트가 Kafka로 발행됨
 *
 * 2. 멱등성 테스트
 *    - 동일한 idempotencyKey로 중복 호출 시 기존 주문 반환
 *
 * 3. 외부 이벤트 수신 후 상태 변경 테스트 (KafkaTestHelper 사용)
 *    - stock.reserved 이벤트 수신 → ORDER_CONFIRMED 상태로 변경
 *    - stock.reservation.failed 이벤트 수신 → ORDER_CANCELLED 상태로 변경
 *
 * Note:
 * - Store 검증은 TestStoreClient로 처리됨 (HTTP 호출 Mock)
 * - 재고 관리는 Product Service 책임 (이 테스트에서 검증하지 않음)
 *
 * @see com.groom.order.common.kafka.KafkaTestHelper 외부 이벤트 시뮬레이션
 * @see com.groom.order.adapter.outbound.client.TestStoreClient Store 서비스 Mock
 */
@Disabled("이벤트 기반 아키텍처에 맞게 재작성 필요")
@SqlGroup(
    Sql(
        scripts = ["/sql/cleanup-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
    ),
    Sql(
        scripts = ["/sql/init-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    ),
    Sql(
        scripts = ["/sql/cleanup-order-command-business-test.sql"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
    ),
)
@DisplayName("CreateOrderService 통합 테스트")
class CreateOrderServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var createOrderService: CreateOrderService

    @Autowired
    private lateinit var loadOrderPort: LoadOrderPort

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    @Autowired
    private lateinit var entityManager: EntityManager

    companion object {
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val STORE_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001")
        private val STORE_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000002")
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        private val PRODUCT_KEYBOARD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")
        private val PRODUCT_LOW_STOCK = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003")
    }


    @Test
    @DisplayName("단일 상품 주문 생성 성공")
    fun createOrder_withSingleProduct_shouldSucceed() {
        // given
        val command =
            CreateOrderCommand(
                userExternalId = CUSTOMER_USER_1,
                storeId = STORE_1,
                idempotencyKey = "test-service-single-${UUID.randomUUID()}",
                items =
                    listOf(
                        CreateOrderCommand.OrderItemDto(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 2,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                note = "서비스 통합 테스트",
            )

        // when
        val result = createOrderService.createOrder(command)

        // then
        assertThat(result.orderId).isNotNull()
        assertThat(result.orderNumber).startsWith("ORD-")
        assertThat(result.status).isEqualTo(OrderStatus.ORDER_CONFIRMED)
        assertThat(result.reservationId).isNotNull()
        assertThat(result.expiresAt).isNotNull()
        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].productName).isEqualTo("Gaming Mouse")
        assertThat(result.items[0].quantity).isEqualTo(2)

        // DB 검증
        val savedOrder = loadOrderPort.loadById(result.orderId)
        assertThat(savedOrder).isNotNull
        assertThat(savedOrder!!.status).isEqualTo(OrderStatus.ORDER_CONFIRMED)
    }

    @Test
    @DisplayName("여러 상품 주문 생성 성공")
    fun createOrder_withMultipleProducts_shouldSucceed() {
        // given
        val command =
            CreateOrderCommand(
                userExternalId = CUSTOMER_USER_1,
                storeId = STORE_1,
                idempotencyKey = "test-service-multi-${UUID.randomUUID()}",
                items =
                    listOf(
                        CreateOrderCommand.OrderItemDto(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 1,
                            unitPrice = BigDecimal("29000"),
                        ),
                        CreateOrderCommand.OrderItemDto(
                            productId = PRODUCT_KEYBOARD,
                            productName = "Mechanical Keyboard",
                            quantity = 1,
                            unitPrice = BigDecimal("89000"),
                        ),
                    ),
                note = null,
            )

        // when
        val result = createOrderService.createOrder(command)

        // then
        assertThat(result.items).hasSize(2)
        assertThat(result.totalAmount.toLong()).isEqualTo(118000L) // 29000 + 89000
    }

    @Test
    @DisplayName("동일한 멱등성 키로 중복 주문 시 기존 주문 반환")
    fun createOrder_withDuplicateIdempotencyKey_shouldReturnExistingOrder() {
        // given
        val idempotencyKey = "test-service-idempotent-${UUID.randomUUID()}"
        val command =
            CreateOrderCommand(
                userExternalId = CUSTOMER_USER_1,
                storeId = STORE_1,
                idempotencyKey = idempotencyKey,
                items =
                    listOf(
                        CreateOrderCommand.OrderItemDto(
                            productId = PRODUCT_MOUSE,
                            productName = "Gaming Mouse",
                            quantity = 1,
                            unitPrice = BigDecimal("29000"),
                        ),
                    ),
                note = null,
            )

        // when: 첫 번째 주문 생성
        val firstResult = createOrderService.createOrder(command)

        // when: 동일한 멱등성 키로 두 번째 주문 시도
        val secondResult = createOrderService.createOrder(command)

        // then: 같은 주문 ID 반환
        assertThat(secondResult.orderId).isEqualTo(firstResult.orderId)
        assertThat(secondResult.orderNumber).isEqualTo(firstResult.orderNumber)
    }

    // NOTE: 이벤트 기반 아키텍처로 전환 후 아래 테스트들은 더 이상 유효하지 않음
    // - Store/Product 검증은 Product Service에서 처리
    // - 재고 예약/차감은 Product Service에서 처리
    // Step 6에서 Kafka 이벤트 기반 통합 테스트로 재작성 필요
}
