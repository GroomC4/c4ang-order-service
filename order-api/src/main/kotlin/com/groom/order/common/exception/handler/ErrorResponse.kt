package com.groom.ecommerce.common.exception.handler

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "에러 응답")
data class ErrorResponse(
    @Schema(description = "에러 구분코드")
    val code: String,
    @Schema(description = "에러 메세지")
    val message: String,
)
