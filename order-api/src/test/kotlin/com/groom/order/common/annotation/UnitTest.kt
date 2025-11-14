package com.groom.order.common.annotation

import org.junit.jupiter.api.Tag
import org.springframework.test.context.ActiveProfiles

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("unit-test")
@ActiveProfiles("test")
annotation class UnitTest
