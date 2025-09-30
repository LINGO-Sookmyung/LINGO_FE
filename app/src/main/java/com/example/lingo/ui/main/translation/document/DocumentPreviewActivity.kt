package com.example.lingo.ui.main.translation.document

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R
import com.example.lingo.data.local.MyDocumentsStore
import com.example.lingo.data.remote.RetrofitClient
import com.example.lingo.data.repository.UploadRepository
import com.example.lingo.data.repository.UploadResult
import com.example.lingo.ui.main.MainActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.lingo.util.TokenManager

class DocumentPreviewActivity : AppCompatActivity() {

    private var documentUri: Uri? = null
    private var mimeType: String? = null
    private var docTitle: String = "문서"
    private var downloadFilename: String? = null
    private var presignedUrl: String? = null

    private val uploadRepo by lazy { UploadRepository(RetrofitClient.api) }

    // 백엔드 호스트만 인증 부착 (S3 등 외부 프리사인드에는 미부착)
    private val backendHosts = setOf("54.215.109.237") // 필요 시 도메인 추가
    private val rawHttp by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                val builder = req.newBuilder()
                    .header("Accept", "application/octet-stream, application/pdf, image/*, */*")
                    .header("User-Agent", "LingoApp/1.0 (Android)")

                if (backendHosts.contains(host)) {
                    val token = TokenManager.get(this@DocumentPreviewActivity).getAccessToken()
                    if (!token.isNullOrBlank()) {
                        builder.header("Authorization", "Bearer $token")
                    }
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    // [PREVIEW-OFF] 미리보기 뷰 참조 주석 처리
    // private lateinit var ivPreview: ImageView
    // private lateinit var tvPlaceholder: TextView
    private lateinit var btnDownload: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_preview)

        docTitle = intent.getStringExtra("doc_title") ?: "문서"
        downloadFilename = intent.getStringExtra("download_filename")
        presignedUrl = intent.getStringExtra("presigned_url")

        // ── Toolbar 확보 ──
        var toolbar: MaterialToolbar? = findViewById(R.id.topToolbar)
        if (toolbar == null) toolbar = findViewById(R.id.topAppBar)
        if (toolbar == null) {
            val appBar = layoutInflater.inflate(R.layout.view_top_appbar, null)
            addContentView(
                appBar,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            toolbar = appBar.findViewById(R.id.topToolbar)
            val content = findViewById<ViewGroup>(android.R.id.content)
            val root = content.getChildAt(0)
            appBar.post {
                root?.setPadding(
                    root.paddingLeft,
                    root.paddingTop + appBar.height,
                    root.paddingRight,
                    root.paddingBottom
                )
            }
        }
        toolbar?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            supportActionBar?.title = docTitle
            tb.title = docTitle
            tb.setNavigationOnClickListener { finish() }
        }

        // [PREVIEW-OFF] 미리보기 뷰 바인딩 주석 처리
        // ivPreview = findViewById(R.id.ivPreview)
        // tvPlaceholder = findViewById(R.id.tvPreviewPlaceholder)
        btnDownload = findViewById(R.id.btnDownload)

        // 우선순위: documentUri → presigned_url → download_filename
        documentUri = intent.getParcelableExtra("documentUri")
        mimeType = intent.getStringExtra("mimeType")

        if (documentUri != null) {
            // [PREVIEW-OFF]
            // val ok = renderPreview(ivPreview)
            // tvPlaceholder.alpha = if (ok) 0f else 1f
        } else {
            startDownloadIfNeeded()
        }

        btnDownload.setOnClickListener {
            val result = saveToDownloads()
            if (result != null) {
                val (savedUri, displayName) = result
                val resolvedMime = mimeType ?: contentResolver.getType(savedUri) ?: "application/octet-stream"
                MyDocumentsStore.add(
                    this,
                    MyDocumentsStore.Item(
                        uri = savedUri.toString(),
                        displayName = displayName,
                        mimeType = resolvedMime,
                        docTitle = docTitle,
                        savedAt = System.currentTimeMillis()
                    )
                )
                Toast.makeText(this, "다운로드가 완료되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "다운로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnNext).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }

    /** presigned GET URL로 실제 파일을 캐시에 내려받고 미리보기 */
    private fun startDownloadIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val finalUrl = when {
                    // 1) 인텐트로 직접 온 프리사인드 URL
                    !presignedUrl.isNullOrBlank() -> presignedUrl!!.trim()

                    // 2) 파일명으로 백엔드에 URL 발급 요청 → 응답 path가 절대/상대 모두 가능하니 보정
                    !downloadFilename.isNullOrBlank() -> {
                        when (val r = uploadRepo.getDownloadUrl(downloadFilename!!)) {
                            is UploadResult.Success -> normalizeToAbsoluteUrl(r.data.path)
                            is UploadResult.Failure -> {
                                showToastOnMain("다운로드 URL 발급 실패: ${r.message ?: "알 수 없는 오류"}")
                                return@launch
                            }
                        }
                    }

                    else -> {
                        showToastOnMain("미리보기할 파일 정보가 없습니다.")
                        return@launch
                    }
                }

                val dl = finalUrl ?: run {
                    showToastOnMain("URL 생성 실패")
                    return@launch
                }

                val result = downloadToCache(dl)
                if (result == null) {
                    // 실패 사유를 더 알려주기 위해 한 번 더 HEAD 시도로 상태 받기 (선택)
                    showToastOnMain("파일 다운로드 실패 (URL: ${safeShort(dl)})")
                    return@launch
                }

                val (cacheUri, guessedMime) = result
                documentUri = cacheUri
                mimeType = guessedMime

                // [PREVIEW-OFF] UI에 미리보기 반영 제거
                // runOnUiThread {
                //     val ok = renderPreview(ivPreview)
                //     tvPlaceholder.alpha = if (ok) 0f else 1f
                // }
            } catch (e: Exception) {
                e.printStackTrace()
                showToastOnMain("다운로드 중 오류: ${e.message ?: "알 수 없는 오류"}")
            }
        }
    }

    /** presigned GET URL을 받아 캐시에 저장하고 (Uri, mime) 반환 */
    private fun downloadToCache(url: String): Pair<Uri, String>? {
        val req = Request.Builder().url(url).get().build()
        rawHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val peek = try { resp.body?.string()?.take(200) } catch (_: Exception) { null }
                showToastOnMain("HTTP ${resp.code} ${resp.message}${if (!peek.isNullOrBlank()) " · $peek" else ""}")
                return null
            }
            val body = resp.body ?: return null
            val bytes = body.bytes()
            val contentType = resp.header("Content-Type") ?: "application/octet-stream"

            val ext = when {
                contentType.contains("pdf", true) || url.contains(".pdf", true) -> "pdf"
                contentType.startsWith("image/", true) -> "jpg"
                else -> "bin"
            }

            val file = File(cacheDir, "preview_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { it.write(bytes) }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            return uri to contentType
        }
    }

    /** 파일 저장: 성공 시 (Uri, 파일명) 반환 */
    private fun saveToDownloads(): Pair<Uri, String>? {
        val src = documentUri ?: return null
        val resolvedMime = mimeType ?: contentResolver.getType(src) ?: "application/octet-stream"

        val ext = when {
            resolvedMime.contains("pdf", true) || src.toString().endsWith(".pdf", true) -> "pdf"
            resolvedMime.startsWith("image/", true) -> "jpg"
            else -> "bin"
        }
        val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "lingo_${sanitize(docTitle)}_$now.$ext"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val dstUri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return null

                contentResolver.openOutputStream(dstUri)?.use { out ->
                    contentResolver.openInputStream(src)?.use { it.copyTo(out) }
                }

                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(dstUri, values, null, null)

                dstUri to filename
            } else {
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloads.exists()) downloads.mkdirs()
                val dst = File(downloads, filename)
                contentResolver.openInputStream(src)?.use { input ->
                    FileOutputStream(dst).use { output -> input.copyTo(output) }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    dst
                )
                uri to filename
            }
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^0-9A-Za-z가-힣._-]"), "_")

    // [PREVIEW-OFF] 미리보기 렌더링 관련 메서드 전체 주석 처리
    /*
    private fun renderPreview(iv: ImageView): Boolean {
        val uri = documentUri ?: return false
        val type = mimeType ?: contentResolver.getType(uri) ?: ""
        return try {
            when {
                type.startsWith("image/") -> {
                    iv.setImageURI(uri); true
                }
                type.equals("application/pdf", true) || uri.toString().endsWith(".pdf", true) -> {
                    val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return false
                    val bmp = renderPdfFirstPage(pfd)
                    pfd.close()
                    if (bmp != null) {
                        iv.setImageBitmap(bmp); true
                    } else false
                }
                else -> {
                    iv.setImageURI(uri); true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    private fun renderPdfFirstPage(pfd: ParcelFileDescriptor): Bitmap? = try {
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount <= 0) return null
            renderer.openPage(0).use { page ->
                val scale = 2
                val bmp = Bitmap.createBitmap(
                    page.width * scale,
                    page.height * scale,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    } catch (e: Exception) {
        e.printStackTrace(); null
    }
    */

    private fun showToastOnMain(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ====== 헬퍼들 ======
    private fun normalizeToAbsoluteUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val trimmed = path.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            // RetrofitClient.BASE_URL 과 합쳐야 하지만, 접근 불가 시 하드코드 (필요 시 수정)
            val base = "http://54.215.109.237:8081"
            if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
        }
    }

    private fun safeShort(s: String, n: Int = 120): String =
        if (s.length <= n) s else s.take(n) + "…"
}
