package com.example.lingo.data.remote

import android.content.Context
import android.util.Log
import com.example.lingo.data.model.ReissueRequest
import com.example.lingo.data.model.ReissueResponse
import com.example.lingo.util.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthInterceptor(
    private val appContext: Context,
    private val baseUrl: String = "http://54.215.109.237:8081/"
) : Interceptor {

    private val reissueLock = Any()

    private interface ReissueCallApi {
        @retrofit2.http.POST("/member/reissue")
        fun reissue(@retrofit2.http.Body req: ReissueRequest): retrofit2.Call<ReissueResponse>
    }

    private val reissueApi: ReissueCallApi by lazy {
        val httpLog = okhttp3.logging.HttpLoggingInterceptor { msg ->
            Log.d("ReissueHTTP", msg)
        }.apply { level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY }

        val wireTap = Interceptor { chain ->
            val req = chain.request()
            Log.d("AUTH_WIRE", ">> ${req.method} ${req.url} Authorization=${req.header("Authorization")}")
            chain.proceed(req)
        }

        OkHttpClient.Builder()
            .addNetworkInterceptor(wireTap)
            .addInterceptor(httpLog)
            .build()
            .let { client ->
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ReissueCallApi::class.java)
            }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val tokenManager = TokenManager.get(appContext)
        val originalReq = chain.request()
        val path = originalReq.url.encodedPath

        val alreadyRetried = originalReq.header("X-Reauth-Attempt") == "1"

        val skipAuth = path.startsWith("/member/login")
                || path.startsWith("/member/signup")
                || path.startsWith("/member/reissue")

        // 재발급 금지 엔드포인트(의도적으로 refresh-flow 안 태움)
        val forbidReauthPaths = listOf("/member/logout", "/member/withdraw")
        val forbidReauth = forbidReauthPaths.any { path.startsWith(it) }

        // refresh 헤더 동시 전송이 필요한 엔드포인트 목록
        val needsRefreshHeaderPaths = listOf(
            "/member/logout",
            "/member/change-password/check-current"
        )
        val needsRefreshHeader = needsRefreshHeaderPaths.any { path.startsWith(it) }

        val accessBefore = tokenManager.getAccessToken()
        val refresh = tokenManager.getRefreshToken()

        Log.d(
            "AuthInterceptor",
            "path=$path, skipAuth=$skipAuth, forbidReauth=$forbidReauth, alreadyRetried=$alreadyRetried, " +
                    "hasAccess=${!accessBefore.isNullOrBlank()}, hasRefresh=${!refresh.isNullOrBlank()}"
        )

        // 1) 헤더 구성
        val b = originalReq.newBuilder().removeHeader("Authorization")
        if (!skipAuth && !accessBefore.isNullOrBlank()) {
            b.header("Authorization", "Bearer $accessBefore")                 // ✅ access 붙임
        }

        // 특정 엔드포인트엔 Refresh-Token도 함께 전송 (스웨거와 동일하게 맞춤)
        if (!skipAuth && needsRefreshHeader && !refresh.isNullOrBlank()) {
            b.header("Refresh-Token", "Bearer $refresh")                      // ✅ 가장 흔한 스펙
            // 혹시 백엔드가 다른 이름을 쓰면 아래 대안 중 하나로 교체
            // b.header("Authorization-Refresh", "Bearer $refresh")
            // b.header("X-Refresh-Token", "Bearer $refresh")
        }

        val authedReq = b.build()
        Log.d("AuthInterceptor", "Auth hdr? ${authedReq.header("Authorization")?.startsWith("Bearer ") == true}, " +
                "Refresh hdr? ${authedReq.header("Refresh-Token")?.startsWith("Bearer ") == true}")

        // 2) 1차 요청
        val firstRes = chain.proceed(authedReq)
        Log.d("AuthInterceptor", "firstRes.code=${firstRes.code}")
        if (firstRes.code == 401) {
            Log.d("AuthInterceptor", "401 body peek=${firstRes.peekBody(2048).string()}")
        }

        // 3) 재발급 시도 제외 조건
        if (firstRes.code != 401 || alreadyRetried || skipAuth || forbidReauth) {
            if (firstRes.code == 401 && forbidReauth) {
                tokenManager.clear()
            }
            return firstRes
        }

        // 4) 401 → 재발급 (동시성 보호)
        val refreshed = synchronized(reissueLock) {
            val latestAccess = tokenManager.getAccessToken()
            if (!latestAccess.isNullOrBlank() &&
                authedReq.header("Authorization") != "Bearer $latestAccess"
            ) {
                true
            } else {
                if (refresh.isNullOrBlank()) {
                    false
                } else {
                    try {
                        val call = reissueApi.reissue(ReissueRequest(refreshToken = refresh))
                        val reissueRes = call.execute()
                        Log.d("AuthInterceptor", "reissue code=${reissueRes.code()}")

                        if (reissueRes.isSuccessful && reissueRes.body() != null) {
                            val jwt = reissueRes.body()!!.jwtToken
                            tokenManager.saveTokens(
                                jwt.grantType,
                                jwt.accessToken,
                                jwt.refreshToken
                            )
                            Log.d("AuthInterceptor", "Reissue OK. newAccess=${jwt.accessToken.take(12)}...")
                            true
                        } else {
                            Log.e("AuthInterceptor", "Reissue failed code=${reissueRes.code()} body=${reissueRes.errorBody()?.string()}")
                            tokenManager.clear()
                            false
                        }
                    } catch (e: Exception) {
                        Log.e("AuthInterceptor", "Reissue exception=${e.message}")
                        tokenManager.clear()
                        false
                    }
                }
            }
        }

        return if (refreshed) {
            firstRes.close()
            val newAccess = tokenManager.getAccessToken()
            if (newAccess.isNullOrBlank()) return firstRes

            val retryB = authedReq.newBuilder()
                .removeHeader("Authorization")
                .header("Authorization", "Bearer $newAccess")
                .header("X-Reauth-Attempt", "1")

            // 재시도에도 refresh 헤더 유지(필요 엔드포인트면)
            if (needsRefreshHeader && !refresh.isNullOrBlank()) {
                retryB.header("Refresh-Token", "Bearer $refresh")
            }

            val retriedReq = retryB.build()
            Log.d("AuthInterceptor", "Retry with new access set? ${retriedReq.header("Authorization")?.startsWith("Bearer ") == true}")
            chain.proceed(retriedReq)
        } else {
            firstRes
        }
    }
}