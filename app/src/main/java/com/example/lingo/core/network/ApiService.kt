package com.example.lingo.core.network

import com.example.lingo.data.model.auth.ApiMessage
import com.example.lingo.data.model.auth.ChangePasswordRequest
import com.example.lingo.data.model.auth.CheckCurrentPasswordRequest
import com.example.lingo.data.model.auth.CheckCurrentPasswordResponse
import com.example.lingo.data.model.auth.FindEmailRequest
import com.example.lingo.data.model.auth.FindEmailResponse
import com.example.lingo.data.model.auth.LoginRequest
import com.example.lingo.data.model.auth.LoginResponse
import com.example.lingo.data.model.auth.ReissueRequest
import com.example.lingo.data.model.auth.ReissueResponse
import com.example.lingo.data.model.auth.ResetPasswordSendRequest
import com.example.lingo.data.model.auth.ResetPasswordVerifyRequest
import com.example.lingo.data.model.auth.ResetPasswordVerifyResponse
import com.example.lingo.data.model.auth.SignupCheckEmailRequest
import com.example.lingo.data.model.auth.SignupCheckEmailResponse
import com.example.lingo.data.model.auth.SignupRequest
import com.example.lingo.data.model.auth.SignupResponse
import com.example.lingo.data.model.auth.SimpleMessageResponse
import com.example.lingo.data.model.document.BaseEnvelope
import com.example.lingo.data.model.document.GenerateDocRequest
import com.example.lingo.data.model.document.GenerateDocResponse
import com.example.lingo.data.model.document.RegisterOriginalReq
import com.example.lingo.data.model.document.TranslatedDocumentDto
import com.example.lingo.data.model.storage.PresignedDownloadResponse
import com.example.lingo.data.model.storage.PresignedSingleRes
import com.example.lingo.data.model.storage.PresignedUploadResponse
import com.example.lingo.ui.main.translation.TranslatePreviewRequest
import com.example.lingo.ui.main.translation.TranslatePreviewResponse
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("/member/signup")
    suspend fun signup(@Body body: SignupRequest): Response<SignupResponse>

    @POST("/member/check-email")
    suspend fun checkEmail(@Body body: SignupCheckEmailRequest): Response<SignupCheckEmailResponse>

    @POST("/member/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("/member/find-email")
    suspend fun findEmail(@Body req: FindEmailRequest): Response<FindEmailResponse>

    @POST("member/reset-password/send")
    suspend fun sendResetPassword(@Body body: ResetPasswordSendRequest): Response<SimpleMessageResponse>

    @POST("member/reset-password/verify")
    suspend fun verifyResetPassword(@Body req: ResetPasswordVerifyRequest): Response<ResetPasswordVerifyResponse>

    @POST("/member/reissue")
    suspend fun reissue(@Body req: ReissueRequest): Response<ReissueResponse>

    @POST("/member/logout")
    suspend fun logout(): Response<ApiMessage>

    @POST("/member/change-password/check-current")
    suspend fun checkCurrentPassword(@Body req: CheckCurrentPasswordRequest): Response<CheckCurrentPasswordResponse>

    @POST("/member/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<ApiMessage>

    @GET("/api-docs/translated-documents-list")
    suspend fun getTranslatedDocumentsList(): List<TranslatedDocumentDto>

    @GET("/api-docs/file/presigned/upload")
    suspend fun getUploadPresigned(@Query("filename") filename: String): PresignedSingleRes

    @GET("/api-docs/file/presigned/download")
    suspend fun getDownloadPresigned(@Query("filename") filename: String): PresignedSingleRes

    @POST("/api-docs/documents")
    suspend fun registerOriginalDocument(@Body body: RegisterOriginalReq): BaseEnvelope

    @POST("/api-docs/file/presigned/upload-urls")
    suspend fun createUploadUrls(@Body fileNames: List<String>): Response<PresignedUploadResponse>

    @GET("/api-docs/file/presigned/download")
    suspend fun createDownloadUrl(@Query("filename") filename: String): Response<PresignedDownloadResponse>

    @POST("/api/register-raw-documents/generate-doc")
    suspend fun generateDoc(@Body body: GenerateDocRequest): Response<GenerateDocResponse>

    @POST("/api/register-raw-documents/translate-preview")
    suspend fun translatePreview(@Body body: TranslatePreviewRequest): Response<TranslatePreviewResponse>

    @GET("/translated-documents/{rawDocumentId}")
    suspend fun getTranslatedDocInfo(@Path("rawDocumentId") rawDocumentId: Long): Response<GenerateDocResponse>
}
