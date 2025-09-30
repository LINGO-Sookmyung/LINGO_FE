package com.example.lingo.ui.start

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.example.lingo.R
import com.example.lingo.ui.base.BaseActivity

class unused_SelectUserTypeActivity : BaseActivity() {

    private lateinit var btnGeneralMember: Button
    private lateinit var btnBusinessMember: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_user_type)
        setAppBar(title = "회원 유형", showBack = true)

        btnGeneralMember = findViewById(R.id.btnGeneralMember)
        btnBusinessMember = findViewById(R.id.btnBusinessMember)

        btnGeneralMember.setOnClickListener {
            // 일반 회원가입 화면으로 이동
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }

        btnBusinessMember.setOnClickListener {
            // 기업 회원가입 화면으로 이동 (SignupActivity 재사용 가능)
            val intent = Intent(this, SignupActivity::class.java)
            intent.putExtra("USER_TYPE", "business")
            startActivity(intent)
        }
    }
}