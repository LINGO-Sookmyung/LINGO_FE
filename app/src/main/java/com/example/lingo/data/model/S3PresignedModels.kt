package com.example.lingo.data.model

// 업로드 presigned 응답
data class PresignedUploadResponse(
    val status: Int,
    val message: String,
    val data: List<PresignedItem>,
    val success: Boolean
)

data class PresignedItem(
    val path: String,   // presigned URL (PUT 대상)
    val s3Key: String   // 서버가 인식하는 파일 키
)

// 다운로드 presigned 응답
data class PresignedDownloadResponse(
    val path: String,   // presigned URL (GET 대상)
    val s3Key: String
)
