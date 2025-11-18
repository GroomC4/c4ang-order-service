package com.groom.order.configuration.event

import com.groom.order.common.domain.DomainEvent
import com.groom.order.common.domain.DomainEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Spring의 ApplicationEventPublisher를 사용하는 도메인 이벤트 발행자 구현체.
 */
@Component
class SpringDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
