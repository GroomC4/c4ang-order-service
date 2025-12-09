package com.groom.order.common.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 로컬 개발환경용 인증 필터
 *
 * Istio API Gateway가 없는 로컬 환경에서 개발할 수 있도록
 * X-User-Id, X-User-Role 헤더를 자동으로 주입합니다.
 *
 * - @Profile("local"): local 프로파일에서만 활성화
 * - 기존 헤더가 있으면 그대로 사용 (테스트 시 커스텀 헤더 가능)
 * - 없으면 기본 CUSTOMER 사용자로 설정
 *
 * 사용법:
 * ```
 * ./gradlew bootRun --args='--spring.profiles.active=local'
 * ```
 */
@Profile("local")
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LocalDevAuthFilter : OncePerRequestFilter() {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val USER_ID_HEADER = "X-User-Id"
        private const val USER_ROLE_HEADER = "X-User-Role"
        // 기본 로컬 개발용 사용자
        private const val DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001"
        private const val DEFAULT_USER_ROLE = "CUSTOMER"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val userId = request.getHeader(USER_ID_HEADER)
        val userRole = request.getHeader(USER_ROLE_HEADER)

        // 이미 헤더가 있으면 그대로 통과 (테스트 등에서 커스텀 헤더 사용 가능)
        if (userId != null && userRole != null) {
            logger.debug { "Using existing Istio headers: userId=$userId, role=$userRole" }
            filterChain.doFilter(request, response)
            return
        }

        // 헤더가 없으면 기본 Mock 사용자로 주입
        logger.debug { "Injecting mock Istio headers for local development: userId=$DEFAULT_USER_ID, role=$DEFAULT_USER_ROLE" }

        val wrappedRequest =
            object : HttpServletRequestWrapper(request) {
                override fun getHeader(name: String): String? =
                    when (name) {
                        USER_ID_HEADER -> userId ?: DEFAULT_USER_ID
                        USER_ROLE_HEADER -> userRole ?: DEFAULT_USER_ROLE
                        else -> super.getHeader(name)
                    }

                override fun getHeaders(name: String): java.util.Enumeration<String> {
                    val header = getHeader(name)
                    return if (header != null) {
                        java.util.Collections.enumeration(listOf(header))
                    } else {
                        super.getHeaders(name)
                    }
                }

                override fun getHeaderNames(): java.util.Enumeration<String> {
                    val names = mutableSetOf<String>()
                    super.getHeaderNames()?.toList()?.let { names.addAll(it) }
                    if (userId == null) names.add(USER_ID_HEADER)
                    if (userRole == null) names.add(USER_ROLE_HEADER)
                    return java.util.Collections.enumeration(names)
                }
            }

        filterChain.doFilter(wrappedRequest, response)
    }
}
