package com.groom.order.adapter.outbound.messaging

import com.groom.ecommerce.order.event.avro.CancellationReason
import com.groom.ecommerce.order.event.avro.OrderCancelled
import com.groom.ecommerce.order.event.avro.OrderConfirmed
import com.groom.ecommerce.order.event.avro.OrderCreated
import com.groom.order.configuration.kafka.KafkaTopicProperties
import com.groom.order.domain.model.Order
import com.groom.order.domain.model.OrderItem
import com.groom.order.domain.model.OrderStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture

@DisplayName("KafkaOrderEventPublisher 단위 테스트")
class KafkaOrderEventPublisherTest {

    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    private lateinit var topicProperties: KafkaTopicProperties
    private lateinit var publisher: KafkaOrderEventPublisher

    @BeforeEach
    fun setup() {
        kafkaTemplate = mockk()
        topicProperties = KafkaTopicProperties(
            orderCreated = "order.created",
            orderConfirmed = "order.confirmed",
            orderCancelled = "order.cancelled",
        )
        publisher = KafkaOrderEventPublisher(kafkaTemplate, topicProperties)
    }

    private fun createTestOrder(
        status: OrderStatus = OrderStatus.PENDING,
        confirmedAt: LocalDateTime? = null,
        cancelledAt: LocalDateTime? = null,
    ): Order {
        val order = Order(
            userExternalId = UUID.randomUUID(),
            storeId = UUID.randomUUID(),
            orderNumber = "ORD-20241204-001",
            status = status,
            paymentSummary = mapOf("method" to "CARD"),
            timeline = listOf(mapOf("status" to status.name, "at" to LocalDateTime.now().toString())),
            confirmedAt = confirmedAt,
            cancelledAt = cancelledAt,
        )

        val item1 = OrderItem(
            productId = UUID.randomUUID(),
            productName = "테스트 상품 1",
            quantity = 2,
            unitPrice = BigDecimal("10000"),
        )
        val item2 = OrderItem(
            productId = UUID.randomUUID(),
            productName = "테스트 상품 2",
            quantity = 1,
            unitPrice = BigDecimal("25000"),
        )

        order.addItem(item1)
        order.addItem(item2)

        return order
    }

    private fun mockKafkaSendSuccess(): CompletableFuture<SendResult<String, Any>> {
        val future = CompletableFuture<SendResult<String, Any>>()
        val recordMetadata = RecordMetadata(
            TopicPartition("test-topic", 0),
            0L, 0, 0L, 0, 0
        )
        val sendResult = mockk<SendResult<String, Any>>()
        every { sendResult.recordMetadata } returns recordMetadata
        future.complete(sendResult)
        return future
    }

    @Nested
    @DisplayName("publishOrderCreated")
    inner class PublishOrderCreatedTest {

        @Test
        @DisplayName("주문 생성 이벤트가 올바른 토픽과 파티션 키로 발행된다")
        fun `주문 생성 이벤트가 올바른 토픽과 파티션 키로 발행된다`() {
            // given
            val order = createTestOrder()
            val topicSlot = slot<String>()
            val keySlot = slot<String>()
            val eventSlot = slot<OrderCreated>()

            every {
                kafkaTemplate.send(capture(topicSlot), capture(keySlot), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCreated(order)

            // then
            verify(exactly = 1) { kafkaTemplate.send(any<String>(), any(), any()) }

            topicSlot.captured shouldBe "order.created"
            keySlot.captured shouldBe order.id.toString()
        }

        @Test
        @DisplayName("OrderCreated 이벤트에 주문 정보가 올바르게 매핑된다")
        fun `OrderCreated 이벤트에 주문 정보가 올바르게 매핑된다`() {
            // given
            val order = createTestOrder()
            val eventSlot = slot<OrderCreated>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCreated(order)

            // then
            val event = eventSlot.captured

            event.eventId.shouldNotBeEmpty()
            event.orderId shouldBe order.id.toString()
            event.userId shouldBe order.userExternalId.toString()
            event.storeId shouldBe order.storeId.toString()
            event.totalAmount shouldBe order.calculateTotalAmount()
            event.items shouldHaveSize 2
        }

        @Test
        @DisplayName("OrderCreated 이벤트의 items가 올바르게 변환된다")
        fun `OrderCreated 이벤트의 items가 올바르게 변환된다`() {
            // given
            val order = createTestOrder()
            val eventSlot = slot<OrderCreated>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCreated(order)

            // then
            val event = eventSlot.captured
            val avroItems = event.items

            avroItems shouldHaveSize 2
            avroItems[0].productId shouldBe order.items[0].productId.toString()
            avroItems[0].quantity shouldBe order.items[0].quantity
            avroItems[0].unitPrice shouldBe order.items[0].unitPrice
            avroItems[1].productId shouldBe order.items[1].productId.toString()
            avroItems[1].quantity shouldBe order.items[1].quantity
        }

        @Test
        @DisplayName("totalAmount가 정확히 계산된다")
        fun `totalAmount가 정확히 계산된다`() {
            // given
            val order = createTestOrder() // 10000*2 + 25000*1 = 45000
            val eventSlot = slot<OrderCreated>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCreated(order)

            // then
            val event = eventSlot.captured
            event.totalAmount shouldBe BigDecimal("45000")
        }
    }

    @Nested
    @DisplayName("publishOrderConfirmed")
    inner class PublishOrderConfirmedTest {

        @Test
        @DisplayName("주문 확정 이벤트가 올바른 토픽과 파티션 키로 발행된다")
        fun `주문 확정 이벤트가 올바른 토픽과 파티션 키로 발행된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.PREPARING,
                confirmedAt = LocalDateTime.now(),
            )
            val topicSlot = slot<String>()
            val keySlot = slot<String>()

            every {
                kafkaTemplate.send(capture(topicSlot), capture(keySlot), any())
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderConfirmed(order)

            // then
            topicSlot.captured shouldBe "order.confirmed"
            keySlot.captured shouldBe order.id.toString()
        }

        @Test
        @DisplayName("OrderConfirmed 이벤트에 주문 정보가 올바르게 매핑된다")
        fun `OrderConfirmed 이벤트에 주문 정보가 올바르게 매핑된다`() {
            // given
            val confirmedAt = LocalDateTime.now()
            val order = createTestOrder(
                status = OrderStatus.PREPARING,
                confirmedAt = confirmedAt,
            )
            val eventSlot = slot<OrderConfirmed>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderConfirmed(order)

            // then
            val event = eventSlot.captured

            event.eventId.shouldNotBeEmpty()
            event.orderId shouldBe order.id.toString()
            event.userId shouldBe order.userExternalId.toString()
            event.totalAmount shouldBe order.calculateTotalAmount()
            event.confirmedAt shouldNotBe null
        }
    }

    @Nested
    @DisplayName("publishOrderCancelled")
    inner class PublishOrderCancelledTest {

        @Test
        @DisplayName("주문 취소 이벤트가 올바른 토픽과 파티션 키로 발행된다")
        fun `주문 취소 이벤트가 올바른 토픽과 파티션 키로 발행된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val topicSlot = slot<String>()
            val keySlot = slot<String>()

            every {
                kafkaTemplate.send(capture(topicSlot), capture(keySlot), any())
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, "사용자 요청")

            // then
            topicSlot.captured shouldBe "order.cancelled"
            keySlot.captured shouldBe order.id.toString()
        }

        @Test
        @DisplayName("OrderCancelled 이벤트에 취소된 상품 목록이 포함된다")
        fun `OrderCancelled 이벤트에 취소된 상품 목록이 포함된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val eventSlot = slot<OrderCancelled>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, "재고 부족")

            // then
            val event = eventSlot.captured

            event.items shouldHaveSize 2
            event.items[0].productId shouldBe order.items[0].productId.toString()
            event.items[0].quantity shouldBe order.items[0].quantity
        }

        @Test
        @DisplayName("취소 사유가 null이면 USER_REQUESTED로 매핑된다")
        fun `취소 사유가 null이면 USER_REQUESTED로 매핑된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val eventSlot = slot<OrderCancelled>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, null)

            // then
            eventSlot.captured.cancellationReason shouldBe CancellationReason.USER_REQUESTED
        }

        @Test
        @DisplayName("timeout 키워드가 포함되면 PAYMENT_TIMEOUT으로 매핑된다")
        fun `timeout 키워드가 포함되면 PAYMENT_TIMEOUT으로 매핑된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val eventSlot = slot<OrderCancelled>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, "Payment timeout after 30 minutes")

            // then
            eventSlot.captured.cancellationReason shouldBe CancellationReason.PAYMENT_TIMEOUT
        }

        @Test
        @DisplayName("재고 키워드가 포함되면 STOCK_UNAVAILABLE로 매핑된다")
        fun `재고 키워드가 포함되면 STOCK_UNAVAILABLE로 매핑된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val eventSlot = slot<OrderCancelled>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, "재고 부족으로 취소")

            // then
            eventSlot.captured.cancellationReason shouldBe CancellationReason.STOCK_UNAVAILABLE
        }

        @Test
        @DisplayName("stock 키워드가 포함되면 STOCK_UNAVAILABLE로 매핑된다")
        fun `stock 키워드가 포함되면 STOCK_UNAVAILABLE로 매핑된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val eventSlot = slot<OrderCancelled>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, "Insufficient stock")

            // then
            eventSlot.captured.cancellationReason shouldBe CancellationReason.STOCK_UNAVAILABLE
        }

        @Test
        @DisplayName("사용자 관련 키워드가 포함되면 USER_REQUESTED로 매핑된다")
        fun `사용자 관련 키워드가 포함되면 USER_REQUESTED로 매핑된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val eventSlot = slot<OrderCancelled>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, "고객 요청으로 취소")

            // then
            eventSlot.captured.cancellationReason shouldBe CancellationReason.USER_REQUESTED
        }

        @Test
        @DisplayName("알 수 없는 사유는 SYSTEM_ERROR로 매핑된다")
        fun `알 수 없는 사유는 SYSTEM_ERROR로 매핑된다`() {
            // given
            val order = createTestOrder(
                status = OrderStatus.ORDER_CANCELLED,
                cancelledAt = LocalDateTime.now(),
            )
            val eventSlot = slot<OrderCancelled>()

            every {
                kafkaTemplate.send(any<String>(), any(), capture(eventSlot))
            } returns mockKafkaSendSuccess()

            // when
            publisher.publishOrderCancelled(order, "알 수 없는 오류 발생")

            // then
            eventSlot.captured.cancellationReason shouldBe CancellationReason.SYSTEM_ERROR
        }
    }
}
