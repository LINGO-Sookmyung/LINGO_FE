package com.example.lingo.ui.login.find

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.core.network.ApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import com.example.lingo.ui.base.BaseActivity
import androidx.core.widget.doOnTextChanged
import com.example.lingo.core.model.ApiError
import com.example.lingo.data.model.auth.FindEmailRequest

class FindEmailActivity : BaseActivity() {

    private lateinit var editName: EditText
    private lateinit var editPhone: EditText
    private lateinit var tvPhoneMessage: TextView
    private lateinit var btnNext: Button
    private val gson = Gson()

    //
    private val MIN_PHONE_LEN = 9

    private val api: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl("http://54.215.109.237:8081/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_email)
        setAppBar(title = "이메일 찾기", showBack = true)

        editName = findViewById(R.id.editName)
        editPhone = findViewById(R.id.editPhone)
        tvPhoneMessage = findViewById(R.id.tvPhoneMessage)
        btnNext = findViewById(R.id.btnNext)

        // 이름: 기본 포커스 하이라이트
        setupFocusHighlight(editName)

        // ---- 헬퍼들 ----
        fun applyPhoneUiState() {
            val phone = editPhone.text.toString()
            val invalid = phone.isNotEmpty() && !isPhoneValid(phone)

            when {
                invalid -> {
                    tvPhoneMessage.visibility = View.VISIBLE
                    tvPhoneMessage.text = "전화번호 형식이 잘못되었습니다."
                    tvPhoneMessage.setTextColor(Color.parseColor("#FF6666"))
                    // 에러 최우선: 포커스여도 빨강 유지
                    editPhone.setBackgroundResource(R.drawable.outlined_button_error)
                }
                phone.isEmpty() -> {
                    tvPhoneMessage.visibility = View.GONE
                    // 입력 비어있을 땐 포커스면 파랑, 아니면 기본
                    editPhone.setBackgroundResource(
                        if (editPhone.hasFocus()) R.drawable.outlined_button_selected
                        else R.drawable.outlined_button_white
                    )
                }
                else -> {
                    tvPhoneMessage.visibility = View.GONE
                    // 유효 + 포커스면 파랑, 아니면 기본
                    editPhone.setBackgroundResource(
                        if (editPhone.hasFocus()) R.drawable.outlined_button_selected
                        else R.drawable.outlined_button_white
                    )
                }
            }
        }

        fun recalcButton() {
            val ok = editName.text.isNotBlank() && isPhoneValid(editPhone.text.toString())
            btnNext.isEnabled = ok
            btnNext.setBackgroundResource(
                if (ok) R.drawable.outlined_button_blue else R.drawable.outlined_button_gray
            )
        }
        // ---- 헬퍼 끝 ----

        // 전화번호 포커스 변화 시 상태 재적용
        editPhone.onFocusChangeListener = View.OnFocusChangeListener { _, _ ->
            applyPhoneUiState()
        }

        // 입력 변경 시 즉시 반영 (에러 > 포커스 > 기본)
        editPhone.doOnTextChanged { _, _, _, _ ->
            applyPhoneUiState()
            recalcButton()
        }
        editName.doOnTextChanged { _, _, _, _ ->
            recalcButton()
        }

        // 초기 상태 1회 적용
        applyPhoneUiState()
        recalcButton()

        // 다음 버튼
        btnNext.setOnClickListener { requestFindEmail() }
    }

    private fun setupFocusHighlight(edit: EditText, errorChecker: (() -> Boolean)? = null) {
        edit.onFocusChangeListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
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

    private fun isPhoneValid(phone: String): Boolean =
        phone.isNotEmpty() && phone.all { it.isDigit() } && phone.length >= MIN_PHONE_LEN

    private fun requestFindEmail() {
        val name = editName.text.toString().trim()
        val phone = editPhone.text.toString().trim()
        if (name.isEmpty() || !isPhoneValid(phone)) return

        btnNext.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.findEmail(FindEmailRequest(name = name, phoneNum = phone))
                }
                if (res.isSuccessful) {
                    val email = res.body()?.email.orEmpty()
                    startActivity(
                        Intent(this@FindEmailActivity, FindEmailResultActivity::class.java)
                            .putExtra(FindEmailResultActivity.EXTRA_FOUND_EMAIL, email)
                    )
                } else {
                    val apiErr = try { gson.fromJson(res.errorBody()?.charStream(), ApiError::class.java) } catch (_: Exception) { null }
                    val msg = apiErr?.message ?: "이메일을 찾을 수 없습니다. (code=${res.code()})"
                    Toast.makeText(this@FindEmailActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this@FindEmailActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                Toast.makeText(this@FindEmailActivity, "서버 오류가 발생했습니다. (${e.code()})", Toast.LENGTH_SHORT).show()
            } finally {
                btnNext.isEnabled = true
            }
        }
    }
}
