package com.groom.order.application.service

import com.groom.order.adapter.outbound.client.TestStoreClient
import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import java.math.BigDecimal
import java.util.UUID

/**
 * CreateOrderService 통합 테스트 (이벤트 기반 아키텍처)
 *
 * 테스트 범위:
 * 1. 주문 생성 → ORDER_CREATED 상태로 저장
 * 2. 멱등성 검증 (동일 idempotencyKey로 중복 호출 시 기존 주문 반환)
 *
 * Note:
 * - Store 검증: TestStoreClient (Test Bean)
 * - Product/재고 관리: Product Service 책임 (이벤트 기반)
 * - Kafka Consumer 통합 테스트는 안정성 문제로 단위 테스트에서 검증
 */
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
@DisplayName("CreateOrderService 통합 테스트 (이벤트 기반)")
class CreateOrderServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var createOrderService: CreateOrderService

    @Autowired
    private lateinit var loadOrderPort: LoadOrderPort

    @Autowired
    private lateinit var transactionApplier: TransactionApplier

    companion object {
        private val CUSTOMER_USER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
        private val PRODUCT_MOUSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        private val PRODUCT_KEYBOARD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")
    }

    @Nested
    @DisplayName("주문 생성 테스트")
    inner class CreateOrderTest {
        @Test
        @DisplayName("단일 상품 주문 생성 시 ORDER_CREATED 상태로 저장된다")
        fun `should create order with ORDER_CREATED status`() {
            // given
            val command =
                CreateOrderCommand(
                    userExternalId = CUSTOMER_USER_1,
                    storeId = TestStoreClient.STORE_1,
                    idempotencyKey = "test-single-${UUID.randomUUID()}",
                    items =
                        listOf(
                            CreateOrderCommand.OrderItemDto(
                                productId = PRODUCT_MOUSE,
                                productName = "Gaming Mouse",
                                quantity = 2,
                                unitPrice = BigDecimal("29000"),
                            ),
                        ),
                    note = "빠른 배송 부탁드립니다",
                )

            // when
            val result = createOrderService.createOrder(command)

            // then
            assertThat(result.orderId).isNotNull()
            assertThat(result.orderNumber).startsWith("ORD-")
            assertThat(result.status).isEqualTo(OrderStatus.ORDER_CREATED)
            assertThat(result.totalAmount).isEqualByComparingTo(BigDecimal("58000")) // 29000 * 2
            assertThat(result.items).hasSize(1)
            assertThat(result.items[0].productName).isEqualTo("Gaming Mouse")
            assertThat(result.items[0].quantity).isEqualTo(2)

            // DB 검증
            val savedOrder =
                transactionApplier.applyPrimaryTransaction {
                    loadOrderPort.loadById(result.orderId)
                }
            assertThat(savedOrder).isNotNull
            assertThat(savedOrder!!.status).isEqualTo(OrderStatus.ORDER_CREATED)
            assertThat(savedOrder.note).isEqualTo("빠른 배송 부탁드립니다")
        }

        @Test
        @DisplayName("여러 상품 주문 생성 시 총액이 정확히 계산된다")
        fun `should calculate total amount correctly for multiple items`() {
            // given
            val command =
                CreateOrderCommand(
                    userExternalId = CUSTOMER_USER_1,
                    storeId = TestStoreClient.STORE_1,
                    idempotencyKey = "test-multi-${UUID.randomUUID()}",
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
            assertThat(result.totalAmount).isEqualByComparingTo(BigDecimal("118000")) // 29000 + 89000
        }
    }

    @Nested
    @DisplayName("멱등성 테스트")
    inner class IdempotencyTest {
        @Test
        @DisplayName("동일한 멱등성 키로 중복 호출 시 기존 주문이 반환된다")
        fun `should return existing order for duplicate idempotency key`() {
            // given
            val idempotencyKey = "test-idempotent-${UUID.randomUUID()}"
            val command =
                CreateOrderCommand(
                    userExternalId = CUSTOMER_USER_1,
                    storeId = TestStoreClient.STORE_1,
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
    }

    // Note: Kafka Consumer 통합 테스트(stock.reserved, stock.reservation.failed 이벤트 수신)는
    // Testcontainers Kafka의 Consumer 그룹 재균형 및 파티션 할당 지연으로 인해
    // 안정적인 테스트가 어렵습니다.
    //
    // Kafka Consumer의 메시지 처리 로직은 다음 단위 테스트에서 검증됩니다:
    // - StockReservedKafkaListenerTest
    // - StockReservationFailedKafkaListenerTest
    // - PaymentCompletedKafkaListenerTest
    // - PaymentFailedKafkaListenerTest
}
