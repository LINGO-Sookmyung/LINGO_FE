package com.example.lingo.ui.main.translation.camera

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R
import java.io.File

class PhotoReviewActivity : AppCompatActivity() {

    private var tempPath: String? = null
    private lateinit var imgPreview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_review)

        // 앱바
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topToolbar)?.apply {
            title = "사진 확인"
            setNavigationOnClickListener { finish() }
        }

        imgPreview = findViewById(R.id.imgPreview)

        // CameraCaptureActivity에서 넣어준 "temp_path" 받기
        tempPath = intent.getStringExtra("temp_path")

        if (tempPath.isNullOrBlank() || !File(tempPath!!).exists()) {
            Toast.makeText(this, "임시 사진을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // 파일 경로 → Uri로 변환해서 미리보기 표시
        val photoUri: Uri = Uri.fromFile(File(tempPath!!))
        imgPreview.setImageURI(photoUri)

        // 다시 촬영: 취소(카메라로 복귀). 필요하면 temp 삭제는 호출자에서 처리 중.
        findViewById<android.view.View>(R.id.btnRetake).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // 사진 사용: 상위에서 기대하는 "temp_path"로 돌려주기
        findViewById<android.view.View>(R.id.btnUse).setOnClickListener {
            setResult(Activity.RESULT_OK, intent.apply {
                putExtra("temp_path", tempPath)
            })
            finish()
        }
    }
}
