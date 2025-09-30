package com.example.lingo.ui.main.translation

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingo.R
import androidx.core.content.ContextCompat
import android.content.Intent
import com.google.gson.Gson

class TranslationDocActivity : AppCompatActivity() {

    private lateinit var boxPageCount: View
    private lateinit var etPageCount: EditText
    private lateinit var btnPlus: ImageButton
    private lateinit var btnMinus: ImageButton

    private lateinit var rowCountry: DropRow
    private lateinit var rowLanguage: DropRow
    private lateinit var rowIssuer: DropRow
    private lateinit var rowOrientation: DropRow

    // 서버 enum 값 보관
    private var selectedCountry: String? = null      // USA | JAPAN | CHINA | VIETNAM
    private var selectedLanguage: String? = null     // ENGLISH | JAPANESE | CHINESE | VIETNAMESE
    private var selectedIssuer: String? = null       // GOVERNMENT | INTERNET | KIOSK | OTHER
    private var selectedOrientation: String? = null  // PORTRAIT | LANDSCAPE

    // UI ↔ 서버 enum 매핑
    private val countryMap = mapOf(
        "미국" to "USA",
        "일본" to "JAPAN",
        "중국" to "CHINA",
        "베트남" to "VIETNAM"
    )

    private val languageMap = mapOf(
        "영어" to "ENGLISH",
        "일본어" to "JAPANESE",
        "중국어" to "CHINESE",
        "베트남어" to "VIETNAMESE"
    )

    private val issuersUi = listOf("행정기관", "인터넷", "무인발급기", "기타")
    private val orientationsUi = listOf("세로", "가로")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translation_doc)

        val title = intent.getStringExtra("doc_title") ?: "문서"
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topToolbar).apply {
            this.title = title
            setNavigationOnClickListener { finish() }
        }

        // 페이지 수
        boxPageCount = findViewById(R.id.boxPageCount)
        etPageCount = findViewById(R.id.etPageCount)
        btnPlus = findViewById(R.id.btnPlus)
        btnMinus = findViewById(R.id.btnMinus)

        etPageCount.setText("1")
        btnMinus.isEnabled = false

        boxPageCount.setOnFocusChangeListener { v, hasFocus ->
            v.setBackgroundResource(if (hasFocus) R.drawable.outlined_button_selected else R.drawable.outlined_button_white)
        }
        etPageCount.setOnFocusChangeListener { _, hasFocus ->
            boxPageCount.setBackgroundResource(if (hasFocus) R.drawable.outlined_button_selected else R.drawable.outlined_button_white)
        }
        boxPageCount.setOnClickListener { etPageCount.requestFocus() }

        etPageCount.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(3))
        etPageCount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val n = s?.toString()?.toIntOrNull() ?: 0
                btnMinus.isEnabled = n > 1
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnPlus.setOnClickListener { changePage(+1) }
        btnMinus.setOnClickListener { changePage(-1) }

        // 드롭다운 초기화 (UI는 한국어 표시)
        rowCountry = DropRow(findViewById(R.id.rowCountry)).apply {
            setLabel("제출 국가")
            setValue("미국")
            setOnClick { showSelectDialog("제출 국가", countryMap.keys.toList()) { chooseCountry(it) } }
        }

        rowLanguage = DropRow(findViewById(R.id.rowLanguage)).apply {
            setLabel("번역 언어")
            setValue("영어")
            // ⬇️ 국가에 따라 제한된 언어만 노출
            setOnClick { showSelectDialog("번역 언어", languagesUiForSelectedCountry()) { chooseLanguage(it) } }
        }

        rowIssuer = DropRow(findViewById(R.id.rowIssuer)).apply {
            setLabel("발급처")
            setValue("인터넷")
            setOnClick { showSelectDialog("발급처", issuersUi) { chooseIssuer(it) } }
        }

        rowOrientation = DropRow(findViewById(R.id.rowOrientation)).apply {
            setLabel("문서 방향")
            setValue("세로")
            setOnClick { showSelectDialog("문서 방향", orientationsUi) { chooseOrientation(it) } }
        }
        chooseOrientation("세로")

        // 기본 국가/언어 세팅
        chooseCountry("미국")

        // 다음: ImagesActivity로 이동 + RequestDTO 전달
        findViewById<View>(R.id.btnNext).setOnClickListener {
            val pageCount = (etPageCount.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            val dto = RequestDTO(
                documentType = mapDocTypeFromTitle(title),
                totalPages = pageCount,
                country = (selectedCountry ?: "USA"),
                language = (selectedLanguage ?: "ENGLISH"),
                issuanceChannel = (selectedIssuer ?: "INTERNET"),
                orientation = (selectedOrientation ?: "PORTRAIT")
            )
            val dtoJson = Gson().toJson(dto)

            startActivity(
                Intent(this, TranslationDocImagesActivity::class.java)
                    .putExtra("expected_page_count", pageCount)
                    .putExtra("doc_title", title)
                    .putExtra("request_dto_json", dtoJson)
            )
        }
    }

    private fun changePage(delta: Int) {
        val now = etPageCount.text.toString().toIntOrNull() ?: 1
        val next = (now + delta).coerceAtLeast(1)
        etPageCount.setText(next.toString())
    }

    /** 국가 선택 → 서버 enum 보관 + 언어 후보 재설정 */
    private fun chooseCountry(uiValue: String) {
        selectedCountry = countryMap[uiValue] ?: "USA"   // 서버 enum
        rowCountry.setValue(uiValue)                    // UI는 한국어 유지

        val allowedServerLangs = availableLanguages()   // 서버 enum 목록
        // 현재 선택이 허용 목록에 없으면 첫 번째로 세팅
        if (selectedLanguage !in allowedServerLangs) {
            selectedLanguage = allowedServerLangs.first()
        }
        // UI 표시값(한국어)로 반영
        val uiLang = languageMap.entries.first { it.value == selectedLanguage }.key
        rowLanguage.setValue(uiLang)
    }

    /** 국가별 허용 언어(서버 enum) */
    private fun availableLanguages(): List<String> = when (selectedCountry) {
        "JAPAN"   -> listOf("ENGLISH", "JAPANESE")
        "CHINA"   -> listOf("ENGLISH", "CHINESE")
        "VIETNAM" -> listOf("ENGLISH", "VIETNAMESE")
        else      -> listOf("ENGLISH") // USA (기본)
    }

    /** 현재 국가에 따라 언어 다이얼로그에 보여줄 UI(한국어) 목록 */
    private fun languagesUiForSelectedCountry(): List<String> {
        val allowed = availableLanguages() // 서버 enum 값들
        return languageMap
            .filterValues { it in allowed }
            .keys
            .toList() // UI 한국어 라벨만
    }

    private fun chooseLanguage(uiValue: String) {
        selectedLanguage = languageMap[uiValue] ?: "ENGLISH" // 서버 enum
        rowLanguage.setValue(uiValue)                        // UI 한국어
    }

    private fun chooseIssuer(valueUi: String) {
        selectedIssuer = when (valueUi) {
            "행정기관" -> "GOVERNMENT"
            "무인발급기" -> "KIOSK"
            "기타" -> "OTHER"
            else -> "INTERNET"
        }
        rowIssuer.setValue(valueUi)
    }

    private fun chooseOrientation(ui: String) {
        val v = ui.trim()
        selectedOrientation = if (v == "가로") "LANDSCAPE" else "PORTRAIT"
        rowOrientation.setValue(v)
    }

    /** 타이틀 → 서버 documentType 매핑 (필요 시 실제 enum에 맞춰 수정) */
    private fun mapDocTypeFromTitle(title: String): String = when (title) {
        "가족관계증명서" -> "FAMILY"
        "재학증명서" -> "ENROLLMENT"
        "부동산등기부등본 - 건물" -> "REAL_ESTATE_REGISTRY"
        else -> "GENERIC"
    }

    /** 리스트 선택 모달 */
    private fun showSelectDialog(title: String, items: List<String>, onSelect: (String) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_select_list, null)
        val rv = view.findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = SelectAdapter(items) { selected -> onSelect(selected) }
        rv.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .create()

        adapter.onClickAfter = { dialog.dismiss() }
        dialog.show()
    }

    /** 드롭다운 행 wrapper */
    private class DropRow(private val container: View) {
        private val label: TextView = container.findViewById(R.id.tvLabel)
        private val value: TextView = container.findViewById(R.id.tvValue)
        private val box: View = container.findViewById(R.id.box)
        private val arrow: View = container.findViewById(R.id.ivArrow)

        fun setLabel(text: String) { label.text = text }
        fun setValue(text: String) {
            value.text = text
            value.setTextColor(ContextCompat.getColor(container.context, R.color.text_primary))
        }
        fun setOnClick(block: () -> Unit) {
            val clicker = View.OnClickListener { block() }
            box.setOnClickListener(clicker); value.setOnClickListener(clicker); arrow.setOnClickListener(clicker)

            box.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundResource(if (hasFocus) R.drawable.outlined_button_selected else R.drawable.outlined_button_white)
            }
            box.setOnTouchListener { v, _ ->
                v.isPressed = true; false
            }
        }
    }
}

/** 선택 모달용 어댑터 */
private class SelectAdapter(
    private val items: List<String>,
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<SelectAdapter.VH>() {

    var onClickAfter: (() -> Unit)? = null
    private var selectedPos = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_selectable, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = items[position]
        holder.itemView.isActivated = position == selectedPos
        holder.tv.isActivated = holder.itemView.isActivated

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

            val old = selectedPos
            selectedPos = pos
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
            notifyItemChanged(selectedPos)

            onSelect(items[pos])
            onClickAfter?.invoke()
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tv)
    }
}
