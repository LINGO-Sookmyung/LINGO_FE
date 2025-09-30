package com.example.lingo.ui.main.translation.document

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lingo.R
import com.example.lingo.core.network.RetrofitClient
import com.example.lingo.core.model.CallResult
import com.example.lingo.data.repository.DocumentRepository
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 모달(뒤로가기 안내)용 import
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import com.example.lingo.core.network.ApiService
import com.example.lingo.data.local.TokenManager // 토큰 유무 판단용

class DocumentGeneratingActivity : AppCompatActivity() {

    // ✅ 토큰 유무에 따라 자동으로 무인증/인증 클라이언트 선택
    private val repo by lazy { DocumentRepository(provideApiAuto(this)) }

    private var docTitle: String = "문서"
    private var rawDocumentId: Long = -1L
    private var editedJsonStr: String? = null
    private var editedPayload: JsonObject? = null

    // 🔸 orientation 전달값 (추가)
    private var incomingOrientation: String? = null
    private var incomingRequestDtoJson: String? = null

    // 액티비티 자체 로딩 UI
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var progressBar: ProgressBar

    // 생성 작업 제어
    private var generateJob: Job? = null
    private var isPausedByDialog: Boolean = false
    private var hasCompleted: Boolean = false

    // 중복 호출 가드
    private var genRequestedOnce: Boolean = false

    // 다이얼로그 멤버 변수 (누수 방지)
    private var exitDialog: android.app.Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 로딩 전용 레이아웃
        setContentView(R.layout.activity_document_generating)

        // ✅ (삭제됨) 로그인 강제 종료 가드
        // 이전에는 비로그인 시 finish 했지만, 이제 비로그인도 생성 가능해야 하므로 제거

        // 로딩 뷰 바인딩
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        progressBar = findViewById(R.id.progress)

        // 전달값
        docTitle = intent.getStringExtra("doc_title") ?: "문서"
        rawDocumentId = intent.getLongExtra("rawDocumentId", -1L)
        editedJsonStr = intent.getStringExtra("editedJson")

        // 🔸 orientation 전달값 수신 (추가)
        incomingOrientation = intent.getStringExtra("orientation_enum")
        incomingRequestDtoJson = intent.getStringExtra("request_dto_json")

        // 툴바 표시
        setupToolbar()

        // 뒤로가기(툴바/시스템) → 종료 확인 모달 + 작업 일시정지
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showExitDuringGenerateDialog(
                        onMove = { // 종료 선택 → 작업 취소 후 finish
                            cancelGeneratingWork()
                            finish()
                        },
                        onStay = { // 머물기 → 작업 재개 (재개 시 조회 먼저)
                            resumeGeneratingWorkIfNeeded()
                        }
                    )
                }
            }
        )

        // 유효성 체크
        if (rawDocumentId <= 0L || editedJsonStr.isNullOrBlank()) {
            Toast.makeText(this, "문서 생성에 필요한 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            Log.e("DGA", "Invalid inputs: rawDocumentId=$rawDocumentId, editedJsonStr is null/blank")
            finish()
            return
        }

        // JSON 파싱
        editedPayload = try {
            Gson().fromJson(editedJsonStr, JsonObject::class.java) ?: JsonObject()
        } catch (e: JsonSyntaxException) {
            Log.e("DGA", "Invalid JSON payload: ${e.message}")
            JsonObject()
        }

        if (editedPayload?.size() == 0) {
            Toast.makeText(this, "잘못된 생성 데이터입니다.", Toast.LENGTH_SHORT).show()
            Log.e("DGA", "Parsed payload is empty object")
            finish()
            return
        }

        // 🔸 orientation 주입 (추가)
        applyOrientationIfProvided(editedPayload!!)

        // 로딩 문구 설정 후 생성 호출
        setLoading("문서 생성 중입니다.", "잠시만 기다려 주세요.")
        startGeneratingWork()
    }

    private fun provideApiAuto(ctx: android.content.Context): ApiService {
        val access = TokenManager.get(ctx).getAccessToken()
        return if (access.isNullOrBlank()) {
            RetrofitClient.api          // 비로그인 → 헤더 없음
        } else {
            RetrofitClient.apiWithAuth(ctx) // 로그인 → Authorization 자동 첨부
        }
    }

    // -----------------------------
    // 🔸 orientation 반영 (추가 로직 - 다른 부분 미변경)
    // -----------------------------
    private fun applyOrientationIfProvided(payload: JsonObject) {
        // 1) 우선순위: 명시 extra -> request_dto_json 파싱 -> null
        val fromExtra = incomingOrientation?.trim()?.uppercase()
        val fromDto = runCatching {
            incomingRequestDtoJson?.let {
                Gson().fromJson(it, SimpleRequestDTO::class.java)?.orientation?.trim()?.uppercase()
            }
        }.getOrNull()

        val candidate = when {
            !fromExtra.isNullOrBlank() -> fromExtra
            !fromDto.isNullOrBlank() -> fromDto
            else -> null
        }

        if (candidate == null) return
        if (candidate != "PORTRAIT" && candidate != "LANDSCAPE") return

        // 2) payload 최상단에 orientation이 없으면 넣기
        if (!payload.has("orientation")) {
            payload.addProperty("orientation", candidate)
            Log.d("DGA", "Injected orientation at root: $candidate")
        }

        // 3) payload.requestDTO가 있다면, 거기에도 없을 때만 넣기 (양쪽 모두 인식 가능성 대비)
        val reqDtoObj = runCatching { payload.getAsJsonObject("requestDTO") }.getOrNull()
        if (reqDtoObj != null && !reqDtoObj.has("orientation")) {
            reqDtoObj.addProperty("orientation", candidate)
            Log.d("DGA", "Injected orientation into requestDTO: $candidate")
        }
    }

    // 내부 파싱용 최소 DTO (orientation만 필요)
    private data class SimpleRequestDTO(val orientation: String?)

    // -----------------------------
    // 생성 작업 제어 (시작/취소/재개)
    // -----------------------------
    private fun startGeneratingWork() {
        if (hasCompleted) return
        if (generateJob?.isActive == true) return

        if (genRequestedOnce) {
            // 재개/재진입 시에는 먼저 기존 결과 조회
            tryFetchExistingThenFallbackToGenerate()
            return
        }

        // 최초 호출만 실제 generate를 바로 시도
        genRequestedOnce = true
        generateOnce()
    }

    private fun generateOnce() {
        val payload = editedPayload ?: return
        generateJob = lifecycleScope.launch {
            try {
                when (val r = repo.generateDoc(rawDocumentId, payload)) {
                    is CallResult.Success -> {
                        hasCompleted = true
                        val body = r.data
                        Log.d("DGA", "generateDoc success: name=${body.translatedName}, type=${body.contentType}")

                        // ✅ 프리뷰로 이동하며 ‘다운로드 성공 시 저장’ 힌트 전달
                        startActivity(
                            Intent(this@DocumentGeneratingActivity, DocumentPreviewActivity::class.java).apply {
                                putExtra("doc_title", docTitle)
                                putExtra("presigned_url", body.presignedDownloadUrl)
                                putExtra("mimeType", body.contentType)

                                // --- 저장을 위한 힌트들 ---
                                putExtra("saveOnDownloadIfLoggedIn", true)
                                putExtra("rawDocumentId", rawDocumentId)
                                putExtra("doc_metadata_json", editedJsonStr) // 필요 시 메타로 활용
                            }
                        )
                        finish()
                    }
                    is CallResult.Error -> {
                        val msg = (r.message ?: "").lowercase()
                        val looksDuplicate =
                            msg.contains("duplicate entry") ||
                                    msg.contains("uk_") ||
                                    msg.contains("409") || msg.contains("conflict")

                        if (looksDuplicate) {
                            Log.w("DGA", "Duplicate detected, trying to fetch existing translated doc")
                            tryFetchExistingThenFinishOrFallback()
                        } else {
                            hasCompleted = true
                            Log.e("DGA", "generateDoc error: ${r.message}")
                            Toast.makeText(
                                this@DocumentGeneratingActivity,
                                r.message ?: "문서 생성에 실패했습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    }
                }
            } catch (ce: CancellationException) {
                if (isPausedByDialog) {
                    Log.d("DGA", "generateDoc canceled due to pause-by-dialog")
                    setPausedUi()
                } else {
                    Log.d("DGA", "generateDoc canceled: ${ce.message}")
                }
            } catch (t: Throwable) {
                hasCompleted = true
                Log.e("DGA", "generateDoc failed: ${t.message}", t)
                Toast.makeText(
                    this@DocumentGeneratingActivity,
                    "문서 생성 중 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /**
     * 재개 시: 기존 생성본이 있는지 **조회** 먼저 시도.
     * - 조회 성공 → 프리뷰로 이동하고 종료
     * - 조회 실패 → 최초 재개라면 한 번만 generate 재호출
     */
    private fun tryFetchExistingThenFallbackToGenerate() {
        generateJob = lifecycleScope.launch {
            when (val g = repo.getTranslatedDocInfo(rawDocumentId)) {
                is CallResult.Success -> {
                    hasCompleted = true
                    val info = g.data
                    startActivity(
                        Intent(this@DocumentGeneratingActivity, DocumentPreviewActivity::class.java).apply {
                            putExtra("doc_title", docTitle)
                            putExtra("presigned_url", info.presignedDownloadUrl)
                            putExtra("mimeType", info.contentType)

                            // --- 저장 힌트 유지 ---
                            putExtra("saveOnDownloadIfLoggedIn", true)
                            putExtra("rawDocumentId", rawDocumentId)
                            putExtra("doc_metadata_json", editedJsonStr)
                        }
                    )
                    finish()
                }
                is CallResult.Error -> {
                    Log.w("DGA", "No existing doc found on resume, retrying generate once. err=${g.message}")
                    generateOnce()
                }
            }
        }
    }

    /**
     * 생성 호출에서 '중복' 감지된 경우:
     * - 조회 성공 → 프리뷰 이동 후 종료
     * - 조회 실패 → 사용자에게 안내 후 종료
     */
    private fun tryFetchExistingThenFinishOrFallback() {
        generateJob = lifecycleScope.launch {
            when (val g = repo.getTranslatedDocInfo(rawDocumentId)) {
                is CallResult.Success -> {
                    hasCompleted = true
                    val info = g.data
                    startActivity(
                        Intent(this@DocumentGeneratingActivity, DocumentPreviewActivity::class.java).apply {
                            putExtra("doc_title", info.translatedName.ifBlank { docTitle })
                            putExtra("presigned_url", info.presignedDownloadUrl)
                            putExtra("mimeType", info.contentType)

                            // --- 저장 힌트 유지 ---
                            putExtra("saveOnDownloadIfLoggedIn", true)
                            putExtra("rawDocumentId", rawDocumentId)
                            putExtra("doc_metadata_json", editedJsonStr)
                        }
                    )
                    finish()
                }
                is CallResult.Error -> {
                    hasCompleted = true
                    Toast.makeText(
                        this@DocumentGeneratingActivity,
                        "이미 생성된 문서를 불러오지 못했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun cancelGeneratingWork() {
        isPausedByDialog = false
        generateJob?.cancel(CancellationException("User chose to leave"))
        generateJob = null
    }

    private fun pauseGeneratingWorkForDialog() {
        if (generateJob?.isActive == true) {
            isPausedByDialog = true
            generateJob?.cancel(CancellationException("Paused by exit dialog"))
        }
        setPausedUi()
    }

    private fun resumeGeneratingWorkIfNeeded() {
        if (hasCompleted) return
        if (!isPausedByDialog) return
        isPausedByDialog = false
        setLoading("문서 생성 중입니다.", "잠시만 기다려 주세요.")
        startGeneratingWork() // 재개 시 조회 먼저 수행됨
    }

    // --- 상단바 설정 ---
    private fun setupToolbar() {
        var toolbar: MaterialToolbar? = findViewById(R.id.topToolbar)

        if (toolbar == null) {
            val candidate: View? = findViewById(R.id.topAppBar)
            toolbar = when (candidate) {
                is MaterialToolbar -> candidate
                is AppBarLayout    -> candidate.findViewById(R.id.topToolbar)
                else               -> null
            }
        }

        if (toolbar == null) {
            val appBarView = layoutInflater.inflate(R.layout.view_top_appbar, null)
            addContentView(
                appBarView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            toolbar = appBarView.findViewById(R.id.topToolbar)

            // 컨텐츠가 툴바 아래로 내려가도록 패딩 보정
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

        toolbar?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            supportActionBar?.title = docTitle
            tb.title = docTitle

            Log.d("DGA", "Toolbar='${tb.title}', ActionBar='${supportActionBar?.title}'")

            tb.setNavigationOnClickListener {
                showExitDuringGenerateDialog(
                    onMove = { cancelGeneratingWork(); finish() },
                    onStay = { resumeGeneratingWorkIfNeeded() }
                )
            }
        }
    }

    // --- 액티비티 자체 로딩 UI 업데이트 ---
    private fun setLoading(title: String, subtitle: String) {
        tvTitle.text = title
        tvSubtitle.text = subtitle
        progressBar.visibility = View.VISIBLE
    }

    private fun setPausedUi() {
        tvTitle.text = "일시정지됨"
        tvSubtitle.text = "뒤로가기 확인 중입니다."
        progressBar.visibility = View.INVISIBLE
    }

    // --- 뒤로가기 안내 모달 (dialog_exit_generate.xml) ---
    private fun showExitDuringGenerateDialog(
        onMove: () -> Unit,
        onStay: () -> Unit
    ) {
        if (isFinishing || isDestroyed || hasCompleted) return

        // 모달을 띄우기 직전에 작업 '일시정지'
        pauseGeneratingWorkForDialog()

        val view = layoutInflater.inflate(R.layout.dialog_exit_generate, null)

        exitDialog = android.app.Dialog(this, R.style.TransparentDialog).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(view)
            setCancelable(false)
            setCanceledOnTouchOutside(false)

            val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
            val btnClose  = view.findViewById<ImageButton>(R.id.btnClose)
            val btnCancel = view.findViewById<Button>(R.id.btnCancel)
            val btnMove   = view.findViewById<Button>(R.id.btnMove)

            tvMessage.text = "생성 중인 문서가 사라질 수 있습니다.\n계속 진행하시겠어요?"
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

    override fun onDestroy() {
        // 다이얼로그 누수 방지
        exitDialog?.dismiss()
        exitDialog = null
        // 진행 중인 작업 취소
        generateJob?.cancel(CancellationException("Activity destroyed"))
        generateJob = null
        super.onDestroy()
    }
}
