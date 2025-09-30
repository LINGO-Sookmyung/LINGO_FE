package com.example.lingo.ui.main.mypage

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.ui.base.BaseActivity
import com.example.lingo.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.example.lingo.data.remote.ApiService

// data.model 패키지의 DTO를 사용
import com.example.lingo.data.model.ChangePasswordRequest
import com.example.lingo.data.model.ApiMessage

class ChangePasswordNewActivity : BaseActivity() {

    private lateinit var boxNewPw: View
    private lateinit var boxConfirmPw: View
    private lateinit var editNewPw: EditText
    private lateinit var editNewPwConfirm: EditText
    private lateinit var ivToggleNewPw: ImageView
    private lateinit var ivToggleConfirmPw: ImageView
    private lateinit var tvConfirmMessage: TextView
    private lateinit var btnDone: LinearLayout

    private var showNew = false
    private var showConfirm = false

    // --- Retrofit(ApiService) : 토큰 자동첨부 + 로깅 ---
    private val api: ApiService by lazy { buildApiService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password_new)
        setAppBar(title = "비밀번호 변경", showBack = true)

        bindViews()
        setupPasswordToggles()
        setupFocusBorders()
        setupRealtimeValidation()
        setupSubmit()
    }

    private fun bindViews() {
        boxNewPw          = findViewById(R.id.boxNewPw)
        boxConfirmPw      = findViewById(R.id.boxConfirmPw)
        editNewPw         = findViewById(R.id.editNewPw)
        editNewPwConfirm  = findViewById(R.id.editNewPwConfirm)
        ivToggleNewPw     = findViewById(R.id.ivToggleNewPw)
        ivToggleConfirmPw = findViewById(R.id.ivToggleConfirmPw)
        tvConfirmMessage  = findViewById(R.id.tvConfirmMessage)
        btnDone           = findViewById(R.id.btnNext)
    }

    private fun setupPasswordToggles() {
        ivToggleNewPw.setOnClickListener {
            showNew = !showNew
            editNewPw.transformationMethod = if (showNew)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            ivToggleNewPw.setImageResource(if (showNew) R.drawable.ic_eye_off else R.drawable.ic_eye)
            editNewPw.setSelection(editNewPw.text?.length ?: 0)
        }
        ivToggleConfirmPw.setOnClickListener {
            showConfirm = !showConfirm
            editNewPwConfirm.transformationMethod = if (showConfirm)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            ivToggleConfirmPw.setImageResource(if (showConfirm) R.drawable.ic_eye_off else R.drawable.ic_eye)
            editNewPwConfirm.setSelection(editNewPwConfirm.text?.length ?: 0)
        }
    }

    private fun setupFocusBorders() {
        editNewPw.setOnFocusChangeListener { _, hasFocus ->
            boxNewPw.setBackgroundResource(
                if (hasFocus) R.drawable.outlined_button_selected else R.drawable.outlined_button_white
            )
        }
        editNewPwConfirm.setOnFocusChangeListener { _, hasFocus ->
            boxConfirmPw.setBackgroundResource(
                when {
                    tvConfirmMessage.visibility == View.VISIBLE -> R.drawable.outlined_button_error
                    hasFocus -> R.drawable.outlined_button_selected
                    else -> R.drawable.outlined_button_white
                }
            )
        }
    }

    private fun setupRealtimeValidation() {
        val recalc: () -> Unit = {
            val p1 = editNewPw.text?.toString().orEmpty()
            val p2 = editNewPwConfirm.text?.toString().orEmpty()

            val bothFilled = p1.isNotEmpty() && p2.isNotEmpty()
            val matched = bothFilled && (p1 == p2)

            if (!bothFilled) {
                tvConfirmMessage.visibility = View.GONE
                if (!editNewPwConfirm.hasFocus()) {
                    boxConfirmPw.setBackgroundResource(R.drawable.outlined_button_white)
                }
            } else if (!matched) {
                tvConfirmMessage.text = "비밀번호가 일치하지 않습니다."
                tvConfirmMessage.visibility = View.VISIBLE
                boxConfirmPw.setBackgroundResource(R.drawable.outlined_button_error)
            } else {
                tvConfirmMessage.visibility = View.GONE
                boxConfirmPw.setBackgroundResource(
                    if (editNewPwConfirm.hasFocus()) R.drawable.outlined_button_selected
                    else R.drawable.outlined_button_white
                )
            }

            val enable = matched
            btnDone.isEnabled = enable
            btnDone.setBackgroundResource(
                if (enable) R.drawable.outlined_button_blue else R.drawable.outlined_button_gray
            )
        }

        editNewPw.doOnTextChanged { _, _, _, _ -> recalc() }
        editNewPwConfirm.doOnTextChanged { _, _, _, _ -> recalc() }
        recalc()
    }

    private fun setupSubmit() {
        btnDone.setOnClickListener {
            val p1 = editNewPw.text?.toString().orEmpty()
            val p2 = editNewPwConfirm.text?.toString().orEmpty()
            if (p1.isBlank() || p2.isBlank()) return@setOnClickListener
            if (p1 != p2) {
                tvConfirmMessage.text = "비밀번호가 일치하지 않습니다."
                tvConfirmMessage.visibility = View.VISIBLE
                boxConfirmPw.setBackgroundResource(R.drawable.outlined_button_error)
                return@setOnClickListener
            }

            btnDone.isEnabled = false
            lifecycleScope.launch {
                val (ok, msg) = withContext(Dispatchers.IO) { callChangePassword(p1, p2) }
                btnDone.isEnabled = true

                if (ok) {
                    Toast.makeText(this@ChangePasswordNewActivity, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                    goToMyPage()
                } else {
                    val display = msg ?: "비밀번호 변경에 실패했습니다. 잠시 후 다시 시도해 주세요."
                    tvConfirmMessage.text = display
                    tvConfirmMessage.visibility = View.VISIBLE
                    boxConfirmPw.setBackgroundResource(R.drawable.outlined_button_error)
                }
            }
        }
    }

    private suspend fun callChangePassword(p1: String, p2: String): Pair<Boolean, String?> {
        return try {
            val res: Response<ApiMessage> = api.changePassword(ChangePasswordRequest(p1, p2))
            if (res.isSuccessful) {
                true to (res.body()?.message ?: "OK")
            } else {
                false to parseErrorMessage(res.errorBody())
            }
        } catch (e: Exception) {
            false to e.message
        }
    }

    private fun parseErrorMessage(errBody: ResponseBody?): String? {
        if (errBody == null) return null
        return try {
            val text = errBody.string()
            Gson().fromJson(text, ApiMessage::class.java)?.message ?: text
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun goToMyPage() {
        startActivity(
            Intent(this, MyPageActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    // --- Retrofit + OkHttp 생성부(토큰 자동 첨부) ---
    private fun buildApiService(): ApiService {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = TokenManager.get(applicationContext).getAccessToken()
            val builder = original.newBuilder()
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Accept", "application/json;charset=UTF-8")

            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(log)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://54.215.109.237:8081/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
