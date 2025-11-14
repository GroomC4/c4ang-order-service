package com.groom.order.configuration.jpa

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Optional

@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
@Configuration
class AuditorAwareConfig {
    @Bean
    fun auditorProvider(): AuditorAware<String> = AuditorAware { Optional.of("system") }

    @Bean
    fun offsetDateTimeProvider(): DateTimeProvider = DateTimeProvider { Optional.of(OffsetDateTime.now(ZoneId.systemDefault())) }
}
