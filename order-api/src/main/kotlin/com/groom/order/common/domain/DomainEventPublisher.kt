package com.groom.ecommerce.common.domain

/**
 * 도메인 이벤트를 발행하는 인터페이스.
 * Spring의 ApplicationEventPublisher를 래핑하여 도메인 계층의 의존성을 격리한다.
 */
interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
