package com.example.lingo.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.core.di.ServiceLocator

import com.example.lingo.core.model.ApiError
import com.example.lingo.data.model.auth.LoginRequest
import com.example.lingo.data.model.auth.LoginResponse as LResponse
import com.example.lingo.data.model.auth.JwtToken as JToken

import com.example.lingo.core.network.ApiService
import com.example.lingo.ui.base.BaseActivity
import com.example.lingo.ui.login.find.FindEmailActivity
import com.example.lingo.ui.login.find.FindPasswordActivity
import com.example.lingo.ui.main.MainActivity
import com.example.lingo.ui.start.SignupActivity
import com.example.lingo.data.local.TokenManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class LoginActivity : BaseActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var btnLogin: Button
    private lateinit var tvFindEmail: TextView
    private lateinit var tvFindPassword: TextView
    private lateinit var tvSignUp: TextView
    private lateinit var tvLoginError: TextView

    private var isPasswordVisible = false
    private val gson = Gson()

    // 전역 ServiceLocator 사용 (AuthInterceptor 포함)
    private val api: ApiService by lazy {
        ServiceLocator.provideApi(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setAppBar(title = "로그인", showBack = true)

        bindViews()
        setupUiInteractions()
    }

    private fun bindViews() {
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvFindEmail = findViewById(R.id.tvFindEmail)
        tvFindPassword = findViewById(R.id.tvFindPassword)
        tvSignUp = findViewById(R.id.tvSignUp)
        tvLoginError = findViewById(R.id.tvLoginError)
    }

    private fun setupUiInteractions() {
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(isPasswordVisible)
        }

        editEmail.addTextChangedListener {
            hideLoginError()
            editEmail.setBackgroundResource(
                if (editEmail.hasFocus()) R.drawable.outlined_button_selected
                else R.drawable.outlined_button_white
            )
        }
        editPassword.addTextChangedListener {
            hideLoginError()
            editPassword.setBackgroundResource(
                if (editPassword.hasFocus()) R.drawable.outlined_button_selected
                else R.drawable.outlined_button_white
            )
        }

        fun setupFocusHighlight(edit: EditText) {
            edit.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (tvLoginError.visibility == View.VISIBLE) return@OnFocusChangeListener
                v.setBackgroundResource(
                    if (hasFocus) R.drawable.outlined_button_selected
                    else R.drawable.outlined_button_white
                )
            }
        }
        setupFocusHighlight(editEmail)
        setupFocusHighlight(editPassword)

        editPassword.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                attemptLogin()
                true
            } else false
        }

        btnLogin.setOnClickListener { attemptLogin() }

        tvFindEmail.setOnClickListener { startActivity(Intent(this, FindEmailActivity::class.java)) }
        tvFindPassword.setOnClickListener { startActivity(Intent(this, FindPasswordActivity::class.java)) }
        tvSignUp.setOnClickListener { startActivity(Intent(this, SignupActivity::class.java)) }

        val root = findViewById<View>(R.id.rootLogin)
        root.isClickable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener {
            if (tvLoginError.visibility != View.VISIBLE) {
                editEmail.setBackgroundResource(R.drawable.outlined_button_white)
                editPassword.setBackgroundResource(R.drawable.outlined_button_white)
            }
            currentFocus?.clearFocus()
        }
    }

    private fun attemptLogin() {
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("이메일 주소 또는 비밀번호를 잘못 입력하였습니다.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showLoginError("이메일 형식이 올바르지 않습니다.")
            return
        }

        hideLoginError()
        setLoading(true)

        lifecycleScope.launch {
            try {
                // 제네릭 타입을 FQN + 별칭 모델로 명확히
                val res: retrofit2.Response<LResponse> = withContext(Dispatchers.IO) {
                    api.login(LoginRequest(email, password))
                }

                if (res.isSuccessful) {
                    // 명시 타입으로 고정 (별칭 타입)
                    val body: LResponse? = res.body()
                    val jwt: JToken? = body?.jwtToken
                    val access: String? = jwt?.accessToken
                    val refresh: String? = jwt?.refreshToken
                    val grant: String = jwt?.grantType ?: "Bearer"

                    if (access.isNullOrBlank()) {
                        showLoginError("로그인 응답에 토큰이 없습니다. 잠시 후 다시 시도해 주세요.")
                    } else {
                        TokenManager.get(applicationContext).saveTokens(grant, access, refresh ?: "")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    val apiErr: ApiError? = try {
                        gson.fromJson(res.errorBody()?.charStream(), ApiError::class.java)
                    } catch (_: Exception) { null }

                    val msg = when (res.code()) {
                        400, 401 -> apiErr?.message
                            ?: "이메일 주소 또는 비밀번호를 잘못 입력하였습니다."
                        429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
                        in 500..599 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
                        else -> apiErr?.message ?: "로그인에 실패했습니다. (code=${res.code()})"
                    }
                    showLoginError(msg)
                }
            } catch (_: IOException) {
                showLoginError("네트워크 오류가 발생했습니다.")
            } catch (e: HttpException) {
                showLoginError("서버 오류가 발생했습니다. (${e.code()})")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.alpha = if (loading) 0.6f else 1f
    }

    private fun showLoginError(message: String) {
        tvLoginError.text = message
        tvLoginError.visibility = View.VISIBLE
        editEmail.setBackgroundResource(R.drawable.outlined_button_login_error)
        editPassword.setBackgroundResource(R.drawable.outlined_button_login_error)
    }

    private fun hideLoginError() {
        tvLoginError.visibility = View.GONE
        editEmail.setBackgroundResource(R.drawable.outlined_button_white)
        editPassword.setBackgroundResource(R.drawable.outlined_button_white)
    }

    private fun togglePasswordVisibility(visible: Boolean) {
        if (visible) {
            editPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            ivTogglePassword.setImageResource(R.drawable.ic_eye_off)
        } else {
            editPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            ivTogglePassword.setImageResource(R.drawable.ic_eye)
        }
        editPassword.setSelection(editPassword.text?.length ?: 0)
    }
}
