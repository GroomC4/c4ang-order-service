package com.groom.order.application.service

import com.groom.order.application.dto.CreateOrderCommand
import com.groom.order.common.IntegrationTestBase
import com.groom.order.common.TransactionApplier
import com.groom.order.common.exception.OrderException
import com.groom.order.common.exception.ProductException
import com.groom.order.common.exception.StoreException
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import java.util.UUID

/**
 * CreateOrderService 통합 테스트
 *
 * 주문 생성 서비스의 전체 플로우를 검증합니다.
 * - DB, Redis와 실제 통합
 * - 멱등성, 재고 예약, 주문 생성 등 모든 분기 테스트
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
@DisplayName("CreateOrderService 통합 테스트")
class CreateOrderServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var createOrderService: CreateOrderService

    @Autowired
    private lateinit var loadOrderPort: LoadOrderPort

    @Autowired
    private lateinit var redissonClient: RedissonClient

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

    @BeforeEach
    fun setUp() {
        // Redis 재고 초기화
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").set(100)
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_KEYBOARD").set(50)
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_LOW_STOCK").set(2)
    }

    @AfterEach
    fun tearDown() {
        // Redis 키 정리
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").delete()
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_KEYBOARD").delete()
        redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_LOW_STOCK").delete()

        val expiryIndex = redissonClient.getScoredSortedSet<String>("product:reservation-expiry-index")
        expiryIndex.clear()
    }

    @Test
    @DisplayName("단일 상품 주문 생성 성공")
    fun createOrder_withSingleProduct_shouldSucceed() {
        // given
        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = STORE_1,
            idempotencyKey = "test-service-single-${UUID.randomUUID()}",
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_MOUSE,
                    quantity = 2,
                ),
            ),
            note = "서비스 통합 테스트",
        )

        // when
        val result = createOrderService.createOrder(command)

        // then
        assertThat(result.orderId).isNotNull()
        assertThat(result.orderNumber).startsWith("ORD-")
        assertThat(result.status).isEqualTo(OrderStatus.STOCK_RESERVED)
        assertThat(result.reservationId).isNotNull()
        assertThat(result.expiresAt).isNotNull()
        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].productName).isEqualTo("Gaming Mouse")
        assertThat(result.items[0].quantity).isEqualTo(2)

        // DB 검증
        val savedOrder = loadOrderPort.loadById(result.orderId)
        assertThat(savedOrder).isNotNull
        assertThat(savedOrder!!.status).isEqualTo(OrderStatus.STOCK_RESERVED)
    }

    @Test
    @DisplayName("여러 상품 주문 생성 성공")
    fun createOrder_withMultipleProducts_shouldSucceed() {
        // given
        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = STORE_1,
            idempotencyKey = "test-service-multi-${UUID.randomUUID()}",
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_MOUSE,
                    quantity = 1,
                ),
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_KEYBOARD,
                    quantity = 1,
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
        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = STORE_1,
            idempotencyKey = idempotencyKey,
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_MOUSE,
                    quantity = 1,
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

    @Test
    @DisplayName("존재하지 않는 스토어로 주문 시 StoreNotFound 예외 발생")
    fun createOrder_withNonExistentStore_shouldThrowStoreNotFound() {
        // given
        val nonExistentStoreId = UUID.randomUUID()
        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = nonExistentStoreId,
            idempotencyKey = "test-service-invalid-store-${UUID.randomUUID()}",
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_MOUSE,
                    quantity = 1,
                ),
            ),
            note = null,
        )

        // when & then
        assertThatThrownBy { createOrderService.createOrder(command) }
            .isInstanceOf(StoreException.StoreNotFound::class.java)
    }

    @Test
    @DisplayName("존재하지 않는 상품으로 주문 시 ProductNotFound 예외 발생")
    fun createOrder_withNonExistentProduct_shouldThrowProductNotFound() {
        // given
        val nonExistentProductId = UUID.randomUUID()
        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = STORE_1,
            idempotencyKey = "test-service-invalid-product-${UUID.randomUUID()}",
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = nonExistentProductId,
                    quantity = 1,
                ),
            ),
            note = null,
        )

        // when & then
        assertThatThrownBy { createOrderService.createOrder(command) }
            .isInstanceOf(ProductException.ProductNotFound::class.java)
    }

    @Test
    @DisplayName("재고 부족 시 예외 발생")
    fun createOrder_withInsufficientStock_shouldThrowException() {
        // given: 재고가 2개인 상품을 3개 주문
        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = STORE_1,
            idempotencyKey = "test-service-low-stock-${UUID.randomUUID()}",
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_LOW_STOCK,
                    quantity = 3,
                ),
            ),
            note = null,
        )

        // when & then
        assertThatThrownBy { createOrderService.createOrder(command) }
            .isInstanceOf(Exception::class.java) // 재고 부족 예외
    }

    @Test
    @DisplayName("다른 스토어의 상품 주문 시 예외 발생")
    fun createOrder_withProductFromDifferentStore_shouldThrowException() {
        // given: STORE_2로 STORE_1의 상품 주문 시도
        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = STORE_2,
            idempotencyKey = "test-service-different-store-${UUID.randomUUID()}",
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_MOUSE, // STORE_1의 상품
                    quantity = 1,
                ),
            ),
            note = null,
        )

        // when & then
        assertThatThrownBy { createOrderService.createOrder(command) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("주문 생성 후 재고가 차감되는지 확인")
    fun createOrder_shouldDeductStock() {
        // given
        val initialStock = redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").get()
        val orderQuantity = 5

        val command = CreateOrderCommand(
            userExternalId = CUSTOMER_USER_1,
            storeId = STORE_1,
            idempotencyKey = "test-service-stock-deduct-${UUID.randomUUID()}",
            items = listOf(
                CreateOrderCommand.OrderItemDto(
                    productId = PRODUCT_MOUSE,
                    quantity = orderQuantity,
                ),
            ),
            note = null,
        )

        // when
        createOrderService.createOrder(command)

        // then
        val remainingStock = redissonClient.getAtomicLong("product:remaining-stock:$PRODUCT_MOUSE").get()
        assertThat(remainingStock).isEqualTo(initialStock - orderQuantity)
    }
}
