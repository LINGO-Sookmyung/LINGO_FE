package com.example.lingo.ui.login.find

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.lingo.R
import com.example.lingo.ui.login.LoginActivity
import com.example.lingo.ui.base.BaseActivity

class FindPasswordResultActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_password_result)
        setAppBar(title = "비밀번호 찾기", showBack = true)

        val tvGuide = findViewById<TextView>(R.id.tvGuide)
        val tvNewPw = findViewById<TextView>(R.id.tvNewPassword)
        val btnGoLogin = findViewById<Button>(R.id.btnGoLogin)

        tvGuide.text = "새 비밀번호가 발급되었습니다."
        tvNewPw.text = intent.getStringExtra(FindPasswordCodeActivity.EXTRA_NEW_PASSWORD).orEmpty()

        btnGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
