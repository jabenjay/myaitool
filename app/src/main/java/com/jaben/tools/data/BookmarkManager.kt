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
        migrateV4DefaultsIfNeeded()
    }

    private val _bookmarks = MutableStateFlow(loadBookmarks())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    private val _homeUrl = MutableStateFlow(
        prefs.getString(KEY_HOME_URL, DEFAULT_HOME_URL) ?: DEFAULT_HOME_URL
    )
    val homeUrl: StateFlow<String> = _homeUrl.asStateFlow()

    private fun migrateV4DefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_BOOKMARKS_V4_MIGRATED, false)) return
        prefs.edit().putString(KEY_HOME_URL, DEFAULT_HOME_URL).apply()
        val existing = if (prefs.contains(KEY_BOOKMARKS)) {
            parseJson(prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]")
        } else {
            emptyList()
        }
        val merged = (existing + defaultBookmarks()).distinctBy {
            it.url.trim().removeSuffix("/").lowercase()
        }
        persist(merged)
        prefs.edit().putBoolean(KEY_BOOKMARKS_V4_MIGRATED, true).apply()
    }

    fun setHomeUrl(url: String) {
        val clean = normalizeUrl(url)
        prefs.edit().putString(KEY_HOME_URL, clean).apply()
        _homeUrl.value = clean
    }

    private fun defaultBookmarks(): List<BookmarkItem> = listOf(
        BookmarkItem(name = "文尼导航", url = "https://biglist03.xyz/"),
        BookmarkItem(name = "59导航", url = "https://dh852.cc/?ide=pl2vfit1"),
        BookmarkItem(name = "海角57", url = "https://hjtv57.top/?lde=3k8pwlo0"),
        BookmarkItem(name = "萝莉岛", url = "https://loli88.top/?lde=7veeox3p"),
        BookmarkItem(name = "大门导航", url = "https://vbc04o.csmen1056.cc/%E5%A4%A7%E9%97%A8/1b956a/#shipin"),
        BookmarkItem(name = "2025秒播", url = "https://52chengrer.zxmbsp6.cc/2025/"),
        BookmarkItem(name = "搜片网", url = "https://52chengren.insearch10.cc/vip/369/")
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
        const val KEY_BOOKMARKS_V4_MIGRATED = "bookmarks_v4_migrated"
        const val DEFAULT_HOME_URL = "https://biglist03.xyz/"
    }
}
