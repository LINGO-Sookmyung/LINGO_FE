package com.example.lingo.data.repository

import com.google.gson.Gson
import com.example.lingo.data.model.ApiError
import com.example.lingo.data.model.SignupRequest
import com.example.lingo.data.model.SignupResponse
import com.example.lingo.data.remote.RetrofitClient

sealed class Result<out T> {
    data class Success<T>(val data: T): Result<T>()
    data class Fail(val code: Int?, val error: ApiError?): Result<Nothing>()
}

class AuthRepository(
    private val gson: Gson = Gson()
) {
    suspend fun signup(req: SignupRequest): Result<SignupResponse> {
        val res = RetrofitClient.api.signup(req)
        return if (res.isSuccessful) {
            Result.Success(res.body()!!)
        } else {
            val parsed = try {
                gson.fromJson(res.errorBody()?.charStream(), ApiError::class.java)
            } catch (_: Exception) { null }
            Result.Fail(res.code(), parsed)
        }
    }
}