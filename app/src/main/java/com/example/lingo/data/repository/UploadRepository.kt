package com.example.lingo.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.lingo.data.model.PresignedDownloadResponse
import com.example.lingo.data.model.PresignedItem
import com.example.lingo.data.model.PresignedUploadResponse
import com.example.lingo.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.source
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class UploadResult<out T> {
    data class Success<T>(val data: T) : UploadResult<T>()
    data class Failure(val code: Int?, val message: String?, val body: String? = null) : UploadResult<Nothing>()
}

class UploadRepository(
    private val api: ApiService
) {
    // 10분(600초) 타임아웃의 전용 OkHttpClient (Presigned 업/다운로드용)
    private val longTimeoutClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    // --- Presigned URL 발급 ---
    suspend fun getUploadUrls(fileNames: List<String>): UploadResult<List<PresignedItem>> =
        withContext(Dispatchers.IO) {
            try {
                val res = api.createUploadUrls(fileNames)
                if (res.isSuccessful) {
                    val body: PresignedUploadResponse? = res.body()
                    if (body != null && body.success) {
                        UploadResult.Success(body.data)
                    } else {
                        UploadResult.Failure(res.code(), "Invalid response or success=false")
                    }
                } else {
                    UploadResult.Failure(res.code(), "Request failed", res.errorBody()?.string())
                }
            } catch (e: IOException) {
                UploadResult.Failure(null, "Network error: ${e.message}")
            } catch (e: HttpException) {
                UploadResult.Failure(e.code(), "HTTP error: ${e.message}")
            } catch (e: Exception) {
                UploadResult.Failure(null, "Unexpected error: ${e.message}")
            }
        }

    suspend fun getDownloadUrl(filename: String): UploadResult<PresignedDownloadResponse> =
        withContext(Dispatchers.IO) {
            try {
                val res = api.createDownloadUrl(filename)
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null) UploadResult.Success(body)
                    else UploadResult.Failure(res.code(), "Empty body")
                } else {
                    UploadResult.Failure(res.code(), "Request failed", res.errorBody()?.string())
                }
            } catch (e: IOException) {
                UploadResult.Failure(null, "Network error: ${e.message}")
            } catch (e: HttpException) {
                UploadResult.Failure(e.code(), "HTTP error: ${e.message}")
            } catch (e: Exception) {
                UploadResult.Failure(null, "Unexpected error: ${e.message}")
            }
        }

    // --- Presigned URL 로 실제 업로드 (PUT, 스트리밍) ---
    suspend fun putToPresignedUrl(
        context: Context,
        fileUri: Uri,
        presignedUrl: String,
        explicitMime: String? = null
    ): UploadResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val resolver: ContentResolver = context.contentResolver

            // MIME 강제 (기본 jpg). presigned가 png/pdf 등일 땐 explicitMime으로 넘겨줘.
            val mime = explicitMime ?: "image/jpeg"
            val mediaType = mime.toMediaTypeOrNull()

            // 파일 길이 추정 (가능하면 Content-Length 제공)
            val afd = resolver.openAssetFileDescriptor(fileUri, "r")
            val contentLength = afd?.length ?: -1L
            afd?.close()

            // 스트리밍 RequestBody (메모리 전체 로딩 방지)
            val body = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength(): Long = if (contentLength > 0) contentLength else -1L
                override fun writeTo(sink: okio.BufferedSink) {
                    resolver.openInputStream(fileUri)?.use { ins ->
                        sink.writeAll(ins.source())
                    } ?: throw IOException("Failed to open InputStream for $fileUri")
                }
            }

            android.util.Log.d(
                "UPLOAD",
                "PUT -> url(len)=${presignedUrl.length}, mime=$mime, contentLength=$contentLength"
            )

            // Presigned 요청: 불필요한 헤더(Authorization 등) 금지
            val req = Request.Builder()
                .url(presignedUrl)
                .put(body)
                .header("Content-Type", mime) // S3는 명시 권장
                // .header("x-amz-acl", "bucket-owner-full-control") // 버킷 정책에 따라 필요 시 사용
                .build()

            longTimeoutClient.newCall(req).execute().use { resp ->
                val ok = resp.isSuccessful
                val code = resp.code
                if (ok) {
                    android.util.Log.d("UPLOAD", "PUT <- success code=$code")
                    UploadResult.Success(Unit)
                } else {
                    val err = resp.body?.string()
                    android.util.Log.e("UPLOAD", "PUT <- fail code=$code body=${err?.take(300)}")
                    UploadResult.Failure(code, "S3 upload failed", err)
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure(null, "Network error: ${e.message}")
        } catch (e: Exception) {
            UploadResult.Failure(null, "Unexpected error: ${e.message}")
        }
    }

    @Suppress("unused")
    private fun guessMimeFromUri(uri: Uri): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return if (!ext.isNullOrBlank())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        else null
    }
}
