package com.jaben.tools.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BookmarkItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String
)

class BookmarkManager(context: Context) {
    private val prefs = context.getSharedPreferences("toolbox_bookmarks", Context.MODE_PRIVATE)

    init {
        migrateV2DefaultsIfNeeded()
    }

    private val _bookmarks = MutableStateFlow(loadBookmarks())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    private val _homeUrl = MutableStateFlow(
        prefs.getString(KEY_HOME_URL, DEFAULT_HOME_URL) ?: DEFAULT_HOME_URL
    )
    val homeUrl: StateFlow<String> = _homeUrl.asStateFlow()

    private fun migrateV2DefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_BOOKMARKS_V2_MIGRATED, false)) return
        val oldHome = prefs.getString(KEY_HOME_URL, null)
        if (oldHome == null || oldHome == "https://cn.pornhub.com/") {
            prefs.edit().putString(KEY_HOME_URL, DEFAULT_HOME_URL).apply()
        }
        val raw = prefs.getString(KEY_BOOKMARKS, null)
        if (raw != null) {
            val parsed = parseJson(raw)
            val isOldDefaults = parsed.size == 2 &&
                parsed.any { it.url.contains("baidu.com") } &&
                parsed.any { it.url.contains("google.com") }
            if (isOldDefaults) {
                persist(defaultBookmarks())
            }
        }
        prefs.edit().putBoolean(KEY_BOOKMARKS_V2_MIGRATED, true).apply()
    }

    fun setHomeUrl(url: String) {
        val clean = normalizeUrl(url)
        prefs.edit().putString(KEY_HOME_URL, clean).apply()
        _homeUrl.value = clean
    }

    private fun defaultBookmarks(): List<BookmarkItem> = listOf(
        BookmarkItem(name = "52导航", url = "https://52crdh.com/"),
        BookmarkItem(name = "Pornhub", url = "https://cn.pornhub.com/")
    )

    private fun loadBookmarks(): List<BookmarkItem> {
        if (!prefs.contains(KEY_BOOKMARKS)) {
            val defaults = defaultBookmarks()
            persist(defaults)
            return defaults
        }
        return parseJson(prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]")
    }

    private fun parseJson(raw: String): List<BookmarkItem> {
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                BookmarkItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    url = obj.getString("url")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<BookmarkItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("url", item.url)
                }
            )
        }
        prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun addBookmark(name: String, url: String) {
        val cleanUrl = normalizeUrl(url)
        val cleanName = name.trim().ifEmpty { cleanUrl.removePrefix("https://").removePrefix("http://") }
        val updated = _bookmarks.value + BookmarkItem(name = cleanName, url = cleanUrl)
        _bookmarks.value = updated
        persist(updated)
    }

    fun updateBookmark(id: String, name: String, url: String) {
        val cleanUrl = normalizeUrl(url)
        val cleanName = name.trim().ifEmpty { cleanUrl.removePrefix("https://").removePrefix("http://") }
        val updated = _bookmarks.value.map {
            if (it.id == id) it.copy(name = cleanName, url = cleanUrl) else it
        }
        _bookmarks.value = updated
        persist(updated)
    }

    fun deleteBookmark(id: String) {
        val updated = _bookmarks.value.filterNot { it.id == id }
        _bookmarks.value = updated
        persist(updated)
    }

    fun moveBookmark(fromIndex: Int, toIndex: Int) {
        val current = _bookmarks.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _bookmarks.value = current
        persist(current)
    }

    private companion object {
        const val KEY_BOOKMARKS = "bookmarks_json"
        const val KEY_HOME_URL = "browser_home_url"
        const val KEY_BOOKMARKS_V2_MIGRATED = "bookmarks_v2_migrated"
        const val DEFAULT_HOME_URL = "https://52crdh.com/"
    }
}
