package com.groom.order.common.domain

import java.time.LocalDateTime

/**
 * 도메인 이벤트 마커 인터페이스.
 * 모든 도메인 이벤트는 이 인터페이스를 구현해야 한다.
 */
interface DomainEvent {
    val occurredAt: LocalDateTime
        get() = LocalDateTime.now()
}
