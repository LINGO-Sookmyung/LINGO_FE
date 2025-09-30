package com.example.lingo.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object MyDocumentsStore {
    private const val PREF = "my_documents_store"
    private const val KEY = "items"

    data class Item(
        val uri: String,            // content:// … (다운로드된 파일 Uri)
        val displayName: String,    // 파일명
        val mimeType: String,
        val docTitle: String,       // 문서명(예: 가족관계증명서)
        val savedAt: Long,          // System.currentTimeMillis()

        // ✅ 선택 메타 필드 (없으면 null)
        val rawDocumentId: Long? = null,
        val metaJson: String? = null
    )

    /** 항목 추가 (중복: 동일 uri+displayName이면 무시) */
    fun add(context: Context, item: Item) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val list = load(context).toMutableList()
        if (list.none { it.uri == item.uri && it.displayName == item.displayName }) {
            list.add(0, item)
            prefs.edit().putString(KEY, Gson().toJson(list)).apply()
        }
    }

    /** 저장 목록 로드 */
    fun load(context: Context): List<Item> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "") ?: ""
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<Item>>() {}.type
        return try {
            Gson().fromJson<List<Item>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 전체 비우기 */
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY).apply()
    }
}
