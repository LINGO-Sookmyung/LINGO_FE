package com.example.lingo.ui.main.mypage

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.lingo.R
import com.example.lingo.ui.main.MainActivity
import com.example.lingo.ui.base.BaseActivity

class MyPageActivity : BaseActivity() {

    private lateinit var tabHome: LinearLayout
    private lateinit var tabMy: LinearLayout
    private lateinit var iconHome: ImageView
    private lateinit var iconMy: ImageView
    private lateinit var textHome: TextView
    private lateinit var textMy: TextView

    // 메뉴 버튼들 (TextView 스타일)
    private lateinit var btnChangePassword: TextView
    private lateinit var btnDocuments: TextView
    // private lateinit var btnWithdraw: TextView

    // 메뉴 버튼 리스트 (테두리 컨트롤용)
    private lateinit var menuButtons: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage)

        // 하단 탭
        tabHome = findViewById(R.id.tabHome)
        tabMy = findViewById(R.id.tabMy)
        iconHome = findViewById(R.id.iconHome)
        iconMy = findViewById(R.id.iconMy)
        textHome = findViewById(R.id.textHome)
        textMy = findViewById(R.id.textMy)

        // 메뉴 바인딩
        btnDocuments = findViewById(R.id.btnDocuments)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        // btnWithdraw = findViewById(R.id.btnWithdraw)

        menuButtons = listOf(btnDocuments, btnChangePassword)

        // 앱바: 뒤로가기 없음, 타이틀만
        setAppBar(title = "마이페이지", showBack = false)

        setMyPageSelectedUI()

        // 하단 탭 동작
        tabHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        tabMy.setOnClickListener {
            // 이미 마이페이지 → 아무 동작 없음
        }

        setupMenuButtons()
    }

    override fun onResume() {
        super.onResume()
        // 화면 복귀 시 선택 초기화
        unselectAllMenuButtons()
    }

    /** 메뉴 버튼 모두 해제 */
    private fun unselectAllMenuButtons() {
        if (::menuButtons.isInitialized) {
            menuButtons.forEach { it.isSelected = false }
        }
    }

    /** 메뉴 버튼 클릭 처리 */
    private fun setupMenuButtons() {
        menuButtons.forEach { btn ->
            btn.setOnClickListener {
                // 1) 클릭 시 현재만 선택
                unselectAllMenuButtons()
                btn.isSelected = true

                when (btn.id) {
                    R.id.btnDocuments -> {
                        // 2) 화면 전환 직전에 해제하지 말 것!
                        startActivity(Intent(this, DocumentsActivity::class.java))
                        // 돌아오면 onResume()에서 자동 해제
                    }
                    R.id.btnChangePassword -> {
                        startActivity(Intent(this, ChangePasswordCurrentActivity::class.java))
                    }
                }
            }
        }
    }

    private fun setMyPageSelectedUI() {
        iconHome.setImageResource(R.drawable.ic_home)             // 회색
        iconMy.setImageResource(R.drawable.ic_mypage_black)       // 검정
        textHome.setTextColor(0xFF888888.toInt()) // 회색
        textMy.setTextColor(0xFF000000.toInt())   // 검정
    }
}
