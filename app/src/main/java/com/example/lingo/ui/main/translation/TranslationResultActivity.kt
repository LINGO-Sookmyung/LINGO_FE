package com.example.lingo.ui.main.translation

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lingo.R
import com.example.lingo.core.network.ApiService
import com.example.lingo.core.network.RetrofitClient
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.example.lingo.data.local.TokenManager // 로그인 여부 확인용

class TranslationResultActivity : AppCompatActivity() {

    private val dynamicNameEdits = mutableListOf<EditText>()
    private val dynamicNamePaths = mutableListOf<String>()

    private var rawDocumentId: Long = -1L
    private lateinit var docTitle: String
    private lateinit var originalJson: JsonObject
    private lateinit var rootJson: JsonObject
    private var wasWrapped: Boolean = false

    private var incomingOrientation: String? = null
    private var incomingRequestDtoJson: String? = null

    // ---------- UI 유틸 ----------
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun createNameEdit(index: Int, preset: String?): EditText {
        return EditText(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48).dp()
            ).apply { if (index > 0) setMargins(0, (12).dp(), 0, 0) }
            hint = "Hong Gil Dong"
            setBackgroundResource(R.drawable.outlined_button_white)
            setPadding((12).dp(), paddingTop, (12).dp(), paddingBottom)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#888888"))
            setText(preset.orEmpty())
        }
    }

    private fun provideApiAuto(ctx: android.content.Context): ApiService {
        val access = TokenManager.get(ctx).getAccessToken()
        return if (access.isNullOrBlank()) {
            RetrofitClient.api
        } else {
            RetrofitClient.apiWithAuth(ctx)
        }
    }

    // ---------- JSON 유틸 ----------
    private fun JsonObject.optString(path: String): String? {
        if (path.isBlank()) return null
        val parts = path.split('.')
        var cur: JsonElement = this
        for (p in parts) {
            cur = when {
                cur.isJsonObject -> cur.asJsonObject.get(p) ?: return null
                cur.isJsonArray -> {
                    val idx = p.toIntOrNull() ?: return null
                    val arr = cur.asJsonArray
                    if (idx in 0 until arr.size()) arr.get(idx) else return null
                }
                else -> return null
            }
        }
        return if (cur.isJsonPrimitive) cur.asString else null
    }

    private fun JsonObject.firstNonBlank(vararg keys: String): String? {
        for (k in keys) {
            val v = optString(k)?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun JsonElement.asStringOrNull(): String? =
        if (isJsonPrimitive) asJsonPrimitive.asString else null

    /** 딥서치 (등기부 필드용 보강) */
    private fun JsonElement.deepFindString(vararg keys: String): String? {
        fun norm(s: String) = s.lowercase().replace(Regex("[_\\s-]"), "")
        val targets = keys.map(::norm).toSet()

        fun visit(el: JsonElement): String? {
            when {
                el.isJsonObject -> {
                    val obj = el.asJsonObject
                    for ((k, v) in obj.entrySet()) {
                        if (targets.contains(norm(k)) && v.isJsonPrimitive) return v.asString
                        val sub = visit(v)
                        if (sub != null) return sub
                    }
                }
                el.isJsonArray -> {
                    for (ch in el.asJsonArray) {
                        val sub = visit(ch)
                        if (sub != null) return sub
                    }
                }
            }
            return null
        }
        return visit(this)
    }

    private fun JsonObject.findByPathsOrDeep(paths: Array<out String>, vararg deepKeys: String): String? {
        val v = firstNonBlank(*paths)
        return v ?: deepFindString(*deepKeys)
    }

    /**
     * ✅ 다양한 위치에서 '영문 이름'들을 동적으로 수집 (안정화 버전: 경로/필드 중심)
     *  - 루트 배열: englishNames / namesEnglish / memberEnglishNames
     *  - 단일 키: englishName / nameEnglish / fullNameEnglish / name.en / nameEn / fullName / name
     *  - 멤버 배열: familyMembers / members / children / parents / siblings / householdMembers / persons / parties / applicants / registrants / entries
     */
    private fun JsonObject.collectEnglishNames(): List<String> {
        val out = LinkedHashSet<String>() // 순서 유지 + 중복 제거

        fun addIfValid(s: String?) {
            val t = s?.trim()
            if (!t.isNullOrEmpty()) out.add(t)
        }

        // 1) 루트 배열 우선
        listOf("englishNames", "namesEnglish", "memberEnglishNames").forEach { key ->
            val arr = this.get(key) as? JsonArray ?: return@forEach
            arr.forEach { el ->
                when {
                    el.isJsonPrimitive -> addIfValid(el.asString)
                    el.isJsonObject -> {
                        val o = el.asJsonObject
                        addIfValid(
                            o.firstNonBlank(
                                "englishName",
                                "fullNameEnglish",
                                "nameEnglish",
                                "name.en",
                                "nameEn",
                                "fullName",
                                "name"
                            )
                        )
                    }
                }
            }
        }

        // 2) 흔한 단일 키들 (루트/경로)
        listOf(
            "englishName",
            "nameEnglish",
            "fullNameEnglish",
            "name.en",
            "nameEn",
            "fullName",
            "name",
            "registrant.fullNameEnglish",
            "registrant.englishName",
            "registrant.fullName",
            "applicant.englishName",
            "person.englishName",
            "subject.englishName"
        ).forEach { p -> addIfValid(optString(p)) }

        // 3) 가족/구성원 등의 배열 안에서 수집
        val memberArrayKeys = listOf(
            "familyMembers", "members", "children", "parents", "siblings",
            "householdMembers", "persons", "parties", "applicants", "registrants", "entries"
        )
        val nameFieldCandidates = listOf(
            "englishName", "fullNameEnglish", "nameEnglish", "name.en", "nameEn", "fullName", "name"
        )

        memberArrayKeys.forEach { arrKey ->
            val arr = this.get(arrKey)
            if (arr != null && arr.isJsonArray) {
                arr.asJsonArray.forEach { el ->
                    if (el.isJsonObject) {
                        val o = el.asJsonObject
                        var picked: String? = null
                        for (k in nameFieldCandidates) {
                            picked = o.optString(k)
                            if (!picked.isNullOrBlank()) break
                        }
                        addIfValid(picked)
                        (o.get("englishNames") as? JsonArray)?.forEach { inner ->
                            addIfValid(inner.asStringOrNull())
                        }
                    } else if (el.isJsonPrimitive) {
                        addIfValid(el.asString)
                    }
                }
            }
        }

        // 4) 쉼표/구분자로 합쳐진 경우 분해 (마지막 보조)
        if (out.isEmpty()) {
            firstNonBlank("englishName", "nameEnglish", "fullNameEnglish", "name.en", "nameEn")
                ?.split(',', '·', '/', '│', '|')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.forEach { out.add(it) }
        }

        return out.toList()
    }

    // ---------- 문서 타입 판별 ----------
    private fun resolveDocTypeForLayout(
        hintedTitle: String?,
        serverDocType: String?,
        serverDocumentType: String?
    ): String {
        val t1 = hintedTitle ?: ""
        val t2 = serverDocType ?: ""
        val t3 = serverDocumentType ?: ""

        fun isFamily(s: String) = s.contains("가족관계", true) || s.contains("Family Relationship", true)
        fun isEnrollment(s: String) = s.contains("재학", true) || s.contains("Enrollment", true)
        fun isProperty(s: String) = s.contains("등기", true) || s.contains("Property", true)

        return when {
            isFamily(t1) || isFamily(t2) || isFamily(t3) -> "FAMILY"
            isEnrollment(t1) || isEnrollment(t2) || isEnrollment(t3) -> "ENROLL"
            isProperty(t1) || isProperty(t2) || isProperty(t3) -> "PROPERTY"
            else -> "UNKNOWN"
        }
    }

    // ---------- 생명주기 ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        docTitle = intent.getStringExtra("doc_title") ?: ""
        rawDocumentId = intent.getLongExtra("rawDocumentId", -1L)
        val resultJson = intent.getStringExtra("resultJson") ?: "{}"

        incomingOrientation = intent.getStringExtra("orientation_enum")
        incomingRequestDtoJson = intent.getStringExtra("request_dto_json")

        rootJson = Gson().fromJson(resultJson, JsonObject::class.java)
        val resultObj = rootJson.getAsJsonObject("result")
        wasWrapped = (resultObj != null)
        originalJson = resultObj ?: rootJson

        val serverDocType = rootJson.optString("docType")
        val serverDocumentType = originalJson.optString("documentType")
        val resolved = resolveDocTypeForLayout(docTitle, serverDocType, serverDocumentType)

        val layoutRes = when (resolved) {
            "FAMILY" -> R.layout.activity_translation_result_family_name
            "ENROLL" -> R.layout.activity_translation_result_enrollment
            "PROPERTY" -> R.layout.activity_translation_result_property
            else -> {
                Toast.makeText(this, "지원하지 않는 문서 유형입니다.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
        setContentView(layoutRes)

        setupToolbar(resolved)
        prefillUiFromJson(layoutRes)

        findViewById<View?>(R.id.btnNext)?.setOnClickListener {
            if (rawDocumentId <= 0L) {
                Toast.makeText(this, "잘못된 문서 ID입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val edited = buildEditedJsonFromUi(layoutRes)

            when (layoutRes) {
                R.layout.activity_translation_result_family_name -> {
                    val payload = if (wasWrapped) rootJson.deepCopy().apply { add("result", edited) } else edited
                    val intent = Intent(this, TranslationFamilyResultActivity::class.java).apply {
                        putExtra("doc_title", "가족관계증명서")
                        putExtra("rawDocumentId", rawDocumentId)
                        putExtra("resultJson", payload.toString())
                        putExtra("orientation_enum", incomingOrientation)
                        putExtra("request_dto_json", incomingRequestDtoJson)
                    }
                    startActivity(intent)
                }
                else -> {
                    val payload = if (wasWrapped) rootJson.deepCopy().apply { add("result", edited) } else edited
                    val intent = Intent(this, com.example.lingo.ui.main.translation.document.DocumentGeneratingActivity::class.java).apply {
                        putExtra("doc_title", if (layoutRes == R.layout.activity_translation_result_enrollment) "재학증명서" else "부동산등기부등본 - 건물")
                        putExtra("rawDocumentId", rawDocumentId)
                        putExtra("editedJson", payload.toString())
                        putExtra("orientation_enum", incomingOrientation)
                        putExtra("request_dto_json", incomingRequestDtoJson)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    // ---------- 툴바 ----------
    private fun setupToolbar(resolved: String) {
        var toolbar: MaterialToolbar? = findViewById(R.id.topToolbar)
        if (toolbar == null) {
            val candidate: View? = findViewById(R.id.topAppBar)
            toolbar = when (candidate) {
                is MaterialToolbar -> candidate
                is AppBarLayout -> candidate.findViewById(R.id.topToolbar)
                else -> null
            }
        }
        if (toolbar == null) {
            val appBarView = layoutInflater.inflate(R.layout.view_top_appbar, null)
            addContentView(
                appBarView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            toolbar = appBarView.findViewById(R.id.topToolbar)
        }
        toolbar?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            val titleText = when (resolved) {
                "FAMILY" -> "가족관계증명서"
                "ENROLL" -> "재학증명서"
                "PROPERTY" -> "부동산등기부등본 - 건물"
                else -> docTitle.ifBlank { "문서" }
            }
            supportActionBar?.title = titleText
            tb.title = titleText
            tb.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
    }

    // ---------- 프리필 ----------
    private fun prefillUiFromJson(layoutRes: Int) {
        when (layoutRes) {
            R.layout.activity_translation_result_enrollment -> {
                setTextIfPresent(R.id.editIssueDate, "documentIssueDate", "dateOfIssue", "issueDate")
                setTextIfPresent(R.id.editName, "name", "fullName", "studentName")
                setTextIfPresent(R.id.editBirth, "dateOfBirth", "dob", "birthDate")
                setTextIfPresent(R.id.editDept, "affiliation", "department", "major")
                setTextIfPresent(R.id.editGrade, "grade", "year", "schoolYear")
                setTextIfPresent(R.id.editUniversity, "universityName", "schoolName")
            }
            R.layout.activity_translation_result_property -> {
                setTextIfPresent(R.id.editIssueDate, "documentIssueDate", "dateOfIssue", "issueDate")
                val owner = originalJson.findByPathsOrDeep(
                    arrayOf(
                        "owner",
                        "ownerName",
                        "registeredOwner",
                        "titleHolder",
                        "holderName",
                        "registrant",
                        "proprietor",
                        "owner.name",
                        "owners.0.name",
                        "ownership.0.name",
                        "rights.0.owner",
                        "registration.owner"
                    ),
                    "owner", "ownername", "registeredowner", "titleholder",
                    "holdername", "registrant", "proprietor"
                ) ?: ""
                findViewById<EditText?>(R.id.editOwner)?.setText(owner)
                setTextIfPresent(R.id.editAddress, "address", "property.address", "realEstate.address")
                setTextIfPresent(
                    R.id.editRegistryOffice,
                    "registryOffice", "registrationOffice", "competentRegistryOffice",
                    "issuerOffice", "registry.officeName"
                )
            }
            R.layout.activity_translation_result_family_name -> {
                val container = findViewById<LinearLayout>(R.id.layoutEnglishNames) ?: return
                container.removeAllViews()
                dynamicNameEdits.clear()
                dynamicNamePaths.clear()

                // ✅ 경로 중심의 안정화된 수집 로직 사용
                val collected = originalJson.collectEnglishNames()

                if (collected.isNotEmpty()) {
                    collected.forEachIndexed { idx, name ->
                        val et = createNameEdit(idx, name)
                        container.addView(et)
                        dynamicNameEdits += et
                        dynamicNamePaths += "englishNames[$idx]"
                    }
                } else {
                    val englishFullName = originalJson.firstNonBlank(
                        "englishName", "nameEnglish", "fullNameEnglish", "name.en", "nameEn",
                        "registrant.fullNameEnglish", "registrant.englishName",
                        "applicant.englishName", "person.englishName", "subject.englishName",
                        "romanizedName", "registrant.fullName"
                    )
                    val et = createNameEdit(0, englishFullName)
                    container.addView(et)
                    dynamicNameEdits += et
                    dynamicNamePaths += "englishName"
                }
            }
        }
    }

    private fun setTextIfPresent(editId: Int, vararg jsonKeys: String) {
        val v = findViewById<EditText?>(editId) ?: return
        val value = originalJson.firstNonBlank(*jsonKeys) ?: ""
        v.setText(value)
    }

    // ---------- UI → JSON ----------
    private fun buildEditedJsonFromUi(layoutRes: Int): JsonObject {
        val edited = originalJson.deepCopy()

        fun putIfNotBlank(id: Int, setter: (JsonObject, String) -> Unit) {
            val v = findViewById<EditText?>(id) ?: return
            val text = v.text?.toString()?.trim()
            if (!text.isNullOrBlank()) setter(edited, text)
        }

        when (layoutRes) {
            R.layout.activity_translation_result_enrollment -> {
                putIfNotBlank(R.id.editIssueDate) { obj, v -> obj.addProperty("dateOfIssue", v) }
                putIfNotBlank(R.id.editName) { obj, v -> obj.addProperty("fullName", v) }
                putIfNotBlank(R.id.editBirth) { obj, v -> obj.addProperty("dateOfBirth", v) }
                putIfNotBlank(R.id.editDept) { obj, v -> obj.addProperty("affiliation", v) }
                putIfNotBlank(R.id.editGrade) { obj, v -> obj.addProperty("grade", v) }
                putIfNotBlank(R.id.editUniversity) { obj, v -> obj.addProperty("universityName", v) }
            }
            R.layout.activity_translation_result_property -> {
                putIfNotBlank(R.id.editAddress) { obj, v -> obj.addProperty("address", v) }
                putIfNotBlank(R.id.editRegistryOffice) { obj, v -> obj.addProperty("registryOffice", v) }
                putIfNotBlank(R.id.editOwner) { obj, v ->
                    obj.addProperty("ownerName", v)
                    obj.addProperty("owner", v)
                    obj.addProperty("registeredOwner", v)
                    obj.addProperty("titleHolder", v)
                }
                putIfNotBlank(R.id.editIssueDate) { obj, v -> obj.addProperty("dateOfIssue", v) }
            }
            R.layout.activity_translation_result_family_name -> {
                val names = dynamicNameEdits.map { it.text?.toString().orEmpty().trim() }
                    .filter { it.isNotEmpty() }

                if (names.isNotEmpty()) {
                    val arr = JsonArray().apply { names.forEach { add(it) } }
                    edited.add("englishNames", arr)

                    val first = names.first()
                    edited.addProperty("englishName", first)
                    edited.addProperty("nameEnglish", first)

                    // registrant.fullNameEnglish이 없거나 비어있으면 채워주기
                    val registrant = (edited.getAsJsonObject("registrant") ?: JsonObject())
                    val needFill = !registrant.has("fullNameEnglish") ||
                            (registrant.get("fullNameEnglish")?.asString?.isBlank() == true)
                    if (needFill) {
                        registrant.addProperty("fullNameEnglish", first)
                    }
                    edited.add("registrant", registrant)
                }
            }
        }
        return edited
    }
}
