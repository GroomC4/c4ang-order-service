package com.groom.order.domain.service

import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 주문 번호 생성기
 *
 * 주문 번호 형식: ORD-YYYYMMDD-{6자리난수}
 * 예: ORD-20251028-A1B2C3
 */
@Component
class OrderNumberGenerator {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 주문 번호 생성
     *
     * @param now 기준 시각 (테스트용)
     * @return 생성된 주문 번호 (형식: ORD-YYYYMMDD-XXXXXX)
     */
    fun generate(now: LocalDateTime = LocalDateTime.now()): String {
        val datePart = now.format(dateFormatter)
        val randomPart = generateRandomCode(6)
        return "ORD-$datePart-$randomPart"
    }

    private fun generateRandomCode(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}
