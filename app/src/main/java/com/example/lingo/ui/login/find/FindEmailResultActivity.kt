package com.example.lingo.ui.login.find

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.lingo.R
import com.example.lingo.ui.login.LoginActivity
import com.example.lingo.ui.base.BaseActivity

class FindEmailResultActivity : BaseActivity() {

    companion object {
        const val EXTRA_FOUND_EMAIL = "extra_found_email"   // 마스킹된 이메일 문자열
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_email_result)
        setAppBar(title = "이메일 찾기", showBack = true)

        val tvFoundEmail = findViewById<TextView>(R.id.tvFoundEmail)
        val btnGoLogin   = findViewById<Button>(R.id.btnGoLogin)

        // 이전 화면에서 전달된 이메일 표시 (없으면 예비 텍스트 유지)
        val masked = intent.getStringExtra(EXTRA_FOUND_EMAIL)?.takeIf { it.isNotBlank() }
        if (masked != null) tvFoundEmail.text = masked

        btnGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // 결과 화면은 닫아도 OK
        }
    }
}
