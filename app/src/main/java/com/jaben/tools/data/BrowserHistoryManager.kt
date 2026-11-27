package com.jaben.tools.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BrowserHistoryItem(
    val id: String,
    val title: String,
    val url: String,
    val visitedAtMs: Long
)

class BrowserHistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(): List<BrowserHistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val parsed = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ts = obj.optLong("visitedAtMs", 0L)
                    if (ts >= cutoff) {
                        val url = obj.optString("url").trim()
                        if (url.isNotEmpty()) {
                            add(
                                BrowserHistoryItem(
                                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                                    title = obj.optString("title").ifBlank { url },
                                    url = url,
                                    visitedAtMs = ts
                                )
                            )
                        }
                    }
                }
            }
        }.getOrElse { emptyList() }.sortedByDescending { it.visitedAtMs }
        return parsed
    }

    private fun saveHistory(items: List<BrowserHistoryItem>) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val valid = items.filter { it.visitedAtMs >= cutoff }.take(MAX_ITEMS)
        val arr = JSONArray()
        valid.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("visitedAtMs", item.visitedAtMs)
                }
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun recordVisit(title: String, url: String): List<BrowserHistoryItem> {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return getHistory()
        }
        val cleanTitle = title.trim().ifBlank { cleanUrl }
        val now = System.currentTimeMillis()
        val existing = getHistory().filterNot { it.url.equals(cleanUrl, ignoreCase = true) }
        val updated = listOf(
            BrowserHistoryItem(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                url = cleanUrl,
                visitedAtMs = now
            )
        ) + existing
        saveHistory(updated)
        return updated
    }

    fun search(keyword: String): List<BrowserHistoryItem> {
        val q = keyword.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return getHistory().filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }.take(15)
    }

    fun deleteItem(id: String): List<BrowserHistoryItem> {
        val updated = getHistory().filterNot { it.id == id }
        saveHistory(updated)
        return updated
    }

    fun deleteItems(ids: Set<String>): List<BrowserHistoryItem> {
        val updated = getHistory().filterNot { it.id in ids }
        saveHistory(updated)
        return updated
    }

    fun clearAll(): List<BrowserHistoryItem> {
        prefs.edit().remove(KEY_HISTORY).apply()
        return emptyList()
    }

    companion object {
        private const val PREFS_NAME = "jools_browser_history_prefs"
        private const val KEY_HISTORY = "history_items"
        private const val RETENTION_MS = 14L * 24L * 60L * 60L * 1000L
        private const val MAX_ITEMS = 500
    }
}
