package com.example.lingo.ui.main.translation.camera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.lingo.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: CameraOverlayView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var previewUseCase: Preview? = null
    private lateinit var shutterSound: MediaActionSound

    private var cameraProvider: ProcessCameraProvider? = null
    private var isBound = false

    private val reviewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                    val tempPath = result.data?.getStringExtra("temp_path")
                    if (!tempPath.isNullOrBlank()) {
                        val final = moveTempToGallery(File(tempPath))
                        if (final != null) {
                            setResult(RESULT_OK, Intent().setData(final))
                        } else {
                            Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show()
                            setResult(Activity.RESULT_CANCELED)
                        }
                        runCatching { File(tempPath).delete() }
                        finish()
                    } else {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }
                else -> {
                    val tempPath = result.data?.getStringExtra("temp_path")
                    if (!tempPath.isNullOrBlank()) runCatching { File(tempPath).delete() }
                    setResult(Activity.RESULT_CANCELED)
                    if (!isBound) startCameraSafely()
                }
            }
        }

    private var lastSavedUri: Uri? = null

    private val reqCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCameraSafely() else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    private val reqWriteLegacy =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_capture)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)

        // 🔧 SurfaceView → TextureView (COMPATIBLE) 로 전환
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        findViewById<View>(R.id.btnShutter).setOnClickListener { takePhotoFast() }

        cameraExecutor = Executors.newSingleThreadExecutor()
        shutterSound = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCameraSafely() else reqCamera.launch(Manifest.permission.CAMERA)

        ensureLegacyWriteIfNeeded()
    }

    private fun ensureLegacyWriteIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                reqWriteLegacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun startCameraSafely() {
        if (isBound) return
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get().also { cameraProvider = it }

            val preview = Preview.Builder().build().also { p ->
                // 여기서는 PreviewUseCase에 SurfaceProvider를 연결
                p.setSurfaceProvider(previewView.surfaceProvider)
                previewUseCase = p
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(90)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                isBound = true
            } catch (e: Exception) {
                Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching {
            // ✅ PreviewUseCase에서 SurfaceProvider 분리 (null 허용)
            previewUseCase?.setSurfaceProvider(null)
            cameraProvider?.unbindAll()
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCameraSafely()
    }

    override fun onStop() {
        stopCamera()
        super.onStop()
    }

    private fun takePhotoFast() {
        val capture = imageCapture ?: return
        runCatching { shutterSound.play(MediaActionSound.SHUTTER_CLICK) }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@CameraCaptureActivity, "촬영 실패: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bytes = imageToJpegBytes(image)
                        val tempFile = saveTemp(bytes)

                        // 화면 전환 전에 스트림 끊기 (Surface 타이밍 충돌 방지)
                        runOnUiThread { stopCamera() }

                        val intent = Intent(this@CameraCaptureActivity, PhotoReviewActivity::class.java)
                            .putExtra("temp_path", tempFile.absolutePath)
                        runOnUiThread { reviewLauncher.launch(intent) }
                    } catch (t: Throwable) {
                        runOnUiThread {
                            Toast.makeText(this@CameraCaptureActivity, "임시 저장 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        image.close()
                    }
                }
            }
        )
    }

    private fun imageToJpegBytes(image: ImageProxy): ByteArray {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun saveTemp(bytes: ByteArray): File {
        val name = "lingo_tmp_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, name)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun moveTempToGallery(temp: File): Uri? {
        return try {
            val name =
                "lingo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lingo")
                }
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    temp.inputStream().use { it.copyTo(out) }
                }
                lastSavedUri = uri
            }
            uri
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        if (::shutterSound.isInitialized) runCatching { shutterSound.release() }
        lastSavedUri?.let { runCatching { contentResolver.delete(it, null, null) } }
    }
}

/*
package com.example.lingo.ui.translation

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.lingo.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: DocumentOverlayView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val reqCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    private val reviewLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // PhotoReviewActivity에서 OK로 돌아오면, 최종 URI를 상위(TranslationDocImagesActivity)로 전달
                val finalUri = result.data?.data
                if (finalUri != null) {
                    setResult(RESULT_OK, android.content.Intent().setData(finalUri))
                    finish()
                }
            } else {
                // RESULT_CANCELED: 다시 촬영
                // (원한다면 직전 저장 이미지를 정리)
                lastSavedUri?.let { contentResolver.delete(it, null, null) }
                lastSavedUri = null
            }
        }

    private var lastSavedUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_capture)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)

        findViewById<View>(R.id.btnShutter).setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 권한 체크
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            reqCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private val reqWriteLegacy =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    private fun ensureLegacyWriteIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // API 28 이하
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                reqWriteLegacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        val name = "lingo_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 갤러리의 Pictures/Lingo 폴더로 저장
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lingo")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, // collection URI
            contentValues                                 // pre-insert 없이 contentValues 전달
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    // 어떤 예외인지 확인할 수 있게 로그/토스트에 메시지 표시
                    android.util.Log.e("CameraX", "Save failed: ${exc.imageCaptureError}", exc)
                    Toast.makeText(this@CameraCaptureActivity, "촬영 실패: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val saved = output.savedUri
                    if (saved != null) {
                        lastSavedUri = saved
                        val intent = Intent(this@CameraCaptureActivity, PhotoReviewActivity::class.java)
                            .setData(saved)
                        reviewLauncher.launch(intent)
                    } else {
                        Toast.makeText(this@CameraCaptureActivity, "저장 실패(URI 없음)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }
}
*/
