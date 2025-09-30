package com.example.lingo.ui.main.translation

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R
import com.example.lingo.core.network.ApiService
import com.example.lingo.core.network.RetrofitClient
import com.example.lingo.data.repository.UploadRepository
import com.example.lingo.data.repository.UploadResult
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import retrofit2.Response
import com.example.lingo.data.local.TokenManager // 자동 클라이언트 선택에 사용

class TranslationInProgressActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var progressBar: ProgressBar
    private var topBar: MaterialToolbar? = null

    private lateinit var docTitle: String
    private var expectedPageCount: Int = 1

    // 입력 상태
    private var photoUris: ArrayList<Uri> = arrayListOf()
    private var uploadedS3Keys: ArrayList<String> = arrayListOf()
    private var uploadedFileNamesLegacy: ArrayList<String> = arrayListOf()

    private var requestDto: RequestDTO? = null

    // 업로드 리포지토리(로그인 여부에 따라 자동 선택)
    private lateinit var uploadRepository: UploadRepository

    // 일시정지/재개 상태
    private enum class Phase { IDLE, UPLOAD, PREVIEW }
    private var phase: Phase = Phase.IDLE
    private var currentJob: Job? = null
    private var isPausedByDialog: Boolean = false
    private var hasCompleted: Boolean = false

    // 업로드 상태
    private var nextUploadIndex: Int = 0
    private val presignedPaths: MutableList<String> = mutableListOf()
    private val presignedS3Keys: MutableList<String> = mutableListOf()

    // 다이얼로그 누수 방지
    private var exitDialog: android.app.Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_generating)

        // ✅ 로그인 강제 차단 제거 (비로그인도 진행 가능)
        // 업로드/미리보기에서 사용할 API 클라이언트 자동 선택
        uploadRepository = UploadRepository(provideApiAuto(this))

        progressBar = findViewById(R.id.progress)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)

        // 1) 인텐트 값 로드 + 로그
        docTitle = intent.getStringExtra("doc_title") ?: "부동산등기부등본 - 건물"
        expectedPageCount = intent.getIntExtra("expected_page_count", 1)
        android.util.Log.d("TIPA", "EXTRA doc_title='$docTitle', expected_page_count=$expectedPageCount")

        // 2) 툴바
        var toolbar = findViewById<MaterialToolbar?>(R.id.topToolbar)
        if (toolbar == null) {
            val appBarView = layoutInflater.inflate(R.layout.view_top_appbar, null)
            addContentView(
                appBarView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            toolbar = appBarView.findViewById(R.id.topToolbar)

            val content = findViewById<ViewGroup>(android.R.id.content)
            val root = content.getChildAt(0)
            appBarView.post {
                root?.setPadding(
                    root.paddingLeft,
                    root.paddingTop + appBarView.height,
                    root.paddingRight,
                    root.paddingBottom
                )
            }
        }
        topBar = toolbar
        topBar?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            supportActionBar?.title = docTitle
            tb.title = docTitle
            android.util.Log.d("TIPA", "Toolbar='${tb.title}', ActionBar='${supportActionBar?.title}'")
            tb.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        // 3) 입력들
        photoUris = intent.getParcelableArrayListExtra("photos") ?: arrayListOf()
        uploadedS3Keys = intent.getStringArrayListExtra("uploaded_s3_keys") ?: arrayListOf()
        uploadedFileNamesLegacy = intent.getStringArrayListExtra("uploaded_file_names") ?: arrayListOf()

        // 레거시 값은 "정말 s3Key처럼 보일 때만" 승격
        if (uploadedS3Keys.isEmpty() && uploadedFileNamesLegacy.isNotEmpty()) {
            val looksLikeS3Key = uploadedFileNamesLegacy.all { it.contains('/') && it.contains('.') }
            if (looksLikeS3Key) {
                uploadedS3Keys = ArrayList(uploadedFileNamesLegacy)
            } else {
                android.util.Log.w("TIPA", "uploaded_file_names 가 s3Key처럼 보이지 않아 무시: $uploadedFileNamesLegacy")
            }
        }

        // 4) DTO 구성
        val normalizedJson = intent.getStringExtra("normalized_request_dto_json")
        val rawJson = intent.getStringExtra("request_dto_json")
        requestDto = runCatching {
            when {
                !normalizedJson.isNullOrBlank() -> Gson().fromJson(normalizedJson, RequestDTO::class.java)
                !rawJson.isNullOrBlank()        -> normalizeForSpec(Gson().fromJson(rawJson, RequestDTO::class.java))
                else -> null
            }
        }.getOrNull()

        if (requestDto == null) {
            val documentType = intent.getStringExtra("document_type") ?: "REAL_ESTATE_REGISTRY"
            val country      = intent.getStringExtra("country") ?: "USA"
            val language     = intent.getStringExtra("language") ?: "ENGLISH"
            val issuance     = intent.getStringExtra("issuance_channel") ?: "INTERNET"
            val orientation  = intent.getStringExtra("orientation") ?: "PORTRAIT"
            requestDto = RequestDTO(
                documentType = documentType,
                totalPages   = maxOf(expectedPageCount, uploadedS3Keys.size).coerceAtLeast(1),
                country = country,
                language = language,
                issuanceChannel = issuance,
                orientation = orientation
            ).let { normalizeForSpec(it) }
        }

        // 5) 흐름 시작
        if (uploadedS3Keys.isEmpty()) {
            if (photoUris.isEmpty()) {
                toast("업로드할 파일이 없습니다.")
                finish()
                return
            }
            setLoading("파일 업로드 중입니다.", "잠시만 기다려 주세요.")
            startOrResumeWork()
        } else {
            requestDto = requestDto?.copy(totalPages = uploadedS3Keys.size.coerceAtLeast(1))
            setLoading("문서 분석 중입니다.", "잠시만 기다려 주세요.")
            phase = Phase.PREVIEW
            startOrResumeWork()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showExitDuringTranslateDialog(
                        onMove = { cancelCurrentWork(); finish() },
                        onStay = { resumeWorkIfNeeded() }
                    )
                }
            }
        )
    }

    // ------------------- 로그인 여부에 따라 API 자동 선택 -------------------
    private fun provideApiAuto(ctx: android.content.Context): ApiService {
        val access = TokenManager.get(ctx).getAccessToken()
        return if (access.isNullOrBlank()) {
            RetrofitClient.api           // 비로그인 → 헤더 없음
        } else {
            RetrofitClient.apiWithAuth(ctx) // 로그인 → Authorization 자동 첨부
        }
    }

    // ------------------- 일시정지/재개 제어 -------------------
    private fun startOrResumeWork() {
        if (hasCompleted || isFinishing || isDestroyed) return
        when {
            phase == Phase.PREVIEW -> {
                if (uploadedS3Keys.isEmpty()) {
                    toast("업로드된 파일이 없습니다.")
                    finish(); return
                }
                val dto = requestDto ?: run {
                    toast("요청 정보가 부족합니다.")
                    finish(); return
                }
                startPreview(dto)
            }
            else -> startUpload()
        }
    }

    private fun pauseForDialog() {
        if (currentJob?.isActive == true) {
            isPausedByDialog = true
            currentJob?.cancel(CancellationException("Paused by exit dialog"))
        }
        setPausedUi()
    }

    private fun resumeWorkIfNeeded() {
        if (!hasCompleted && isPausedByDialog) {
            isPausedByDialog = false
            setLoading("처리 중입니다.", "잠시만 기다려 주세요.")
            startOrResumeWork()
        }
    }

    private fun cancelCurrentWork() {
        isPausedByDialog = false
        currentJob?.cancel(CancellationException("User chose to leave"))
        currentJob = null
    }

    // ------------------- 업로드 로직 -------------------
    private fun startUpload() {
        phase = Phase.UPLOAD
        if (requestDto == null) return

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                // 1) Presigned URL 발급 (없을 때만)
                if (presignedPaths.isEmpty() || presignedS3Keys.isEmpty()) {
                    val aliases = (1..photoUris.size).map { "$it.jpg" }
                    when (val issued = uploadRepository.getUploadUrls(aliases)) {
                        is UploadResult.Failure -> {
                            showToast("업로드 URL 발급 실패: ${issued.message ?: "알 수 없는 오류"}")
                            finishOnMain(); return@launch
                        }
                        is UploadResult.Success -> {
                            presignedPaths.clear()
                            presignedS3Keys.clear()
                            issued.data.forEach { item ->
                                presignedPaths.add(item.path)
                                presignedS3Keys.add(item.s3Key)
                            }

                            // A단계 로그: 발급된 presigned와 s3Key
                            android.util.Log.d(
                                "TIPA",
                                "A) issued presigned count=${issued.data.size}\n" +
                                        "paths=$presignedPaths\n" +
                                        "s3keys=$presignedS3Keys"
                            )

                            if (presignedPaths.size != photoUris.size || presignedS3Keys.size != photoUris.size) {
                                showToast("발급 개수와 파일 개수가 다릅니다.")
                                finishOnMain(); return@launch
                            }
                        }
                    }
                }

                // 2) 업로드: 인덱스 고정 매핑으로 중복/순서 보장
                val uploadedS3KeysTemp = MutableList(photoUris.size) { "" }

                // nextUploadIndex부터 재개
                while (nextUploadIndex < photoUris.size) {
                    ensureActive()
                    val i = nextUploadIndex
                    val uri = photoUris[i]
                    val presignedUrl = presignedPaths[i]

                    // B단계 로그: 실제 업로드 시 어떤 presignedUrl을 쓰는지
                    android.util.Log.d(
                        "TIPA",
                        "B) upload index=$i uri=$uri\npresignedUrl(len)=${presignedUrl.length}\ns3Key=${presignedS3Keys[i]}"
                    )

                    // 업로드 시 Content-Type을 image/jpeg로 강제
                    when (val up = uploadRepository.putToPresignedUrl(
                        this@TranslationInProgressActivity,
                        uri,
                        presignedUrl,
                        explicitMime = "image/jpeg"
                    )) {
                        is UploadResult.Success -> {
                            uploadedS3KeysTemp[i] = presignedS3Keys[i]
                            nextUploadIndex++  // 다음 파일로
                            android.util.Log.d("TIPA", "upload ok index=$i -> ${presignedS3Keys[i]}")
                        }
                        is UploadResult.Failure -> {
                            showToast("업로드 실패: ${up.message ?: "알 수 없는 오류"}")
                            finishOnMain(); return@launch
                        }
                    }
                }

                // 3) 업로드 완료 → S3키 확정
                val finalized = uploadedS3KeysTemp.filter { it.isNotBlank() }
                if (finalized.size != photoUris.size) {
                    showToast("일부 파일 업로드가 완료되지 않았습니다.")
                    finishOnMain(); return@launch
                }
                uploadedS3Keys.clear()
                uploadedS3Keys.addAll(finalized)

                // PREVIEW로 전환
                requestDto = requestDto?.copy(totalPages = uploadedS3Keys.size.coerceAtLeast(1))
                withContext(Dispatchers.Main) {
                    setLoading("문서 분석 중입니다.", "잠시만 기다려 주세요.")
                }
                phase = Phase.PREVIEW
                startPreview(requestDto!!)
            } catch (ce: CancellationException) {
                if (isPausedByDialog) setPausedUi()
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("업로드 중 오류: ${e.message}")
                finishOnMain()
            }
        }
    }

    // ------------------- 번역 미리보기 로직 -------------------
    private fun startPreview(dto: RequestDTO) {
        // C단계 로그: 서버에 전달할 fileNames(=s3Key) 검증
        android.util.Log.d(
            "TIPA",
            "C) preview request -> totalPages=${dto.totalPages}, fileNames=$uploadedS3Keys"
        )

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                val body = TranslatePreviewRequest(requestDTO = dto, fileNames = uploadedS3Keys)

                // ✅ 로그인 여부에 따라 자동 선택한 클라이언트 사용
                val resp: Response<TranslatePreviewResponse> =
                    provideApiAuto(this@TranslationInProgressActivity).translatePreview(body)

                if (resp.isSuccessful) {
                    val data = resp.body()
                    if (data == null) {
                        showToast("서버 응답이 비어 있습니다.")
                        finishOnMain(); return@launch
                    }

                    android.util.Log.d(
                        "TIPA",
                        "preview response <- rawDocumentId=${data.rawDocumentId}, path=${data.path}, docType=${data.docType}, lang=${data.lang}"
                    )

                    val resultJsonStr = data.result?.toString() ?: "{}"
                    withContext(Dispatchers.Main) {
                        hasCompleted = true
                        val next = Intent(this@TranslationInProgressActivity, TranslationResultActivity::class.java).apply {
                            putExtra("doc_title", docTitle)
                            putExtra("rawDocumentId", data.rawDocumentId)
                            putExtra("resultJson", resultJsonStr)
                            putExtra("orientation_enum", dto.orientation)
                            putExtra("request_dto_json", Gson().toJson(dto))
                        }
                        startActivity(next)
                        finish()
                    }
                } else {
                    val err = resp.errorBody()?.string()
                    showToast("번역 미리보기 실패 (${resp.code()}) ${err?.take(200) ?: ""}".trim())
                    finishOnMain()
                }
            } catch (ce: CancellationException) {
                if (isPausedByDialog) setPausedUi()
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("요청 중 오류: ${e.message}")
                finishOnMain()
            }
        }
    }

    // ------------------- 뒤로가기 모달 -------------------
    private fun showExitDuringTranslateDialog(
        onMove: () -> Unit,
        onStay: () -> Unit
    ) {
        if (isFinishing || isDestroyed || hasCompleted) return

        pauseForDialog()

        val view = layoutInflater.inflate(R.layout.dialog_exit_translate, null)
        exitDialog = android.app.Dialog(this, R.style.TransparentDialog).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(view)
            setCancelable(false)
            setCanceledOnTouchOutside(false)

            val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
            val btnClose  = view.findViewById<ImageButton>(R.id.btnClose)
            val btnCancel = view.findViewById<Button>(R.id.btnCancel)
            val btnMove   = view.findViewById<Button>(R.id.btnMove)

            tvMessage.text = "번역 중인 내용이 사라질 수 있습니다.\n계속 진행하시겠어요?"
            btnCancel.text = "취소"
            btnMove.text   = "이동"

            btnClose.setOnClickListener { dismiss(); onStay() }
            btnCancel.setOnClickListener { dismiss(); onStay() }
            btnMove.setOnClickListener { dismiss(); onMove() }

            show()
            window?.apply {
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                decorView.setPadding(0, 0, 0, 0)
                val width = (resources.displayMetrics.widthPixels * 0.86f).toInt()
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                val params = attributes
                params.gravity = Gravity.CENTER
                attributes = params
            }
        }
    }

    // ------------------- UI 유틸 -------------------
    private fun setLoading(title: String, subtitle: String) {
        tvTitle.text = title
        tvSubtitle.text = subtitle
        progressBar.visibility = View.VISIBLE
    }

    private fun setPausedUi() = runOnUiThread {
        tvTitle.text = "일시정지됨"
        tvSubtitle.text = "뒤로가기 확인 중입니다."
        progressBar.visibility = View.INVISIBLE
    }

    private fun setLoadingOnMain(title: String, subtitle: String) =
        runOnUiThread { setLoading(title, subtitle) }

    private fun showToast(msg: String) = runOnUiThread { toast(msg) }
    private fun finishOnMain() = runOnUiThread { finish() }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // -------- 스펙 정규화 --------
    private fun normalizeForSpec(src: RequestDTO): RequestDTO {
        fun up(s: String) = s.trim().uppercase()

        val docNorm = when (val doc = up(src.documentType)) {
            "REAL_ESTATE", "REAL_ESTATE_REGISTER", "REAL_ESTATE_REGISTRY" -> "REAL_ESTATE_REGISTRY"
            "ENROLLMENT", "ENROLLMENT_CERTIFICATE" -> "ENROLLMENT_CERTIFICATE"
            "FAMILY", "FAMILY_RELATIONSHIP", "FAMILY_RELATIONSHIP_CERTIFICATE" -> "FAMILY_RELATIONSHIP_CERTIFICATE"
            else -> doc
        }
        val countryNorm = up(src.country)
        val langNorm = up(src.language)
        val issueNorm = when (val issueUp = up(src.issuanceChannel)) {
            "GOVERNMENT" -> "GOVERNMENT_AGENCY"
            else -> issueUp
        }
        val orientNorm = up(src.orientation)

        return src.copy(
            documentType = docNorm,
            country = countryNorm,
            language = langNorm,
            issuanceChannel = issueNorm,
            orientation = orientNorm
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        exitDialog?.dismiss()
        exitDialog = null
        cancelCurrentWork()
        scope.cancel()
    }
}

/* =========================
   translate-preview DTO들
   ========================= */

data class TranslatePreviewRequest(
    val requestDTO: RequestDTO,
    val fileNames: List<String>
)

data class RequestDTO(
    val documentType: String,
    val totalPages: Int,
    val country: String,
    val language: String,
    val issuanceChannel: String,
    val orientation: String
)

data class TranslatePreviewResponse(
    val rawDocumentId: Long,
    val docType: String?,
    val lang: String?,
    val path: String?,
    val result: JsonElement?   // 객체/배열/프리미티브/문자열 모두 수용
)
