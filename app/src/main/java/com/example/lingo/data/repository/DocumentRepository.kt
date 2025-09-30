package com.example.lingo.data.repository

import com.example.lingo.data.model.GenerateDocRequest
import com.example.lingo.data.model.GenerateDocResponse
import com.example.lingo.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class DocumentRepository(
    private val api: ApiService
) {
    /**
     * 문서 생성 (멱등성은 서버 정책에 따름)
     * - 실패 시 errorBody 문자열을 그대로 message에 담아 돌려줌 (중복 감지에 활용)
     */
    suspend fun generateDoc(
        rawDocumentId: Long,
        editedJson: com.google.gson.JsonObject
    ): CallResult<GenerateDocResponse> = withContext(Dispatchers.IO) {
        safeCall {
            api.generateDoc(GenerateDocRequest(rawDocumentId, editedJson))
        }
    }

    /**
     * 이미 생성된 번역문서 조회 (중복 에러 발생 시 우선 호출해 기존 결과를 열기 위함)
     * - 응답 모델은 GenerateDocResponse와 동일 필드라고 가정
     *
     * ApiService 예시:
     * @GET("/translated-documents/{rawDocumentId}")
     * suspend fun getTranslatedDocInfo(@Path("rawDocumentId") rawDocumentId: Long): Response<GenerateDocResponse>
     */
    suspend fun getTranslatedDocInfo(
        rawDocumentId: Long
    ): CallResult<GenerateDocResponse> = withContext(Dispatchers.IO) {
        safeCall {
            api.getTranslatedDocInfo(rawDocumentId)
        }
    }

    // ---------------------------
    // 내부 유틸
    // ---------------------------

    private inline fun <T> buildResult(
        res: Response<T>
    ): CallResult<T> {
        return if (res.isSuccessful) {
            val body = res.body()
            if (body != null) {
                CallResult.Success(body)
            } else {
                CallResult.Error(code = res.code(), message = "Empty body")
            }
        } else {
            // 서버 원문 에러 바디를 그대로 메시지에 담아 중복 감지 등에 활용
            val raw = try { res.errorBody()?.string() } catch (_: Exception) { null }
            CallResult.Error(code = res.code(), message = raw ?: "Request failed (HTTP ${res.code()})")
        }
    }

    private suspend inline fun <T> safeCall(
        crossinline block: suspend () -> Response<T>
    ): CallResult<T> {
        return try {
            val res = block()
            buildResult(res)
        } catch (e: IOException) {
            CallResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: HttpException) {
            // HttpException에도 에러 바디가 들어있을 수 있으니 최대한 노출
            val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            CallResult.Error(code = e.code(), message = raw ?: "HTTP error: ${e.message}", cause = e)
        } catch (e: Exception) {
            CallResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }
}
