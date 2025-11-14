package com.groom.order.common.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * AiPromptAudit 엔티티.
 * DDL: p_ai_prompt_audit 테이블
 *
 * AI API 요청 질문과 응답을 모두 저장하는 감사 로그입니다.
 */
@Entity
@Table(name = "p_ai_prompt_audit")
class AiPromptAudit(
    @Column
    val userId: UUID?,
    @Column(nullable = false, columnDefinition = "text")
    val prompt: String,
    @Column(columnDefinition = "text")
    val response: String?,
    @Column
    val model: String?,
    @Column(nullable = false)
    val success: Boolean = false,
    @Column(columnDefinition = "text")
    val errorMessage: String?,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(columnDefinition = "jsonb")
    val metadata: String? = null,
) {
    @Id
    @Column(columnDefinition = "uuid", updatable = false)
    var id: UUID = UUID.randomUUID()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiPromptAudit) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "AiPromptAudit(id=$id, userId=$userId, success=$success)"
}
