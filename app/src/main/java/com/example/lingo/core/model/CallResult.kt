package com.example.lingo.core.model

sealed class CallResult<out T> {
    data class Success<T>(val data: T) : CallResult<T>()
    data class Error(
        val code: Int? = null,
        val message: String? = null,
        val cause: Throwable? = null
    ) : CallResult<Nothing>()
}
