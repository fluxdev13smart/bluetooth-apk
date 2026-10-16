package com.example.btchat.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A heading plus a piece of text you copy or send often (a place, an address, a phrase). */
data class SavedItem(val id: Long, val title: String, val text: String)

object Saved {
    private val _items = MutableStateFlow<List<SavedItem>>(emptyList())
    val items: StateFlow<List<SavedItem>> = _items
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("btchat_saved", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", "[]") ?: "[]"
        _items.value = try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                SavedItem(o.getLong("id"), o.getString("title"), o.getString("text"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** id 0 creates a new item at the top; any other id replaces that item. */
    @Synchronized
    fun upsert(id: Long, title: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val h = title.trim().ifEmpty { t.take(24) }
        val list = _items.value
        _items.value = if (id != 0L && list.any { it.id == id }) {
            list.map { if (it.id == id) SavedItem(id, h, t) else it }
        } else {
            listOf(SavedItem(System.currentTimeMillis(), h, t)) + list
        }
        persist()
    }

    @Synchronized
    fun delete(id: Long) {
        _items.value = _items.value.filter { it.id != id }
        persist()
    }

    private fun persist() {
        val a = JSONArray()
        _items.value.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("text", it.text))
        }
        prefs.edit().putString("items", a.toString()).apply()
    }
}
