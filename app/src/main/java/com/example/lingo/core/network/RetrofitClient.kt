package com.example.lingo.core.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 빌드 타입/환경별 분리를 원하면 BuildConfig.BASE_URL로 빼세요.
    private const val BASE_URL = "http://54.215.109.237:8081/"

    private val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }

    private val logging = HttpLoggingInterceptor().apply {
        // 배포에는 Level.BASIC ~ NONE 권장
        level = HttpLoggingInterceptor.Level.BODY
    }

    // 공통 헤더(선택)
    private val commonHeaderInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "LingoApp/1.0 (Android)")
            .build()
        chain.proceed(req)
    }

    private fun baseOkHttpBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .addInterceptor(commonHeaderInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES) // 파일/문서 처리
            .writeTimeout(10, TimeUnit.MINUTES) // 업로드
            .callTimeout(10, TimeUnit.MINUTES) // 전체 호출 상한
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
    // 인증 불필요
    val api: ApiService by lazy {
        val client = baseOkHttpBuilder().build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    // 인증 필요할 때 쓰는 버전 (토큰 쿠키/헤더 부착)
    fun apiWithAuth(context: Context): ApiService {
        val client = baseOkHttpBuilder()
            .addInterceptor(AuthInterceptor(context))
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
