package com.example.lingo.ui.start

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R

class AgreementActivity : AppCompatActivity() {

    private lateinit var checkAll: CheckBox        // 전체 약관동의
    private lateinit var check1: CheckBox          // (필수) 이용 약관 동의
    private lateinit var check2: CheckBox          // (필수) 개인정보 수집 동의
    private lateinit var check3: CheckBox          // (선택) 마케팅 동의
    private lateinit var nextButton: Button

    // 동의 상태 저장 키(간단 SharedPreferences 버전)
    private val prefName = "agreement_prefs"
    private val keyAccepted = "agreement_accepted"
    private val keyVersion = "agreement_version"

    // 약관/개인정보 내용이 바뀌면 이 값을 올려서 재동의 받기
    private val AGREEMENT_VERSION = 1

    // 프로그램적으로 체크 상태를 바꿀 때 리스너 중첩 호출 방지용
    private var muteListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agreement)

        // ID 연결 (XML에 존재해야 합니다)
        checkAll = findViewById(R.id.checkAll)
        check1   = findViewById(R.id.checkbox1)
        check2   = findViewById(R.id.checkbox2)
        check3   = findViewById(R.id.checkbox3)
        nextButton = findViewById(R.id.next_button)

        // 초기 상태 적용
        updateButtonState()
        syncAllCheckFromIndividuals()

        // 전체 약관동의 클릭 시: 세 개 모두 같은 상태로
        checkAll.setOnCheckedChangeListener { _, isChecked ->
            if (muteListener) return@setOnCheckedChangeListener
            mute {
                check1.isChecked = isChecked
                check2.isChecked = isChecked
                check3.isChecked = isChecked
            }
            updateButtonState()
        }

        // 개별 체크 변경 시: 버튼 상태 갱신 + 전체동의 동기화
        val individualListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            if (muteListener) return@OnCheckedChangeListener
            updateButtonState()
            syncAllCheckFromIndividuals()
        }

        check1.setOnCheckedChangeListener(individualListener)
        check2.setOnCheckedChangeListener(individualListener)
        check3.setOnCheckedChangeListener(individualListener)

        // 버튼 클릭
        nextButton.setOnClickListener {
            if (check1.isChecked && check2.isChecked) {
                saveAgreementAccepted()
                startActivity(
                    Intent(this, StartActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    }
                )
                finish()
            } else {
                // 방어 로직
                Toast.makeText(this, "필수 항목에 모두 동의해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 필수 2개(checkbox1, checkbox2) 체크 여부로 버튼 활성화 */
    private fun updateButtonState() {
        val isChecked = check1.isChecked && check2.isChecked
        nextButton.isEnabled = isChecked
        nextButton.setBackgroundResource(
            if (isChecked) R.drawable.outlined_button_blue
            else R.drawable.outlined_button_gray
        )
    }

    /** 개별 체크 상태를 보고 전체동의 체크를 동기화 */
    private fun syncAllCheckFromIndividuals() {
        val allOn = check1.isChecked && check2.isChecked && check3.isChecked
        mute {
            checkAll.isChecked = allOn
        }
    }

    /** 리스너 일시 정지 래퍼 */
    private inline fun mute(block: () -> Unit) {
        muteListener = true
        try { block() } finally { muteListener = false }
    }

    private fun saveAgreementAccepted() {
        val sp = getSharedPreferences(prefName, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(keyAccepted, true)
            .putInt(keyVersion, AGREEMENT_VERSION)
            .apply()
    }
}
