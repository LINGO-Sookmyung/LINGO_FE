package com.example.lingo.data.model.document

import com.google.gson.JsonObject

data class GenerateDocRequest(
    val rawDocumentId: Long,
    val editedContentJson: JsonObject
)

data class GenerateDocResponse(
    val rawDocumentId: Long,
    val translatedDocumentId: Long,
    val translatedName: String,       // 예: 최종 파일 표시명
    val resultS3Key: String,          // S3 키 (백업용)
    val presignedDownloadUrl: String, // 최종 결과 다운로드 URL
    val contentType: String           // 예: "application/pdf"
)
