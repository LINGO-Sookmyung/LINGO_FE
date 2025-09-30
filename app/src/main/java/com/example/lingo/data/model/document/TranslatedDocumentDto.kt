package com.example.lingo.data.model.document

data class TranslatedDocumentDto(
    val rawDocumentId: Long,
    val documentType: String,     // "CORPORATE_REGISTER" 등
    val country: String,          // "USA" 등
    val translatedDocumentId: Long,
    val translatedDocumentName: String,
    val translatedFilePath: String,
    val presignedDownloadUrl: String
)