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
import com.example.lingo.data.model.auth.CheckCurrentPasswordRequest
import com.example.lingo.core.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.util.Base64
import com.example.lingo.core.di.ServiceLocator
import org.json.JSONObject
import com.example.lingo.data.local.TokenManager

class ChangePasswordCurrentActivity : BaseActivity() {

    private lateinit var boxPassword: View
    private lateinit var editCurrentPw: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var tvError: TextView
    private lateinit var btnNext: LinearLayout

    private var passwordVisible = false

    private val api: ApiService by lazy {
        ServiceLocator.provideApi(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password_current)
        setAppBar(title = "비밀번호 변경", showBack = true)

        initViews()
        initInteractions()
    }

    private fun initViews() {
        boxPassword      = findViewById(R.id.boxPassword)
        editCurrentPw    = findViewById(R.id.editCurrentPw)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)
        tvError          = findViewById(R.id.tvError)
        btnNext          = findViewById(R.id.btnNext)

        tvError.visibility = View.GONE
    }

    private fun initInteractions() {
        // 눈 아이콘 토글
        ivTogglePassword.setOnClickListener {
            if (passwordVisible) {
                editCurrentPw.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ic_eye)
            } else {
                editCurrentPw.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ic_eye_off)
            }
            passwordVisible = !passwordVisible
            editCurrentPw.setSelection(editCurrentPw.text?.length ?: 0)
        }

        // 포커스 시 테두리
        editCurrentPw.setOnFocusChangeListener { _, hasFocus ->
            boxPassword.setBackgroundResource(
                when {
                    tvError.visibility == View.VISIBLE -> R.drawable.outlined_button_error
                    hasFocus -> R.drawable.outlined_button_selected
                    else -> R.drawable.outlined_button_white
                }
            )
        }

        // 입력 중 에러 숨기기
        editCurrentPw.doOnTextChanged { _, _, _, _ -> hideErrorIfNeeded() }
        editCurrentPw.setOnClickListener { hideErrorIfNeeded() }

        // 다음 버튼
        btnNext.setOnClickListener { submitCurrentPassword() }
    }

    /** 에러 메시지/테두리 복원 */
    private fun hideErrorIfNeeded() {
        if (tvError.visibility == View.VISIBLE) {
            tvError.visibility = View.GONE
            boxPassword.setBackgroundResource(
                if (editCurrentPw.hasFocus()) R.drawable.outlined_button_selected
                else R.drawable.outlined_button_white
            )
        }
    }

    /** 에러 상태 표시 */
    private fun showMismatchError(msg: String = "비밀번호가 일치하지 않습니다.") {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
        boxPassword.setBackgroundResource(R.drawable.outlined_button_error)
        editCurrentPw.requestFocus()
    }

    /** 로딩 상태에서 버튼 잠그기/해제 */
    private fun setLoading(loading: Boolean) {
        btnNext.isEnabled = !loading
        btnNext.alpha = if (loading) 0.6f else 1f
    }

    /** 실제 서버 검증 */
    private fun submitCurrentPassword() {
        val input = editCurrentPw.text?.toString()?.trim().orEmpty()
        if (input.isBlank()) {
            showMismatchError("현재 비밀번호를 입력해주세요.")
            return
        }

        // [JWT 디버그] 현재 access 만료/발급자 확인
        val access = TokenManager.get(applicationContext).getAccessToken()
        decodeJwtPayload(access.orEmpty())?.let { p ->
            val exp = p.optLong("exp", 0L) // 초 단위
            val now = System.currentTimeMillis() / 1000
            Log.d(
                "JWT",
                "exp=$exp now=$now exp-left=${exp - now}s iss=${p.optString("iss")} aud=${p.optString("aud")}"
            )
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.checkCurrentPassword(CheckCurrentPasswordRequest(currentPassword = input))
                }
                if (!res.isSuccessful) {
                    val bodyText = res.errorBody()?.string()
                    Log.e("ChangePw", "code=${res.code()} body=$bodyText")
                }

                if (res.isSuccessful) {
                    val body = res.body()
                    val isValid = body?.valid == true
                    if (isValid) {
                        startActivity(Intent(this@ChangePasswordCurrentActivity, ChangePasswordNewActivity::class.java))
                    } else {
                        showMismatchError()
                    }
                } else {
                    when (res.code()) {
                        400 -> showMismatchError()
                        401 -> showMismatchError("세션이 만료되었습니다. 다시 로그인해 주세요.")
                        else -> Toast.makeText(
                            this@ChangePasswordCurrentActivity,
                            "오류가 발생했습니다. (code=${res.code()})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                // 네트워크/JSON 등
                Toast.makeText(
                    this@ChangePasswordCurrentActivity,
                    "네트워크 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun decodeJwtPayload(jwt: String): JSONObject? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val payload = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            JSONObject(String(payload, Charsets.UTF_8))
        } catch (e: Exception) { null }
    }

}
