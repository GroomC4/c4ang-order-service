package com.groom.order.application.service

import com.groom.order.application.dto.CheckHasPaymentResult
import com.groom.order.application.dto.GetInternalOrderResult
import com.groom.order.application.dto.MarkPaymentPendingResult
import com.groom.order.common.exception.OrderException
import com.groom.order.domain.model.OrderStatus
import com.groom.order.domain.port.LoadOrderPort
import com.groom.order.domain.port.SaveOrderPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Internal API 서비스
 *
 * Payment Service 등 내부 서비스에서 호출하는 주문 관련 기능을 제공합니다.
 * 인증/인가는 네트워크 레벨(Service Mesh)에서 처리되므로 별도 검증하지 않습니다.
 */
@Service
class OrderInternalService(
    private val loadOrderPort: LoadOrderPort,
    private val saveOrderPort: SaveOrderPort,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 조회
     *
     * Payment Service에서 결제 요청 시 주문 정보를 검증하기 위해 사용합니다.
     *
     * @param orderId 주문 ID
     * @return 주문 조회 결과
     * @throws OrderException.OrderNotFound 주문이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    fun getOrder(orderId: UUID): GetInternalOrderResult {
        logger.debug { "Internal API: Getting order $orderId" }

        val order = loadOrderPort.loadById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)

        return GetInternalOrderResult.from(order)
    }

    /**
     * 결제 대기 상태로 변경
     *
     * Payment Service에서 Payment 생성 시점에 Order와 Payment를 연결합니다.
     * ORDER_CONFIRMED → PAYMENT_PENDING 상태 전이가 발생합니다.
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @return 상태 변경 결과
     * @throws OrderException.OrderNotFound 주문이 존재하지 않는 경우
     * @throws OrderException.PaymentAlreadyExists 이미 결제가 연결된 경우
     * @throws OrderException.OrderStatusInvalid 주문 상태가 ORDER_CONFIRMED가 아닌 경우
     */
    @Transactional
    fun markPaymentPending(orderId: UUID, paymentId: UUID): MarkPaymentPendingResult {
        logger.info { "Internal API: Marking order $orderId as payment pending with paymentId $paymentId" }

        val order = loadOrderPort.loadById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)

        // 이미 결제가 연결되어 있는지 확인
        if (order.paymentId != null) {
            throw OrderException.PaymentAlreadyExists(orderId, order.paymentId!!)
        }

        // 주문 상태가 ORDER_CONFIRMED인지 확인
        if (order.status != OrderStatus.ORDER_CONFIRMED) {
            throw OrderException.OrderStatusInvalid(
                orderId = orderId,
                currentStatus = order.status.name,
                requiredStatus = OrderStatus.ORDER_CONFIRMED.name,
            )
        }

        order.markPaymentPending(paymentId)
        saveOrderPort.save(order)

        logger.info { "Order $orderId marked as payment pending successfully" }

        return MarkPaymentPendingResult.from(order)
    }

    /**
     * 결제 존재 여부 확인
     *
     * 주문당 결제 1개 제한 비즈니스 규칙을 검증하기 위해 사용합니다.
     *
     * @param orderId 주문 ID
     * @return 결제 존재 여부 결과
     * @throws OrderException.OrderNotFound 주문이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    fun checkHasPayment(orderId: UUID): CheckHasPaymentResult {
        logger.debug { "Internal API: Checking if order $orderId has payment" }

        val order = loadOrderPort.loadById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)

        return CheckHasPaymentResult.from(order)
    }
}
