package com.example.lingo.data.model.auth

data class SignupRequest(
    val email: String,
    val password: String,
    val pwConfirm: String,
    val name: String,
    val birth: String,     // "YYYY-MM-DD"
    val phoneNum: String,
    val memberType: String = "USER"
)

data class SignupResponse(
    val memberId: Long,
    val email: String,
    val name: String,
    val message: String
)

data class SignupCheckEmailRequest(
    val email: String
)

data class SignupCheckEmailResponse(
    val message: String,
    val available: Boolean
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class JwtToken(
    val grantType: String,
    val accessToken: String,
    val refreshToken: String
)

data class LoginResponse(
    val memberId: Long,
    val email: String,
    val jwtToken: JwtToken
)

data class FindEmailRequest(
    val name: String,
    val phoneNum: String
)

data class FindEmailResponse(
    val email: String
)

data class ResetPasswordSendRequest(
    val name: String,
    val email: String
)

data class SimpleMessageResponse(
    val message: String
)

data class ResetPasswordVerifyRequest(
    val email: String,
    val verificationCode: String
)

data class ResetPasswordVerifyResponse(
    val newPassword: String
)

data class ApiMessage(
    val message: String
)

data class CheckCurrentPasswordRequest(
    val currentPassword: String
)

data class CheckCurrentPasswordResponse(
    val valid: Boolean
)

data class ChangePasswordRequest(
    val newPassword: String,
    val confirmNewPassword: String
)

data class ApiErrorResponse(
    val message: String,
    val status: Int?,
    val error: String?
)