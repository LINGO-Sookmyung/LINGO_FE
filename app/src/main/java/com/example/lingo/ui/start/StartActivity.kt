package com.example.lingo.ui.start

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R
import com.example.lingo.ui.login.LoginActivity
import com.example.lingo.ui.main.MainActivity
import com.example.lingo.util.AgreementPrefs
import com.example.lingo.util.TokenManager

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) 약관 동의 미완료면 약관 화면으로
        if (!AgreementPrefs.isAccepted(this)) {
            startActivity(
                Intent(this, AgreementActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra("from", "start")
                }
            )
            finish()
            return
        }

        // 2) 로그인 상태/토큰 확인
        val tm = TokenManager.get(this)
        val hasToken = !tm.getAccessToken().isNullOrBlank()
        val isLoggedIn = tm.isLoggedIn()
        val shouldGoMain = hasToken && isLoggedIn

        if (shouldGoMain) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
            )
            finish()
            return
        }

        // 3) 로그인 안 되어 있으면 시작 화면 노출
        setContentView(R.layout.activity_start)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGuest = findViewById<Button>(R.id.btnGuest)

        btnStart.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnGuest.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
    }
}
