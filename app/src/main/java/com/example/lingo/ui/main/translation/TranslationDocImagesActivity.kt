package com.example.lingo.ui.main.translation

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingo.R
import com.example.lingo.data.remote.RetrofitClient
import com.example.lingo.data.repository.UploadRepository
import com.example.lingo.data.repository.UploadResult
import com.example.lingo.ui.main.translation.camera.CameraCaptureActivity
import com.example.lingo.ui.main.translation.camera.PhotoCaptureGuideActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class TranslationDocImagesActivity : AppCompatActivity() {

    private val photos = mutableListOf<Uri>()
    private lateinit var adapter: TranslationPhotoAdapter

    private var pendingCameraUri: Uri? = null
    private var expectedPageCount: Int = 1
    private var docTitle: String = "문서"
    private var requestDtoJson: String? = null  // TranslationDocActivity에서 전달받은 원본 DTO JSON
    private var normalizedRequestDtoJson: String? = null // 스펙에 맞춰 정규화한 JSON

    // 업로드 리포지토리 (S3 presigned PUT 포함)
    private val uploadRepository: UploadRepository by lazy {
        UploadRepository(RetrofitClient.api)
    }

    // 카메라 촬영
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                pendingCameraUri?.let {
                    photos.add(it)
                    adapter.notifyItemInserted(photos.lastIndex)
                }
            }
        }

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
            else Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }

    // 가이드 → 카메라
    private val guideLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val i = Intent(this, CameraCaptureActivity::class.java)
                    .putExtra("doc_title", docTitle)
                cameraLauncher.launch(i)
            }
        }

    // 카메라 결과(내부 촬영 UI에서 URI 반환)
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                photos.add(uri)
                adapter.notifyItemInserted(photos.lastIndex)
            }
        }

    // 문서(PDF/이미지) 선택
    private val pickDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val type = contentResolver.getType(uri) ?: ""
            when {
                type.startsWith("image/") ||
                        uri.toString().endsWith(".jpg", true) ||
                        uri.toString().endsWith(".jpeg", true) ||
                        uri.toString().endsWith(".png", true) -> {
                    photos.add(uri)
                    adapter.notifyItemInserted(photos.lastIndex)
                }
                type == "application/pdf" || uri.toString().endsWith(".pdf", true) -> {
                    val added = addPdfPagesAsImages(uri)
                    if (added > 0) {
                        adapter.notifyItemRangeInserted(photos.size - added, added)
                        toast("${added}페이지 추가됨")
                    } else {
                        toast("PDF 처리에 실패했습니다.")
                    }
                }
                else -> toast("지원하지 않는 형식입니다.")
            }
        }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        pickDocument.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translation_doc_images)

        // 전달값
        docTitle = intent.getStringExtra("doc_title") ?: "부동산등기부등본 - 건물"
        expectedPageCount = intent.getIntExtra("expected_page_count", 1)
        requestDtoJson = intent.getStringExtra("request_dto_json")

        findViewById<MaterialToolbar>(R.id.topToolbar)?.apply {
            title = docTitle
            setNavigationOnClickListener { finish() }
        }

        val rvPhotos = findViewById<RecyclerView>(R.id.rvPhotos)
        val btnAddPhoto = findViewById<View>(R.id.btnAddPhoto)
        val btnNext = findViewById<View>(R.id.btnNext)

        adapter = TranslationPhotoAdapter(photos) { pos ->
            photos.removeAt(pos)
            adapter.notifyItemRemoved(pos)
            adapter.notifyItemRangeChanged(pos, photos.size - pos)
        }
        rvPhotos.layoutManager = LinearLayoutManager(this)
        rvPhotos.adapter = adapter

        btnAddPhoto.setOnClickListener { showAddDialog() }

        // 로딩 없이 바로 다음 화면으로 이동
        btnNext.setOnClickListener {
            if (photos.size != expectedPageCount) {
                showCountMismatchDialog()
                return@setOnClickListener
            }
            if (photos.isEmpty()) {
                toast("이미지를 추가해 주세요.")
                return@setOnClickListener
            }
            // 1) DTO 정규화
            if (!normalizeRequestDtoOrShowError()) return@setOnClickListener

            // 2) 업로드는 다음 화면에서 하므로, 로컬 URI/정규화 DTO만 전달
            goToTranslationProgressWithoutUpload()
        }
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_photo, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        view.findViewById<View>(R.id.optCamera).setOnClickListener {
            dialog.dismiss()
            val i = Intent(this, PhotoCaptureGuideActivity::class.java)
                .putExtra("doc_title", docTitle)
            guideLauncher.launch(i)
        }
        view.findViewById<View>(R.id.optGallery).setOnClickListener {
            dialog.dismiss()
            openGallery()
        }
        view.findViewById<View>(R.id.optDocument).setOnClickListener {
            dialog.dismiss()
            openDocumentPicker()
        }
        dialog.show()
    }

    private fun showCountMismatchDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_count_mismatch, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirm).setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun ensureCameraThenOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val name = "lingo_doc_${System.currentTimeMillis()}.jpg"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv
            )
        } else {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        }
        pendingCameraUri = uri
        if (uri != null) {
            takePicture.launch(uri)
        } else {
            toast("카메라를 열 수 없습니다.")
        }
    }

    /**
     *  스웨거 스펙 반영:
     *  - DocumentType: [REAL_ESTATE_REGISTRY, ENROLLMENT_CERTIFICATE, FAMILY_RELATIONSHIP_CERTIFICATE]
     *  - Country: [USA, JAPAN, CHINA, VIETNAM]
     *  - Language: [ENGLISH, JAPANESE, CHINESE, VIETNAMESE]
     *  - IssuanceChannel: [GOVERNMENT_AGENCY, INTERNET, KIOSK, OTHER]
     *  - Orientation: [PORTRAIT, LANDSCAPE]
     *
     *  requestDtoJson을 파싱 후, 위 허용값으로 정규화하고 totalPages는 실제 페이지 수로 맞춘다.
     */
    private fun normalizeRequestDtoOrShowError(): Boolean {
        val raw = requestDtoJson ?: run {
            toast("요청 정보가 없습니다.")
            return false
        }

        return try {
            val src = Gson().fromJson(raw, RawDocumentRequestDTO::class.java)

            val normalized = src.copy(
                documentType = normalizeDocType(src.documentType) ?: return errorEnum("문서 유형", src.documentType),
                country = normalizeCountry(src.country) ?: return errorEnum("문서 제출 국가", src.country),
                language = normalizeLanguage(src.language) ?: return errorEnum("번역 언어", src.language),
                issuanceChannel = normalizeIssuance(src.issuanceChannel) ?: return errorEnum("발급 경로", src.issuanceChannel),
                orientation = normalizeOrientation(src.orientation) ?: return errorEnum("문서 방향", src.orientation),
                totalPages = photos.size.toLong() // 실제 선택된 페이지수로 강제 일치
            )

            normalizedRequestDtoJson = Gson().toJson(normalized)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            toast("요청 정보 파싱에 실패했습니다.")
            false
        }
    }

    private fun errorEnum(label: String, value: String?): Boolean {
        toast("$label 값이 스펙과 다릅니다: $value")
        return false
    }

    // ---- Enum 정규화 유틸 ----
    private val allowedDocTypes = setOf(
        "REAL_ESTATE_REGISTRY",
        "ENROLLMENT_CERTIFICATE",
        "FAMILY_RELATIONSHIP_CERTIFICATE"
    )
    private val docTypeSynonyms = mapOf(
        "FAMILY" to "FAMILY_RELATIONSHIP_CERTIFICATE",
        "FAMILY_RELATIONSHIP" to "FAMILY_RELATIONSHIP_CERTIFICATE",
        "ENROLLMENT" to "ENROLLMENT_CERTIFICATE",
        "REAL_ESTATE" to "REAL_ESTATE_REGISTRY",
        "REAL_ESTATE_REGISTER" to "REAL_ESTATE_REGISTRY"
    )
    private fun normalizeDocType(v: String?): String? {
        if (v.isNullOrBlank()) return null
        val up = v.trim().uppercase()
        val mapped = docTypeSynonyms[up] ?: up
        return if (allowedDocTypes.contains(mapped)) mapped else null
    }

    private val allowedCountries = setOf("USA", "JAPAN", "CHINA", "VIETNAM")
    private fun normalizeCountry(v: String?): String? {
        if (v.isNullOrBlank()) return null
        val up = v.trim().uppercase()
        return if (allowedCountries.contains(up)) up else null
    }

    private val allowedLanguages = setOf("ENGLISH", "JAPANESE", "CHINESE", "VIETNAMESE")
    private fun normalizeLanguage(v: String?): String? {
        if (v.isNullOrBlank()) return null
        val up = v.trim().uppercase()
        return if (allowedLanguages.contains(up)) up else null
    }

    private val allowedIssuance = setOf("GOVERNMENT_AGENCY", "INTERNET", "KIOSK", "OTHER")
    private val issuanceSynonyms = mapOf("GOVERNMENT" to "GOVERNMENT_AGENCY")
    private fun normalizeIssuance(v: String?): String? {
        if (v.isNullOrBlank()) return null
        val up = v.trim().uppercase()
        val mapped = issuanceSynonyms[up] ?: up
        return if (allowedIssuance.contains(mapped)) mapped else null
    }

    private val allowedOrientation = setOf("PORTRAIT", "LANDSCAPE")
    private fun normalizeOrientation(v: String?): String? {
        if (v.isNullOrBlank()) return null
        val up = v.trim().uppercase()
        return if (allowedOrientation.contains(up)) up else null
    }

    private fun provideApiAuto(ctx: android.content.Context): com.example.lingo.data.remote.ApiService {
        val access = com.example.lingo.util.TokenManager.get(ctx).getAccessToken()
        return if (access.isNullOrBlank()) {
            com.example.lingo.data.remote.RetrofitClient.api          // 비로그인 → 헤더 없음
        } else {
            com.example.lingo.data.remote.RetrofitClient.apiWithAuth(ctx) // 로그인 → Authorization 자동 첨부
        }
    }

    private fun goToTranslationProgress(
        uploadedS3Keys: List<String>,
        originalFileNames: List<String>
    ) {
        val intent = Intent(this, TranslationInProgressActivity::class.java).apply {
            putExtra("expected_page_count", expectedPageCount)
            putParcelableArrayListExtra("photos", ArrayList(photos))
            // 서버용: fileNames 자리에 s3Key들을 사용할 수 있게 전달
            putStringArrayListExtra("uploaded_s3_keys", ArrayList(uploadedS3Keys))
            // (선택) 표시/디버깅용
            putStringArrayListExtra("uploaded_file_names", ArrayList(originalFileNames))
            putExtra("doc_title", docTitle)
            // 스펙에 맞춰 정규화된 DTO JSON
            putExtra("normalized_request_dto_json", normalizedRequestDtoJson)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    /** PDF 각 페이지를 이미지를 캐시에 저장 후 photos에 추가 */
    private fun addPdfPagesAsImages(pdfUri: Uri): Int {
        var added = 0
        try {
            contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            // PDF 원본 크기 기준으로 비트맵 생성
                            val width = page.width
                            val height = page.height

                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                            // 전체 영역을 렌더링
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageUri = saveBitmapToCacheAndGetUri(
                                bmp, "picked_pdf_${System.currentTimeMillis()}_$i.jpg"
                            )
                            bmp.recycle()

                            if (pageUri != null) {
                                photos.add(pageUri)
                                added++
                            }
                        }
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            toast("메모리가 부족합니다. PDF 해상도를 줄여주세요.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return added
    }

    private fun saveBitmapToCacheAndGetUri(bmp: Bitmap, fileName: String): Uri? {
        return try {
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            androidx.core.content.FileProvider.getUriForFile(
                this, "${applicationContext.packageName}.fileprovider", file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 갤러리 (13+)
    private val pickFromGallery13Plus =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            uri?.let {
                photos.add(it)
                adapter.notifyItemInserted(photos.lastIndex)
            }
        }

    // 갤러리 (레거시)
    private val pickFromGalleryLegacy =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    photos.add(uri)
                    adapter.notifyItemInserted(photos.lastIndex)
                }
            }
        }

    private fun openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pickFromGallery13Plus.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }
            pickFromGalleryLegacy.launch(intent)
        }
    }

    private fun goToTranslationProgressWithoutUpload() {
        val intent = Intent(this, TranslationInProgressActivity::class.java).apply {
            putExtra("expected_page_count", expectedPageCount)
            putParcelableArrayListExtra("photos", ArrayList(photos)) // 로컬 URI 전달
            putExtra("doc_title", docTitle)
            putExtra("normalized_request_dto_json", normalizedRequestDtoJson)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- 내부 DTO (정규화용) ----------
    data class RawDocumentRequestDTO(
        val documentType: String,
        val totalPages: Long,
        val country: String,
        val language: String,
        val issuanceChannel: String,
        val orientation: String
    )
}
