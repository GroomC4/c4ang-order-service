package com.groom.order.domain.service

import com.groom.order.domain.model.OrderAudit
import com.groom.order.domain.model.OrderAuditEventType
import com.groom.order.infrastructure.repository.OrderAuditRepositoryImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 감사 로그 기록 서비스
 *
 * 모든 주문 관련 도메인 이벤트를 감사 로그로 기록합니다.
 * 독립 트랜잭션(REQUIRES_NEW)으로 실행되어 메인 트랜잭션 실패와 무관하게 기록을 보장합니다.
 */
@Service
class OrderAuditRecorder(
    private val orderAuditRepository: OrderAuditRepositoryImpl,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 감사 로그 기록
     *
     * @param orderId 주문 ID
     * @param eventType 이벤트 타입
     * @param changeSummary 변경 사항 요약
     * @param actorUserId 변경 수행자 (선택)
     * @param metadata 추가 정보 (선택)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        orderId: UUID,
        eventType: OrderAuditEventType,
        changeSummary: String,
        actorUserId: UUID? = null,
        metadata: Map<String, Any>? = null,
    ) {
        try {
            val audit =
                OrderAudit(
                    orderId = orderId,
                    eventType = eventType,
                    changeSummary = changeSummary,
                    actorUserId = actorUserId,
                    recordedAt = LocalDateTime.now(),
                    metadata = metadata,
                )

            orderAuditRepository.save(audit)

            logger.debug { "Order audit recorded: orderId=$orderId, eventType=$eventType" }
        } catch (e: Exception) {
            // 감사 로그 기록 실패는 메인 비즈니스 로직에 영향을 주지 않도록 로그만 남김
            logger.error(e) { "Failed to record order audit: orderId=$orderId, eventType=$eventType" }
        }
    }

    /**
     * 주문 아이템 단위 감사 로그 기록
     *
     * @param orderId 주문 ID
     * @param orderItemId 주문 아이템 ID
     * @param eventType 이벤트 타입
     * @param changeSummary 변경 사항 요약
     * @param actorUserId 변경 수행자 (선택)
     * @param metadata 추가 정보 (선택)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordItem(
        orderId: UUID,
        orderItemId: UUID,
        eventType: OrderAuditEventType,
        changeSummary: String,
        actorUserId: UUID? = null,
        metadata: Map<String, Any>? = null,
    ) {
        try {
            val audit =
                OrderAudit(
                    orderId = orderId,
                    orderItemId = orderItemId,
                    eventType = eventType,
                    changeSummary = changeSummary,
                    actorUserId = actorUserId,
                    recordedAt = LocalDateTime.now(),
                    metadata = metadata,
                )

            orderAuditRepository.save(audit)

            logger.debug { "Order item audit recorded: orderId=$orderId, orderItemId=$orderItemId, eventType=$eventType" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to record order item audit: orderId=$orderId, orderItemId=$orderItemId" }
        }
    }
}
