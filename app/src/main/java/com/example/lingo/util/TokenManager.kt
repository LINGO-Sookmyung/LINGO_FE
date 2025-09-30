package com.example.lingo.util

import android.content.Context

class TokenManager private constructor(private val ctx: Context) {

    private val prefs by lazy { ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    fun getAccessToken(): String? = prefs.getString("jwt_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)

    fun saveTokens(grantType: String?, access: String?, refresh: String?) {
        prefs.edit()
            .apply {
                if (!access.isNullOrBlank()) putString("jwt_token", access)
                if (!refresh.isNullOrBlank()) putString("refresh_token", refresh)
                if (!access.isNullOrBlank()) putBoolean("is_logged_in", true)
            }
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove("jwt_token")
            .remove("refresh_token")
            .putBoolean("is_logged_in", false)
            .apply()
    }

    companion object {
        @Volatile private var INSTANCE: TokenManager? = null
        fun get(context: Context): TokenManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}