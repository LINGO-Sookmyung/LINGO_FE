package com.example.lingo.ui.main.translation.camera

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R

class PhotoCaptureGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture_guide)

        // 앱바
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topToolbar)?.apply {
            title = "사진 촬영 가이드"
            setNavigationOnClickListener { finish() }
        }

        // 확인했어요 -> OK 결과 반환 후 종료
        findViewById<View>(R.id.btnConfirm).setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
