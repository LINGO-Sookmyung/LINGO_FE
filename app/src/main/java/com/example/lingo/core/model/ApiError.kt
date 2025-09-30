package com.example.lingo.core.model

data class ApiError(
    val message: String,
    val status: Int?,
    val error: String?
)