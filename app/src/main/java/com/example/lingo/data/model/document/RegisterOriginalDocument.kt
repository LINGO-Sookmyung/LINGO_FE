package com.example.lingo.data.model.document

data class RegisterOriginalReq(
    val requestDTO: RequestDTO,
    val fileNames: List<String>   // 서버가 기대하는 "파일명 리스트" (예: ["1.jpg","2.jpg"])
)

data class RequestDTO(
    val documentType: String,
    val totalPages: Int,
    val country: String,        // VIETNAM | USA | JAPAN | CHINA
    val language: String,       // ENGLISH | JAPANESE | CHINESE | VIETNAMESE
    val issuanceChannel: String,// INTERNET | GOVERNMENT | KIOSK | OTHER
    val orientation: String     // PORTRAIT | LANDSCAPE
)

data class BaseEnvelope(
    val status: Int,
    val message: String?,
    val data: Any?,
    val success: Boolean
)