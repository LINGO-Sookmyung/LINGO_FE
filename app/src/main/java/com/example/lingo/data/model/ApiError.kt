package com.example.lingo.data.model

data class ApiError(
    val message: String,
    val status: Int?,
    val error: String?
)