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

    private val _bookmarks = MutableStateFlow(loadBookmarks())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    private val _homeUrl = MutableStateFlow(
        prefs.getString(KEY_HOME_URL, DEFAULT_HOME_URL) ?: DEFAULT_HOME_URL
    )
    val homeUrl: StateFlow<String> = _homeUrl.asStateFlow()

    fun setHomeUrl(url: String) {
        val clean = normalizeUrl(url)
        prefs.edit().putString(KEY_HOME_URL, clean).apply()
        _homeUrl.value = clean
    }

    private fun loadBookmarks(): List<BookmarkItem> {
        if (!prefs.contains(KEY_BOOKMARKS)) {
            val defaults = listOf(
                BookmarkItem(name = "百度", url = "https://www.baidu.com"),
                BookmarkItem(name = "Google", url = "https://www.google.com")
            )
            persist(defaults)
            return defaults
        }

        val raw = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
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
        const val DEFAULT_HOME_URL = "https://cn.pornhub.com/"
    }
}
