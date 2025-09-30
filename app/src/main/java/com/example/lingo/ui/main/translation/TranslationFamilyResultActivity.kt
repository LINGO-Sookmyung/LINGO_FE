package com.example.lingo.ui.main.translation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

class TranslationFamilyResultActivity : AppCompatActivity() {

    // --- 인텐트로 받는 값들 ---
    private var rawDocumentId: Long = -1L
    private var docTitle: String = "가족관계증명서"

    /** 인텐트로 받은 "원문 전체" (래핑/메타 포함) */
    private lateinit var originalRoot: JsonObject
    /** 원본 result 객체 (편집 대상) */
    private lateinit var originalResult: JsonObject
    /** 최초 입력이 { "result": {...} } 래핑이었는지 표시 */
    private var wasWrapped: Boolean = false

    // ------------------- 생명주기 -------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translation_result_family)

        // 상단 앱바
        findViewById<MaterialToolbar?>(R.id.topToolbar)?.apply {
            title = "가족관계증명서"
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        // 인텐트 파라미터 수신
        rawDocumentId = intent.getLongExtra("rawDocumentId", -1L)
        docTitle = intent.getStringExtra("doc_title") ?: "가족관계증명서"
        val rawJsonStr = intent.getStringExtra("resultJson") ?: "{}"

        // ⚠️ 원문 전체를 보존 (docType 등 메타 포함)
        originalRoot = runCatching { Gson().fromJson(rawJsonStr, JsonObject::class.java) }.getOrNull() ?: JsonObject()
        val maybeResult = originalRoot.getAsJsonObject("result")
        if (maybeResult != null) {
            wasWrapped = true
            originalResult = maybeResult
        } else {
            wasWrapped = false
            originalResult = originalRoot
        }

        // 화면 프리필
        prefillUiFromJson()

        // 포커스 하이라이트
        setupFocusHighlight()

        // 다음(생성) 버튼 → DocumentGeneratingActivity로 편집 결과 전달
        findViewById<View?>(R.id.btnNext)?.setOnClickListener {
            if (rawDocumentId <= 0L) {
                Toast.makeText(this, "잘못된 문서 ID입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val editedResult = buildEditedJsonFromUi()

            // 루트 메타를 그대로 유지한 채 result만 교체
            val payload: JsonObject = if (wasWrapped) {
                originalRoot.deepCopy().apply { add("result", editedResult) }
            } else {
                editedResult
            }

            startActivity(
                Intent(
                    this,
                    com.example.lingo.ui.main.translation.document.DocumentGeneratingActivity::class.java
                ).apply {
                    putExtra("doc_title", docTitle)
                    putExtra("rawDocumentId", rawDocumentId)
                    putExtra("editedJson", payload.toString()) // 생성 페이로드 전달
                }
            )
        }
    }

    // ------------------- JSON 유틸 -------------------
    private fun JsonObject.optString(path: String): String? {
        if (path.isBlank()) return null
        val parts = path.split('.')
        var cur: JsonElement? = this
        for (p in parts) {
            cur = when {
                cur == null -> return null
                cur.isJsonObject -> cur.asJsonObject.get(p)
                cur.isJsonArray -> {
                    val idx = p.toIntOrNull() ?: return null
                    val arr = cur.asJsonArray
                    if (idx in 0 until arr.size()) arr[idx] else return null
                }
                else -> return null
            }
        }
        return cur?.takeIf { it.isJsonPrimitive }?.asString
    }

    /** 여러 후보 키 중 먼저 값이 나오는 것을 반환 */
    private fun JsonObject.firstNonBlank(vararg keys: String): String? {
        for (k in keys) {
            val v = optString(k)?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    // ------------------- JSON ↔ UI -------------------
    /** 원본 JSON 값을 화면에 채움 */
    private fun prefillUiFromJson() {
        fun setMulti(id: Int, vararg keys: String) {
            val v = findViewById<EditText?>(id) ?: return
            val value = originalResult.firstNonBlank(*keys) ?: ""
            v.setText(value)
        }

        // 발행일: dateOfIssue | documentIssueDate
        setMulti(R.id.editIssueDate, "dateOfIssue", "documentIssueDate")

        // 확인번호: certificateNumber | verificationCode
        setMulti(R.id.editCertNumber, "certificateNumber", "verificationCode")

        // 등록기준지(본적/주소): placeOfFamilyRegistration | baseAddress
        setMulti(R.id.editBaseAddress, "placeOfFamilyRegistration", "baseAddress")

        // 발급기관: issuingAuthority.organization | registryOffice
        setMulti(R.id.editRegistryOffice, "issuingAuthority.organization", "registryOffice")
    }

    /** 화면에서 수정된 값을 edited JSON으로 구성 (빈 값은 덮지 않음) */
    private fun buildEditedJsonFromUi(): JsonObject {
        val edited = originalResult.deepCopy()

        fun putIfNotBlank(id: Int, setter: (JsonObject, String) -> Unit) {
            val v = findViewById<EditText?>(id) ?: return
            val text = v.text?.toString()?.trim()
            if (!text.isNullOrBlank()) setter(edited, text)
        }

        // 발행일 → documentIssueDate & dateOfIssue
        putIfNotBlank(R.id.editIssueDate) { obj, value ->
            obj.addProperty("documentIssueDate", value)
            obj.addProperty("dateOfIssue", value)
        }

        // 확인번호 → verificationCode & certificateNumber
        putIfNotBlank(R.id.editCertNumber) { obj, value ->
            obj.addProperty("verificationCode", value)
            obj.addProperty("certificateNumber", value)
        }

        // 등록기준지(본적) → baseAddress & placeOfFamilyRegistration
        putIfNotBlank(R.id.editBaseAddress) { obj, value ->
            obj.addProperty("baseAddress", value)
            obj.addProperty("placeOfFamilyRegistration", value)
        }

        // 발급기관 → registryOffice & issuingAuthority.organization (nested)
        putIfNotBlank(R.id.editRegistryOffice) { obj, value ->
            obj.addProperty("registryOffice", value)
            val issuingAuthority = (obj.getAsJsonObject("issuingAuthority") ?: JsonObject()).also {
                it.addProperty("organization", value)
            }
            obj.add("issuingAuthority", issuingAuthority)
        }

        return edited
    }

    // ------------------- 포커스 하이라이트 -------------------
    private fun setupFocusHighlight() {
        val root: View = findViewById(android.R.id.content)
        findAllEditTexts(root).forEach { et ->
            val container = when (et.id) {
                R.id.editIssueDate -> findViewById<View>(R.id.layoutIssueDate) ?: et
                else -> et
            }
            et.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                container.setBackgroundResource(
                    if (hasFocus) R.drawable.outlined_button_selected
                    else R.drawable.outlined_button_white
                )
            }
        }
    }

    private fun findAllEditTexts(view: View): List<EditText> {
        val result = mutableListOf<EditText>()
        fun traverse(v: View) {
            when (v) {
                is EditText -> result.add(v)
                is ViewGroup -> repeat(v.childCount) { traverse(v.getChildAt(it)) }
            }
        }
        traverse(view)
        return result
    }
}