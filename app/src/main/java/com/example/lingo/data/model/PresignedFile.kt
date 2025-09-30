package com.example.lingo.data.model

data class PresignedSingleReq(
    val filename: String
)

data class PresignedSingleRes( // 단건 응답이 순수 객체라면 이걸로
    val path: String,
    val s3Key: String
)

data class PresignedMultiEnvelope<T>(
    val status: Int,
    val message: String?,
    val data: T?,
    val success: Boolean
)