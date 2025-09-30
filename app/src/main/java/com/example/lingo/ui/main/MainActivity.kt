package com.example.lingo.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.ui.login.LoginActivity
import com.example.lingo.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import com.example.lingo.ui.main.mypage.MyPageActivity
import com.example.lingo.ui.main.translation.TranslationDocActivity
import com.example.lingo.ui.start.SignupActivity
import com.example.lingo.util.TokenManager

class MainActivity : BaseActivity() {

    // 상단 로그인/회원가입/로그아웃
    private lateinit var tvGoSignup: TextView
    private lateinit var tvGoLogin: TextView
    private lateinit var tvLogout: TextView
    private lateinit var tvDivider: TextView

    // 증명서 버튼
    private lateinit var familyCert: TextView
    private lateinit var enrollmentCert: TextView
    private lateinit var propertyCert: TextView

    // 증명서 박스들을 멤버로 관리
    private lateinit var certBoxes: List<TextView>

    // 하단 네비(텍스트/아이콘)
    private lateinit var tabHome: LinearLayout
    private lateinit var tabMy: LinearLayout
    private lateinit var iconHome: ImageView
    private lateinit var iconMy: ImageView
    private lateinit var textHome: TextView
    private lateinit var textMy: TextView

    // 기존 SharedPreferences (UI 판정용으로만 사용)
    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }

    // 로그인 상태는 TokenManager를 단일 소스로 사용
    private fun isUserLoggedIn(): Boolean {
        val tm = TokenManager.get(applicationContext)
        return !tm.getAccessToken().isNullOrBlank()
    }

    // 세션 정리: TokenManager + prefs 동시 초기화
    private fun clearSession() {
        TokenManager.get(applicationContext).clear()
        prefs.edit()
            .remove("jwt_token")
            .remove("refresh_token")
            .putBoolean("is_logged_in", false)
            .apply()
        unselectAllCertificates()
    }

    // 로그아웃 API (전역 ServiceLocator 사용: AuthInterceptor 포함)
    private val api by lazy { com.example.lingo.data.remote.ServiceLocator.provideApi(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setHomeSelectedUI()

        certBoxes = listOf(familyCert, enrollmentCert, propertyCert)

        setupAuthArea()
        setupCertificateButtons()
        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        setupAuthArea()            // 로그인 상태 갱신
        unselectAllCertificates()
    }

    /** 모든 증명서 선택 해제 (파란 테두리 제거) */
    private fun unselectAllCertificates() {
        if (::certBoxes.isInitialized) {
            certBoxes.forEach { it.isSelected = false }
        }
    }

    /** findViewById 몰아넣기 */
    private fun bindViews() {
        // 상단
        tvGoSignup = findViewById(R.id.tvGoSignup)
        tvGoLogin  = findViewById(R.id.tvGoLogin)
        tvLogout   = findViewById(R.id.tvLogout)
        tvDivider  = findViewById(R.id.tvDivider)

        // 증명서
        familyCert     = findViewById(R.id.family_cert)
        enrollmentCert = findViewById(R.id.enrollment_cert)
        propertyCert   = findViewById(R.id.property_cert)

        // 하단
        tabHome  = findViewById(R.id.tabHome)
        tabMy    = findViewById(R.id.tabMy)
        iconHome = findViewById(R.id.iconHome)
        iconMy   = findViewById(R.id.iconMy)
        textHome = findViewById(R.id.textHome)
        textMy   = findViewById(R.id.textMy)

        // 탭 클릭
        tabHome.setOnClickListener { setHomeSelectedUI() }
    }

    /** 홈 탭 선택 UI */
    private fun setHomeSelectedUI() {
        iconHome.setImageResource(R.drawable.ic_home_black) // 검정
        iconMy.setImageResource(R.drawable.ic_mypage)       // 회색
        textHome.setTextColor(0xFF000000.toInt())
        textMy.setTextColor(0xFF888888.toInt())
    }

    /** 상단: 비로그인 = 회원가입/로그인, 로그인 = 로그아웃 */
    private fun setupAuthArea() {
        val loggedIn = isUserLoggedIn()

        if (loggedIn) {
            // 로그인 상태 → 로그아웃만 보이기
            tvGoSignup.visibility = View.GONE
            tvGoLogin.visibility  = View.GONE
            tvDivider.visibility  = View.GONE
            tvLogout.visibility   = View.VISIBLE

            tvLogout.setOnClickListener {
                tvLogout.isEnabled = false
                lifecycleScope.launch {
                    try {
                        // 선(先) 재발급은 AuthInterceptor가 처리하므로 생략
                        // 바로 로그아웃 호출
                        val res = withContext(Dispatchers.IO) { api.logout() }
                        when {
                            res.isSuccessful -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    res.body()?.message ?: "로그아웃되었습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            res.code() == 401 -> {
                                // 이미 만료/무효 → 사실상 로그아웃 상태로 간주
                                Toast.makeText(
                                    this@MainActivity,
                                    "로그아웃되었습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            else -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "로그아웃 실패 (code=${res.code()})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (_: IOException) {
                        Toast.makeText(this@MainActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    } catch (e: HttpException) {
                        Toast.makeText(this@MainActivity, "서버 오류가 발생했습니다. (${e.code()})", Toast.LENGTH_SHORT).show()
                    } finally {
                        // 무조건 로컬 세션 종료 + UI 갱신
                        clearSession()
                        setupAuthArea()
                        tvLogout.isEnabled = true
                    }
                }
            }
        } else {
            // 비로그인 상태 → 회원가입/로그인 보이기
            tvGoSignup.visibility = View.VISIBLE
            tvGoLogin.visibility  = View.VISIBLE
            tvDivider.visibility  = View.VISIBLE
            tvLogout.visibility   = View.GONE

            tvGoSignup.setOnClickListener {
                startActivity(Intent(this, SignupActivity::class.java))
            }
            tvGoLogin.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
    }

    /** 증명서 박스: 기본(테두리 없음), 선택 시 파란 테두리 + 조건부 모달 */
    private fun setupCertificateButtons() {
        // 변경: 멤버 certBoxes 사용
        certBoxes.forEach { box ->
            box.isSelected = false
            box.setOnClickListener {
                // 클릭 시 우선 전체 해제 후 현재만 선택
                unselectAllCertificates()
                box.isSelected = true

                if (isUserLoggedIn()) {
                    // 로그인 → 바로 진행 (진입 전에 선택 해제해도 되고, 돌아올 때 onResume에서 해제됨)
                    goNext(box.id)
                } else {
                    // 비로그인 → 모달
                    showLoginPromptDialog(
                        onLogin = {
                            // 로그인 화면으로 이동할 때도 테두리 바로 원복
                            unselectAllCertificates()
                            startActivity(Intent(this, LoginActivity::class.java))
                        },
                        onProceedWithoutLogin = {
                            // 게스트 진행 시: 이동 직전에 원복(바로 원복하고 전환)
                            unselectAllCertificates()
                            goNext(box.id)
                        },
                        onCancel = {
                            // 팝업을 닫으면 원복
                            unselectAllCertificates()
                        }
                    )
                }
            }
        }
    }

    private fun goNext(certViewId: Int) {
        when (certViewId) {
            R.id.family_cert -> startDocFlow("가족관계증명서")
            R.id.enrollment_cert -> startDocFlow("재학증명서")
            R.id.property_cert -> startDocFlow("부동산등기부등본 - 건물")
        }
    }

    private fun startDocFlow(title: String) {
        val intent = Intent(this, TranslationDocActivity::class.java)
            .putExtra("doc_title", title)
        startActivity(intent)
    }

    /** 비로그인 상태에서 마이 탭: 로그인 유도 모달 */
    private fun showLoginRequiredDialog(
        onLogin: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_login_required, null, false)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            // 팝업 닫히면 혹시 남아있을 수 있는 테두리 원복
            unselectAllCertificates()
            onCancel()
        }

        val btnClose  = view.findViewById<ImageButton>(R.id.btnClose)
        val btnGoLogin = view.findViewById<Button>(R.id.btnGoLogin)

        btnClose.setOnClickListener {
            dialog.cancel()
        }
        btnGoLogin.setOnClickListener {
            dialog.dismiss()
            unselectAllCertificates()
            onLogin()
        }

        dialog.show()
    }

    /** 증명서 클릭 시 비로그인: 로그인/게스트 선택 모달 */
    private fun showLoginPromptDialog(
        onLogin: () -> Unit,
        onProceedWithoutLogin: () -> Unit,
        onCancel: () -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_login_prompt, null, false)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            unselectAllCertificates()
            onCancel()
        }

        val btnClose   = view.findViewById<ImageButton>(R.id.btnClose)
        val btnGoLogin = view.findViewById<Button>(R.id.btnGoLogin)
        val btnGuest   = view.findViewById<Button>(R.id.btnGuest)

        btnClose.setOnClickListener {
            dialog.cancel()
        }
        btnGoLogin.setOnClickListener {
            dialog.dismiss()
            unselectAllCertificates()
            onLogin()
        }
        btnGuest.setOnClickListener {
            dialog.dismiss()
            unselectAllCertificates()
            onProceedWithoutLogin()
        }

        dialog.show()
    }

    /** 하단 네비게이션 */
    private fun setupBottomNav() {
        val black = ContextCompat.getColor(this, R.color.black)
        val gray  = ContextCompat.getColor(this, R.color.gray)

        textHome.setTextColor(black)
        textMy.setTextColor(gray)
        iconHome.setImageResource(R.drawable.ic_home_black)
        iconMy.setImageResource(R.drawable.ic_mypage)

        tabMy.setOnClickListener {
            if (isUserLoggedIn()) {
                // 로그인 상태: 모달 없이 바로
                startActivity(Intent(this, MyPageActivity::class.java))
            } else {
                // 비로그인: 로그인 유도
                showLoginRequiredDialog(
                    onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                    onCancel = { /* no-op */ }
                )
            }
        }
    }
}
