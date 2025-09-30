package com.example.lingo.ui.login.find

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.ui.base.BaseActivity
import kotlinx.coroutines.launch
import android.content.Intent
import com.example.lingo.core.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import com.example.lingo.data.model.auth.ResetPasswordVerifyRequest

class FindPasswordCodeActivity : BaseActivity() {

    private val api: ApiService by lazy {
        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        retrofit2.Retrofit.Builder()
            .baseUrl("http://54.215.109.237:8081/")
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_NEW_PASSWORD = "extra_new_password"
    }

    private lateinit var root: View
    private lateinit var editCode: EditText
    private lateinit var tvCodeMessage: TextView
    private lateinit var btnVerify: Button

    private val email by lazy { intent.getStringExtra(EXTRA_EMAIL).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_password_code) // <- 당신의 XML id
        setAppBar(title = "비밀번호 찾기", showBack = true)

        root = findViewById(R.id.rootFindCode)
        editCode = findViewById(R.id.editCode)
        tvCodeMessage = findViewById(R.id.tvCodeMessage)
        btnVerify = findViewById(R.id.btnVerify)

        editCode.inputType = InputType.TYPE_CLASS_TEXT // 숫자 + 알파벳 자유롭게 입력 가능
        editCode.filters = arrayOf() // 길이 제한 제거

        // 포커스 테두리
        setupFocusHighlight(editCode) { tvCodeMessage.visibility == View.VISIBLE }

        btnVerify.isEnabled = true
        btnVerify.setBackgroundResource(R.drawable.outlined_button_blue)

        // 바깥 클릭 시 포커스 해제 & 테두리 복원(에러면 유지)
        root.isClickable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener {
            if (tvCodeMessage.visibility == View.VISIBLE) {
                editCode.setBackgroundResource(R.drawable.outlined_button_error)
            } else {
                editCode.setBackgroundResource(R.drawable.outlined_button_white)
            }
            currentFocus?.clearFocus()
        }

        btnVerify.setOnClickListener { verifyCode() }
    }

    private fun verifyCode() {
        val code = editCode.text.toString()

        btnVerify.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.verifyResetPassword(ResetPasswordVerifyRequest(email = email, verificationCode = code))
                }
                if (res.isSuccessful) {
                    val newPw = res.body()?.newPassword.orEmpty()
                    // 결과 화면으로 이동(새 비밀번호 전달)
                    startActivity(
                        Intent(this@FindPasswordCodeActivity, FindPasswordResultActivity::class.java)
                            .putExtra(EXTRA_NEW_PASSWORD, newPw)
                    )
                    finish()
                } else {
                    // 404 → "인증코드가 일치하지 않습니다." 메시지
                    editCode.setBackgroundResource(R.drawable.outlined_button_error)
                    tvCodeMessage.text = "인증코드가 일치하지 않습니다."
                    tvCodeMessage.visibility = View.VISIBLE
                }
            } catch (e: IOException) {
                Toast.makeText(this@FindPasswordCodeActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                Toast.makeText(this@FindPasswordCodeActivity, "서버 오류가 발생했습니다. (${e.code()})", Toast.LENGTH_SHORT).show()
            } finally {
                btnVerify.isEnabled = true
            }
        }
    }

    /** 공통 포커스 하이라이트 */
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
