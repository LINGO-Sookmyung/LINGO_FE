package com.example.lingo.data.model.storage

/**
 * 공통 API Envelope
 * 서버가 status/message/data/success 형태로 감싸는 응답에 사용
 */
data class ApiEnvelope<T>(
    val status: Int,
    val message: String? = null,
    val data: T? = null,
    val success: Boolean = true
)

/**
 * 사전서명(업로드) 항목: PUT 대상으로 쓰는 presigned URL + 서버 인식 키
 */
data class PresignedItem(
    val path: String,   // PUT 대상 presigned URL
    val s3Key: String   // 서버가 인식하는 파일 키
)

/**
 * 사전서명(단건 링크): GET/PUT 모두 표현 가능
 * (다운로드/단건 업로드 응답 payload 로 공용 활용)
 */
data class PresignedLink(
    val path: String,   // presigned URL
    val s3Key: String
)

/**
 * 사전서명(단건) 요청
 */
data class PresignedSingleRequest(
    val filename: String
)

/* -----------------------------
 * 권장 응답 타입 (Envelope + Payload)
 * ----------------------------- */

/** 업로드용 다건 사전서명 응답 */
typealias PresignedUploadResponse = ApiEnvelope<List<PresignedItem>>

/** 단건 사전서명 응답(업로드/다운로드 공용) */
typealias PresignedSingleResponse = ApiEnvelope<PresignedLink>

/** 다운로드 사전서명 응답 (단건) */
typealias PresignedDownloadResponse = ApiEnvelope<PresignedLink>

/* -----------------------------
 * Backward-compat typealiases
 * ----------------------------- */

@Deprecated("Use PresignedSingleRequest", ReplaceWith("PresignedSingleRequest"))
typealias PresignedSingleReq = PresignedSingleRequest

@Deprecated("Use PresignedSingleResponse (ApiEnvelope<PresignedLink>)")
typealias PresignedSingleRes = PresignedLink

@Deprecated("Use ApiEnvelope<T>", ReplaceWith("ApiEnvelope<T>"))
typealias PresignedMultiEnvelope<T> = ApiEnvelope<T>
