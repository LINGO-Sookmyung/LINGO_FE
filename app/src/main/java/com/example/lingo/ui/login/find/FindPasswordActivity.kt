/*
package com.example.lingo.ui.find

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.ui.base.BaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FindPasswordActivity : BaseActivity() {

    private lateinit var root: View
    private lateinit var editName: EditText
    private lateinit var editEmail: EditText
    private lateinit var tvEmailMessage: TextView
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_password)
        setAppBar(title = "비밀번호 찾기", showBack = true)

        root = findViewById(R.id.rootFindEmail)
        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        tvEmailMessage = findViewById(R.id.tvEmailMessage)
        btnNext = findViewById(R.id.btnNext)

        editEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        // 이름은 기본 포커스 하이라이트만
        setupFocusHighlight(editName)

        // ---- 헬퍼들 ----
        fun applyEmailUiState() {
            val email = editEmail.text.toString().trim()
            val invalid = email.isNotEmpty() && !isEmailValid(email)

            when {
                invalid -> {
                    tvEmailMessage.visibility = View.VISIBLE
                    tvEmailMessage.text = "이메일 형식이 잘못되었습니다."
                    // ★ 에러가 최우선: 포커스여도 빨간 테두리 고정
                    editEmail.setBackgroundResource(R.drawable.outlined_button_error)
                }
                email.isEmpty() -> {
                    tvEmailMessage.visibility = View.GONE
                    // 비어있으면 포커스면 파랑, 아니면 기본
                    editEmail.setBackgroundResource(
                        if (editEmail.hasFocus()) R.drawable.outlined_button_selected
                        else R.drawable.outlined_button
                    )
                }
                else -> {
                    tvEmailMessage.visibility = View.GONE
                    // 유효 + 포커스면 파랑, 아니면 기본
                    editEmail.setBackgroundResource(
                        if (editEmail.hasFocus()) R.drawable.outlined_button_selected
                        else R.drawable.outlined_button
                    )
                }
            }
        }

        fun recalcButton() {
            val nameOk = editName.text.toString().isNotBlank()
            val emailOk = isEmailValid(editEmail.text.toString().trim())
            val enable = nameOk && emailOk
            btnNext.isEnabled = enable
            btnNext.setBackgroundResource(
                if (enable) R.drawable.outlined_button_blue else R.drawable.outlined_button_gray
            )
        }
        // ---- 헬퍼 끝 ----

        // 이메일: 포커스 변화/입력 변화 시 상태 재적용
        editEmail.onFocusChangeListener = View.OnFocusChangeListener { _, _ ->
            applyEmailUiState()
        }
        editEmail.doOnTextChanged { _, _, _, _ ->
            applyEmailUiState()
            recalcButton()
        }

        // 이름 입력 변화 시 버튼만 재계산
        editName.doOnTextChanged { _, _, _, _ -> recalcButton() }

        // 초기 1회 적용
        applyEmailUiState()
        recalcButton()

        // 바깥 탭: 에러면 빨강 유지, 아니면 기본/포커스 없는 상태로
        root.isClickable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val invalid = email.isNotEmpty() && !isEmailValid(email)
            if (invalid) {
                editEmail.setBackgroundResource(R.drawable.outlined_button_error)
            } else {
                editEmail.setBackgroundResource(R.drawable.outlined_button)
            }
            editName.setBackgroundResource(R.drawable.outlined_button)
            currentFocus?.clearFocus()
        }

        // 다음 버튼(테스트 모드)
        btnNext.setOnClickListener {
            val name = editName.text.toString().trim()
            val email = editEmail.text.toString().trim()

            btnNext.isEnabled = false
            lifecycleScope.launch {
                // 실제 API 대신 더미 테스트
                delay(1000)

                if (name == "min" && email == "minji548900@naver.com") {
                    Toast.makeText(
                        this@FindPasswordActivity,
                        "인증 코드가 이메일로 전송되었습니다. (테스트 모드)",
                        Toast.LENGTH_SHORT
                    ).show()
                    startActivity(
                        Intent(this@FindPasswordActivity, FindPasswordCodeActivity::class.java)
                            .putExtra(FindPasswordCodeActivity.EXTRA_NAME, name)
                            .putExtra(FindPasswordCodeActivity.EXTRA_EMAIL, email)
                    )
                } else {
                    Toast.makeText(
                        this@FindPasswordActivity,
                        "등록되지 않은 사용자입니다. (테스트 모드)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                btnNext.isEnabled = true
            }
        }
    }

    private fun isEmailValid(email: String): Boolean =
        email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun setupFocusHighlight(
        edit: EditText,
        errorChecker: (() -> Boolean)? = null
    ) {
        edit.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val hasError = errorChecker?.invoke() == true
            v.setBackgroundResource(
                when {
                    hasError -> R.drawable.outlined_button_error
                    hasFocus -> R.drawable.outlined_button_selected
                    else     -> R.drawable.outlined_button
                }
            )
        }
    }
}
*/

package com.example.lingo.ui.login.find

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.core.model.ApiError
import com.example.lingo.ui.base.BaseActivity
import com.example.lingo.core.network.ApiService
import com.example.lingo.data.model.auth.ResetPasswordSendRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class FindPasswordActivity : BaseActivity() {

    private lateinit var root: View
    private lateinit var editName: EditText
    private lateinit var editEmail: EditText
    private lateinit var tvEmailMessage: TextView
    private lateinit var btnNext: Button

    // ★ 추가: Retrofit + ApiService + Gson
    private val gson = Gson()
    private val api: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl("http://54.215.109.237:8081/")   // 베이스 URL
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_password)
        setAppBar(title = "비밀번호 찾기", showBack = true)

        root = findViewById(R.id.rootFindEmail)
        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        tvEmailMessage = findViewById(R.id.tvEmailMessage)
        btnNext = findViewById(R.id.btnNext)

        editEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        setupFocusHighlight(editName)
        setupFocusHighlight(editEmail) {
            val t = editEmail.text.toString().trim()
            t.isNotEmpty() && !isEmailValid(t)
        }

        val recalc: () -> Unit = {
            val nameOk = editName.text.toString().isNotBlank()
            val email = editEmail.text.toString().trim()
            val emailOk = isEmailValid(email)

            when {
                email.isEmpty() -> {
                    tvEmailMessage.visibility = View.GONE
                    if (!editEmail.hasFocus()) editEmail.setBackgroundResource(R.drawable.outlined_button_white)
                }
                !emailOk -> {
                    tvEmailMessage.visibility = View.VISIBLE
                    tvEmailMessage.text = "이메일 형식이 잘못되었습니다."
                    if (!editEmail.hasFocus()) editEmail.setBackgroundResource(R.drawable.outlined_button_error)
                }
                else -> {
                    tvEmailMessage.visibility = View.GONE
                    if (!editEmail.hasFocus()) editEmail.setBackgroundResource(R.drawable.outlined_button_white)
                }
            }

            val enable = nameOk && emailOk
            btnNext.isEnabled = enable
            btnNext.setBackgroundResource(
                if (enable) R.drawable.outlined_button_blue else R.drawable.outlined_button_gray
            )
        }
        editName.doOnTextChanged { _, _, _, _ -> recalc() }
        editEmail.doOnTextChanged { _, _, _, _ -> recalc() }
        recalc()

        root.isClickable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener {
            editName.setBackgroundResource(R.drawable.outlined_button_white)
            val email = editEmail.text.toString().trim()
            if (email.isEmpty() || isEmailValid(email)) {
                editEmail.setBackgroundResource(R.drawable.outlined_button_white)
            } else {
                editEmail.setBackgroundResource(R.drawable.outlined_button_error)
            }
            currentFocus?.clearFocus()
        }

        btnNext.setOnClickListener {
            val name = editName.text.toString().trim()
            val email = editEmail.text.toString().trim()

            btnNext.isEnabled = false
            lifecycleScope.launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        api.sendResetPassword(ResetPasswordSendRequest(name, email))
                    }
                    if (res.isSuccessful) {
                        Toast.makeText(
                            this@FindPasswordActivity,
                            res.body()?.message ?: "인증 코드가 이메일로 전송되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(
                            Intent(this@FindPasswordActivity, FindPasswordCodeActivity::class.java)
                                .putExtra(FindPasswordCodeActivity.EXTRA_NAME, name)
                                .putExtra(FindPasswordCodeActivity.EXTRA_EMAIL, email)
                        )
                    } else {
                        // ★ raw 대신 Gson으로 에러 파싱
                        val apiErr = try {
                            gson.fromJson(res.errorBody()?.charStream(), ApiError::class.java)
                        } catch (_: Exception) { null }
                        val msg = apiErr?.message ?: "요청을 처리할 수 없습니다. (code=${res.code()})"
                        Toast.makeText(this@FindPasswordActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    Toast.makeText(this@FindPasswordActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                } catch (e: HttpException) {
                    Toast.makeText(this@FindPasswordActivity, "서버 오류가 발생했습니다. (${e.code()})", Toast.LENGTH_SHORT).show()
                } finally {
                    btnNext.isEnabled = true
                }
            }
        }
    }

    private fun isEmailValid(email: String): Boolean =
        email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun setupFocusHighlight(
        edit: EditText,
        errorChecker: (() -> Boolean)? = null
    ) {
        edit.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val hasError = errorChecker?.invoke() == true
            v.setBackgroundResource(
                when {
                    hasError -> R.drawable.outlined_button_error
                    hasFocus -> R.drawable.outlined_button_selected
                    else     -> R.drawable.outlined_button_white
                }
            )
        }
    }
}