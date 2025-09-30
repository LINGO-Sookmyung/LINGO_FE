package com.example.lingo.ui.main.mypage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingo.R
import com.example.lingo.ui.main.MainActivity
import com.example.lingo.ui.base.BaseActivity
import com.example.lingo.data.local.MyDocumentsStore
import kotlinx.coroutines.launch
import android.content.Context
import android.widget.Toast

class DocumentsActivity : BaseActivity() {

    private lateinit var rvDocuments: RecyclerView
    private lateinit var emptyView: View

    // 하단 네비
    private lateinit var tabHome: LinearLayout
    private lateinit var tabMy: LinearLayout
    private lateinit var iconHome: ImageView
    private lateinit var iconMy: ImageView
    private lateinit var textHome: TextView
    private lateinit var textMy: TextView

    private val adapter by lazy { DocumentsAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documents)

        // 상단 앱바
        setAppBar(title = "문서함", showBack = true)

        rvDocuments = findViewById(R.id.rvDocuments)
        emptyView = findViewById(R.id.emptyView)

        // 하단 네비
        tabHome = findViewById(R.id.tabHome)
        tabMy = findViewById(R.id.tabMy)
        iconHome = findViewById(R.id.iconHome)
        iconMy = findViewById(R.id.iconMy)
        textHome = findViewById(R.id.textHome)
        textMy = findViewById(R.id.textMy)

        // 마이페이지 선택된 상태 UI
        iconHome.setImageResource(R.drawable.ic_home)
        iconMy.setImageResource(R.drawable.ic_mypage_black)
        textHome.setTextColor(0xFF888888.toInt())
        textMy.setTextColor(0xFF000000.toInt())

        // 탭 동작
        tabHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        tabMy.setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
            finish()
        }

        // 리스트 세팅
        rvDocuments.layoutManager = LinearLayoutManager(this)
        rvDocuments.adapter = adapter

        // 데이터 로드
        loadDocuments()
    }

    /** 로컬 문서 불러오기 */
    private fun loadDocuments() {
        lifecycleScope.launch {
            val localDocs = MyDocumentsStore.load(this@DocumentsActivity)

            val items = mutableListOf<DocItem>()
            val sdf = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA)

            localDocs.forEach {
                val dateStr = sdf.format(java.util.Date(it.savedAt))
                items.add(
                    DocItem(
                        title = it.displayName,
                        date = "${it.docTitle} • $dateStr",
                        localUri = it.uri
                    )
                )
            }

            setItems(items)
        }
    }

    private fun setItems(items: List<DocItem>) {
        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            rvDocuments.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            rvDocuments.visibility = View.VISIBLE
        }
        adapter.submit(items)
    }
}

/** 데이터 모델(리스트 아이템) */
data class DocItem(
    val title: String,
    val date: String,
    val localUri: String
)

/** Adapter */
private class DocumentsAdapter : RecyclerView.Adapter<DocumentsVH>() {

    private val data = mutableListOf<DocItem>()

    fun submit(items: List<DocItem>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentsVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document, parent, false)
        return DocumentsVH(v)
    }

    override fun onBindViewHolder(holder: DocumentsVH, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size
}

/** ViewHolder */
private class DocumentsVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val title = itemView.findViewById<TextView>(R.id.tvTitle)
    private val date  = itemView.findViewById<TextView>(R.id.tvDate)
    private val btnOpen   = itemView.findViewById<Button>(R.id.btnAction)
    private val btnDelete = itemView.findViewById<Button>(R.id.btnDelete)

    fun bind(item: DocItem) {
        title.text = item.title
        date.text  = item.date

        // 열기 버튼
        btnOpen.text = "열기"
        btnOpen.setOnClickListener {
            val context = itemView.context
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(item.localUri), "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "문서 열기"))
        }

        // 삭제 버튼
        btnDelete.setOnClickListener {
            val context = itemView.context
            // 로컬 스토어에서 삭제
            val current = MyDocumentsStore.load(context).toMutableList()
            val newList = current.filterNot { it.uri == item.localUri }
            val prefs = context.getSharedPreferences("my_documents_store", Context.MODE_PRIVATE)
            prefs.edit().putString("items", com.google.gson.Gson().toJson(newList)).apply()

            Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()

            // 어댑터에서 리스트 갱신
            (itemView.parent as? RecyclerView)?.adapter?.let { adapter ->
                if (adapter is DocumentsAdapter) {
                    adapter.submit(
                        newList.map {
                            DocItem(
                                title = it.displayName,
                                date = "${it.docTitle} • ${
                                    java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA)
                                        .format(java.util.Date(it.savedAt))
                                }",
                                localUri = it.uri
                            )
                        }
                    )
                }
            }
        }
    }
}
