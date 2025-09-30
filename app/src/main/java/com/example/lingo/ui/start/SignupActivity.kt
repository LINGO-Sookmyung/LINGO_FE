package com.example.lingo.ui.start

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.core.model.ApiError
import com.example.lingo.ui.login.LoginActivity
import com.example.lingo.ui.base.BaseActivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import java.io.IOException
import java.util.*
import com.example.lingo.core.network.ApiService
import com.example.lingo.data.model.auth.SignupCheckEmailRequest
import com.example.lingo.data.model.auth.SignupRequest

class SignupActivity : BaseActivity() {

    private lateinit var editName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var editConfirmPassword: EditText
    private lateinit var editPhone: EditText
    private lateinit var tvPasswordMismatch: TextView
    private lateinit var btnSignUp: Button
    private lateinit var ivTogglePassword: ImageView
    private lateinit var ivToggleConfirmPassword: ImageView
    private lateinit var editBirthday: EditText
    private lateinit var ivCalendar: ImageView
    private lateinit var layoutBirthday: RelativeLayout
    private lateinit var btnCheckDup: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvEmailMessage: TextView
    private lateinit var tvPhoneMessage: TextView
    private lateinit var tvBirthdayMessage: TextView   // ★ 추가: 생년월일 에러 문구

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private enum class EmailState {
        UNCHECKED, VALID_AVAILABLE, INVALID_FORMAT, DUPLICATE
    }

    private var emailState: EmailState = EmailState.UNCHECKED

    // 서버 주소
    private val api: ApiService by lazy {
        provideApiService("http://54.215.109.237:8081/")
    }
    private val gson = Gson()

    private fun provideApiService(baseUrl: String): ApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)
        setAppBar(title = "회원가입", showBack = true)

        val root = findViewById<View>(R.id.rootSignup)

        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)
        editPhone = findViewById(R.id.editPhone)
        tvPasswordMismatch = findViewById(R.id.tvPasswordMismatch)
        btnSignUp = findViewById(R.id.btnSignUp)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword)
        editBirthday = findViewById(R.id.editBirthday)
        ivCalendar = findViewById(R.id.ivCalendar)
        layoutBirthday = findViewById(R.id.layoutBirthday)
        btnCheckDup = findViewById(R.id.btnCheckDup)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        tvEmailMessage = findViewById(R.id.tvEmailMessage)
        tvPhoneMessage = findViewById(R.id.tvPhoneMessage)
        tvBirthdayMessage = findViewById(R.id.tvBirthdayMessage)

        // 비번 토글
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(editPassword, ivTogglePassword, isPasswordVisible)
        }
        ivToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(editConfirmPassword, ivToggleConfirmPassword, isConfirmPasswordVisible)
        }

        // 생년월일 직접 입력(자동 하이픈/검증/커서 유지) 활성화
        enableManualBirthdayInput()

        // 공통 텍스트 감시자
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { validateInputs() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        editName.addTextChangedListener(textWatcher)
        editPassword.addTextChangedListener(textWatcher)
        editConfirmPassword.addTextChangedListener(textWatcher)
        editBirthday.addTextChangedListener(textWatcher)
        editPhone.addTextChangedListener(textWatcher)

        // 이메일 전용 감시자(형식 에러 즉시 반영)
        editEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = s?.toString()?.trim().orEmpty()
                when {
                    email.isEmpty() -> setEmailState(EmailState.UNCHECKED)
                    !isEmailFormatValid(email) -> setEmailState(EmailState.INVALID_FORMAT, "이메일 형식이 잘못되었습니다.")
                    else -> setEmailState(EmailState.UNCHECKED)
                }
                validateInputs()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 중복 확인
        btnCheckDup.setOnClickListener {
            val email = editEmail.text.toString().trim()
            if (!isEmailFormatValid(email)) {
                setEmailState(EmailState.INVALID_FORMAT, "이메일 형식이 잘못되었습니다.")
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        api.checkEmail(SignupCheckEmailRequest(email))
                    }
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.available == true) {
                            setEmailState(EmailState.VALID_AVAILABLE, body.message)
                        } else {
                            setEmailState(EmailState.DUPLICATE, body?.message ?: "이미 사용 중인 이메일입니다.")
                        }
                    } else {
                        val apiErr = try { gson.fromJson(response.errorBody()?.charStream(), ApiError::class.java) } catch (_: Exception) { null }
                        val msg = apiErr?.message ?: "이메일 확인 중 오류가 발생했습니다. (code=${response.code()})"
                        if (response.code() == 400) setEmailState(EmailState.INVALID_FORMAT, msg)
                        else setEmailState(EmailState.DUPLICATE, msg)
                    }
                } catch (e: IOException) {
                    setEmailState(EmailState.DUPLICATE, "네트워크 오류가 발생했습니다.")
                } catch (e: HttpException) {
                    setEmailState(EmailState.DUPLICATE, "서버 오류가 발생했습니다. (${e.code()})")
                }
            }
        }

        // 초기 UI
        btnCheckDup.isEnabled = false
        btnCheckDup.setBackgroundResource(R.drawable.outlined_button_gray)
        tvEmailMessage.visibility = View.GONE
        tvPhoneMessage.visibility = View.GONE
        tvBirthdayMessage.visibility = View.GONE
        emailState = EmailState.UNCHECKED
        validateInputs()

        setupFocusHighlight(editEmail)   { hasEmailError() } // 이메일 에러 반영
        setupFocusHighlight(editPassword)
        setupFocusHighlight(editConfirmPassword) { tvPasswordMismatch.visibility == View.VISIBLE } // 비번 불일치 반영
        setupFocusHighlight(editName)
        setupFocusHighlight(editPhone)   { tvPhoneMessage.visibility == View.VISIBLE } // 전화번호 에러 반영

        // 바깥 터치: 포커스 해제 + 모두 기본 테두리로 (에러라도 테두리는 기본)
        root.isClickable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener {
            // 포커스 전부 해제
            currentFocus?.clearFocus()
            editEmail.clearFocus()
            editPassword.clearFocus()
            editConfirmPassword.clearFocus()
            editName.clearFocus()
            editPhone.clearFocus()
            editBirthday.clearFocus()

            // 모두 기본 테두리로 세팅
            editEmail.setBackgroundResource(R.drawable.outlined_button_white)
            editPassword.setBackgroundResource(R.drawable.outlined_button_white)
            editConfirmPassword.setBackgroundResource(R.drawable.outlined_button_white)
            editName.setBackgroundResource(R.drawable.outlined_button_white)
            editPhone.setBackgroundResource(R.drawable.outlined_button_white)
            editBirthday.setBackgroundResource(R.drawable.outlined_button_white)

            // 메시지는 기존 로직대로 유지 (보이기/숨기기만)
            tvBirthdayMessage.visibility = if (hasBirthdayError()) View.VISIBLE else View.GONE
        }

        // 캘린더: 다른 칸 초기화(에러 유지) → 생일칸만 파랑 → DatePicker
        ivCalendar.setOnClickListener {
            resetNonBirthdayFieldsUI()
            editBirthday.requestFocus()
            ivCalendar.setImageResource(R.drawable.ic_calendar_blue)
            showDatePicker()
        }
    }

    // ======== 생년월일 직접 입력(자동 하이픈/검증/커서 유지) ========
    private fun enableManualBirthdayInput() {
        // 편집 가능 보장 + LTR 고정
        editBirthday.isEnabled = true
        editBirthday.isFocusable = true
        editBirthday.isFocusableInTouchMode = true
        editBirthday.isCursorVisible = true
        editBirthday.isClickable = true
        editBirthday.isLongClickable = true

        editBirthday.textDirection = View.TEXT_DIRECTION_LTR
        editBirthday.layoutDirection = View.LAYOUT_DIRECTION_LTR
        editBirthday.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        editBirthday.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL

        // 숫자만 입력
        editBirthday.inputType = InputType.TYPE_CLASS_NUMBER
        editBirthday.hint = "YYYY-MM-DD"

        editBirthday.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            private var beforeText: String = ""

            // onTextChanged 파라미터 저장
            private var chStart: Int = 0
            private var chBefore: Int = 0
            private var chCount: Int = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                beforeText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 이번 변경이 시작된 오프셋, 지워진 길이, 추가된 길이 저장
                chStart = start
                chBefore = before
                chCount = count
            }

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true

                val current = s?.toString() ?: ""

                // 1) 변경 구간 "앞"에 있던 숫자 개수 (커서 복원 기준)
                val leftDigitsBefore = beforeText
                    .take(chStart)
                    .count { it.isDigit() }

                // 2) 이번 변경으로 실제 들어간 '숫자' 개수 (붙여넣기 포함)
                val insertedDigitsCount = current
                    .substring(chStart, (chStart + chCount).coerceAtMost(current.length))
                    .count { it.isDigit() }

                // 3) 현재 전체 숫자열 (이게 최종 소스가 됨) — 최대 8자리(YYYYMMDD)
                val digitsNow = current.filter { it.isDigit() }.take(8)

                // 4) YYYY-MM-DD 포맷 생성
                val formatted = buildString {
                    for (i in digitsNow.indices) {
                        append(digitsNow[i])
                        if (i == 3 || i == 5) append("-")
                    }
                }

                // 5) 텍스트 반영
                if (formatted != current) {
                    editBirthday.setText(formatted)
                }

                // 6) 커서 복원:
                //    - 삽입: 왼쪽 숫자 + 삽입된 숫자 만큼 앞으로
                //    - 삭제: 삽입 0 → 왼쪽 숫자 그대로
                val newDigitIdx = (leftDigitsBefore + insertedDigitsCount)
                    .coerceAtMost(digitsNow.length)
                    .coerceAtLeast(0)

                val newCursor = positionAfterKthDigit(formatted, newDigitIdx)
                editBirthday.setSelection(newCursor)

                // 7) 에러/테두리 갱신
                val isErr = hasBirthdayError()
                tvBirthdayMessage.visibility = if (isErr) View.VISIBLE else View.GONE
                if (isErr) {
                    tvBirthdayMessage.text = "생년월일 형식이 잘못되었습니다."
                    tvBirthdayMessage.setTextColor(Color.parseColor("#FF6666"))
                }
                applyFieldBackground(editBirthday, hasError = isErr)

                isFormatting = false
                validateInputs()
            }
        })

        editBirthday.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            val isError = hasBirthdayError()
            editBirthday.setBackgroundResource(
                when {
                    hasFocus && isError -> R.drawable.outlined_button_error
                    hasFocus            -> R.drawable.outlined_button_selected
                    else                -> R.drawable.outlined_button_white
                }
            )
        }
    }

    /** 포맷 문자열에서 k번째(1-based) 숫자 '뒤' 인덱스 반환. k=0이면 0 */
    private fun positionAfterKthDigit(formatted: String, k: Int): Int {
        if (k <= 0) return 0
        var count = 0
        for (i in formatted.indices) {
            if (formatted[i].isDigit()) {
                count++
                if (count == k) return (i + 1).coerceAtMost(formatted.length)
            }
        }
        return formatted.length
    }

    // 허용 형식: YYYY-MM 또는 YYYY-MM-DD (간단 검증)
    private fun validateBirthdayInput(text: String): Boolean {
        val ym  = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")
        val ymd = Regex("""^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""")
        return ym.matches(text) || ymd.matches(text)
    }

    // 생년월일 에러: "값이 있고" 형식이 잘못됐을 때만 true
    private fun hasBirthdayError(): Boolean {
        val t = editBirthday.text.toString()
        return t.isNotEmpty() && !validateBirthdayInput(t)
    }
    // ================================================

    // 생일 입력 UI 기본 복귀
    private fun resetBirthdayUI() {
        ivCalendar.setImageResource(R.drawable.ic_calendar)
        editBirthday.setBackgroundResource(R.drawable.outlined_button_white)
        editBirthday.clearFocus()
        // 취소 시 메시지는 그대로 두되, 빈 값이면 숨김
        tvBirthdayMessage.visibility = if (hasBirthdayError()) View.VISIBLE else View.GONE
    }

    // 생일 제외 다른 칸 기본 복귀(에러는 유지)
    private fun resetNonBirthdayFieldsUI() {
        // 포커스 해제
        editEmail.clearFocus()
        editPassword.clearFocus()
        editConfirmPassword.clearFocus()
        editName.clearFocus()
        editPhone.clearFocus()

        // 이메일: 형식 에러면 빨강 유지
        val emailInvalid = hasEmailError()
        editEmail.setBackgroundResource(if (emailInvalid) R.drawable.outlined_button_error else R.drawable.outlined_button_white)

        // 비밀번호 확인: 불일치면 빨강 유지
        val pwMismatchNow = editConfirmPassword.text.isNotEmpty() &&
                editConfirmPassword.text.toString() != editPassword.text.toString()
        editConfirmPassword.setBackgroundResource(if (pwMismatchNow) R.drawable.outlined_button_error else R.drawable.outlined_button_white)

        // 비밀번호/이름은 기본
        editPassword.setBackgroundResource(R.drawable.outlined_button_white)
        editName.setBackgroundResource(R.drawable.outlined_button_white)

        // 전화번호: 에러면 빨강 유지
        val phoneHasError = tvPhoneMessage.visibility == View.VISIBLE
        editPhone.setBackgroundResource(if (phoneHasError) R.drawable.outlined_button_error else R.drawable.outlined_button_white)

        // 생년월일 메시지도 동기화
        tvBirthdayMessage.visibility = if (hasBirthdayError()) View.VISIBLE else View.GONE
    }

    // 포커스 변화 시 테두리
    private fun setupFocusHighlight(
        edit: EditText,
        errorChecker: (() -> Boolean)? = null
    ) {
        edit.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val hasError = (errorChecker?.invoke() == true)
            v.setBackgroundResource(
                when {
                    hasFocus && hasError -> R.drawable.outlined_button_error
                    hasFocus             -> R.drawable.outlined_button_selected
                    else                 -> R.drawable.outlined_button_white
                }
            )
        }
    }

    // 전화번호 유효성
    private val MIN_PHONE_LEN = 9
    private fun isPhoneValid(phone: String): Boolean =
        phone.isNotEmpty() && phone.all { it.isDigit() } && phone.length >= MIN_PHONE_LEN

    // 이메일 형식
    private fun isEmailFormatValid(email: String): Boolean =
        email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun hasEmailError(): Boolean {
        val email = editEmail.text.toString().trim()
        val formatBad = email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()
        // 형식이 나쁘거나, 서버 체크 결과가 "형식 에러/중복"일 때만 에러
        return formatBad || emailState == EmailState.INVALID_FORMAT || emailState == EmailState.DUPLICATE
    }

    // 이메일 상태 UI
    private fun setEmailState(state: EmailState, message: String? = null) {
        emailState = state
        when (state) {
            EmailState.UNCHECKED -> {
                tvEmailMessage.visibility = View.GONE
                btnCheckDup.isEnabled = true
                btnCheckDup.setBackgroundResource(R.drawable.outlined_button_blue)
            }
            EmailState.INVALID_FORMAT -> {
                tvEmailMessage.visibility = View.VISIBLE
                tvEmailMessage.text = message ?: "이메일 형식이 잘못되었습니다."
                tvEmailMessage.setTextColor(Color.parseColor("#FF6666"))
                btnCheckDup.isEnabled = false
                btnCheckDup.setBackgroundResource(R.drawable.outlined_button_gray)
            }
            EmailState.DUPLICATE -> {
                tvEmailMessage.visibility = View.VISIBLE
                tvEmailMessage.text = message ?: "이미 사용 중인 이메일입니다."
                tvEmailMessage.setTextColor(Color.parseColor("#FF6666"))
                btnCheckDup.isEnabled = false
                btnCheckDup.setBackgroundResource(R.drawable.outlined_button_gray)
            }
            EmailState.VALID_AVAILABLE -> {
                tvEmailMessage.visibility = View.VISIBLE
                tvEmailMessage.text = message ?: "사용 가능한 이메일입니다."
                tvEmailMessage.setTextColor(Color.parseColor("#2196F3"))
                btnCheckDup.isEnabled = false
                btnCheckDup.setBackgroundResource(R.drawable.outlined_button_darkgray)
            }
        }
        validateInputs()
    }

    private fun doSignup() {
        val req = SignupRequest(
            email = editEmail.text.toString().trim(),
            password = editPassword.text.toString(),
            pwConfirm = editConfirmPassword.text.toString(),
            name = editName.text.toString().trim(),
            birth = editBirthday.text.toString().trim(),
            phoneNum = editPhone.text.toString().trim(),
            memberType = "USER"
        )

        if (req.password != req.pwConfirm) {
            Toast.makeText(this, "비밀번호와 비밀번호 확인이 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { api.signup(req) }
                if (res.isSuccessful) {
                    val body = res.body()
                    Toast.makeText(this@SignupActivity, body?.message ?: "회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                    finish()
                } else {
                    val errBody = res.errorBody()?.charStream()
                    val apiError = try { gson.fromJson(errBody, ApiError::class.java) } catch (_: Exception) { null }
                    val msg = apiError?.message ?: "회원가입에 실패했습니다. (code=${res.code()})"
                    Toast.makeText(this@SignupActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this@SignupActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                Toast.makeText(this@SignupActivity, "서버 오류가 발생했습니다. (${e.code()})", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this@SignupActivity, "설정 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        btnSignUp.isEnabled = !isLoading
        // progress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean) {
        editText.inputType = if (isVisible) {
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        imageView.setImageResource(if (isVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        editText.setSelection(editText.text.length)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val dialog = DatePickerDialog(this, { _, y, m, d ->
            editBirthday.setText(String.format("%04d-%02d-%02d", y, m + 1, d))
            resetBirthdayUI()
        }, year, month, day)

        dialog.setOnCancelListener { resetBirthdayUI() }
        dialog.show()
    }

    // 에러/포커스에 따라 배경을 세팅하는 공용 헬퍼
    private fun applyFieldBackground(edit: EditText, hasError: Boolean) {
        val focused = edit.hasFocus()
        edit.setBackgroundResource(
            when {
                focused && hasError -> R.drawable.outlined_button_error
                focused            -> R.drawable.outlined_button_selected
                else               -> R.drawable.outlined_button_white
            }
        )
    }

    private fun validateInputs() {
        if (!::editName.isInitialized ||
            !::editEmail.isInitialized ||
            !::editPassword.isInitialized ||
            !::editConfirmPassword.isInitialized ||
            !::editBirthday.isInitialized ||
            !::editPhone.isInitialized ||
            !::tvPasswordMismatch.isInitialized ||
            !::btnSignUp.isInitialized ||
            !::btnCheckDup.isInitialized ||
            !::tvPhoneMessage.isInitialized ||
            !::tvBirthdayMessage.isInitialized
        ) return

        val name = editName.text.toString()
        val email = editEmail.text.toString()
        val password = editPassword.text.toString()
        val confirmPassword = editConfirmPassword.text.toString()
        val birth = editBirthday.text.toString()
        val phone = editPhone.text.toString()

        // 비밀번호 불일치 메시지
        val pwMismatch = confirmPassword.isNotEmpty() && confirmPassword != password
        tvPasswordMismatch.visibility = if (pwMismatch) View.VISIBLE else View.GONE

        // 전화번호
        if (phone.isEmpty()) {
            tvPhoneMessage.visibility = View.GONE
            applyFieldBackground(editPhone, hasError = false)
        } else if (!isPhoneValid(phone)) {
            tvPhoneMessage.visibility = View.VISIBLE
            tvPhoneMessage.text = "전화번호 형식이 잘못되었습니다."
            tvPhoneMessage.setTextColor(Color.parseColor("#FF6666"))
            applyFieldBackground(editPhone, hasError = true)
        } else {
            tvPhoneMessage.visibility = View.GONE
            applyFieldBackground(editPhone, hasError = false)
        }

        // 이메일: 형식 에러/중복만 빨강, 나머진 포커스/기본
        applyFieldBackground(editEmail, hasError = hasEmailError())

        // 비밀번호 확인: 불일치면 빨강, 아니면 포커스/기본
        applyFieldBackground(editConfirmPassword, hasError = pwMismatch)

        // 생년월일: "빈 값은 오류 아님", 값이 있고 형식 틀릴 때만 빨강 + 메시지
        val birthdayErr = hasBirthdayError()
        tvBirthdayMessage.visibility = if (birthdayErr) View.VISIBLE else View.GONE
        if (birthdayErr) {
            tvBirthdayMessage.text = "생년월일 형식이 잘못되었습니다."
            tvBirthdayMessage.setTextColor(Color.parseColor("#FF6666"))
        }
        applyFieldBackground(editBirthday, hasError = birthdayErr)

        // 중복 확인 버튼 상태
        val emailFormatOk = Patterns.EMAIL_ADDRESS.matcher(email).matches()
        when (emailState) {
            EmailState.INVALID_FORMAT -> {
                btnCheckDup.isEnabled = false
                btnCheckDup.isClickable = false
                btnCheckDup.setBackgroundResource(R.drawable.outlined_button_gray)
            }
            EmailState.VALID_AVAILABLE -> {
                btnCheckDup.isEnabled = false
                btnCheckDup.isClickable = false
                btnCheckDup.setBackgroundResource(R.drawable.outlined_button_darkgray)
            }
            EmailState.DUPLICATE -> {
                btnCheckDup.isEnabled = false
                btnCheckDup.isClickable = false
                btnCheckDup.setBackgroundResource(R.drawable.outlined_button_gray)
            }
            EmailState.UNCHECKED -> {
                if (email.isNotEmpty() && emailFormatOk) {
                    btnCheckDup.isEnabled = true
                    btnCheckDup.isClickable = true
                    btnCheckDup.setBackgroundResource(R.drawable.outlined_button_blue)
                } else {
                    btnCheckDup.isEnabled = false
                    btnCheckDup.isClickable = false
                    btnCheckDup.setBackgroundResource(R.drawable.outlined_button_gray)
                }
            }
        }

        // 회원가입 버튼
        val isFormValid =
            name.isNotEmpty() &&
                    email.isNotEmpty() &&
                    password.isNotEmpty() &&
                    confirmPassword.isNotEmpty() &&
                    birth.isNotEmpty() &&
                    isPhoneValid(phone) &&
                    !pwMismatch &&
                    emailState == EmailState.VALID_AVAILABLE

        btnSignUp.isEnabled = isFormValid
        btnSignUp.isClickable = isFormValid
        btnSignUp.isFocusable = isFormValid
        btnSignUp.setBackgroundResource(
            if (isFormValid) R.drawable.outlined_button_blue else R.drawable.outlined_button_gray
        )

        btnSignUp.setOnClickListener(if (isFormValid) View.OnClickListener { doSignup() } else null)
    }
}
