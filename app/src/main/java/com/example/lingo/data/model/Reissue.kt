package com.example.lingo.data.model

import com.google.gson.annotations.SerializedName

data class ReissueRequest(
    @com.google.gson.annotations.SerializedName("refreshToken")
    val refreshToken: String
)

data class ReissueResponse(
    val memberId: Long,
    val email: String,
    val jwtToken: JwtToken
) {
    data class JwtToken(
        val grantType: String,      // "Bearer"
        val accessToken: String,
        val refreshToken: String
    )
}

// 로그아웃 시 서버가 refresh를 받아 블랙리스트 처리하는 경우 대비
data class LogoutRequest(
    @SerializedName("refreshToken") val refreshToken: String
)