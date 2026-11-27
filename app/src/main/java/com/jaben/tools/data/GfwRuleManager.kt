package com.jaben.tools.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GfwRuleState(
    val ruleCount: Int = 0,
    val isUpdating: Boolean = false,
    val lastStatus: String = "代理规则更新时间：尚未更新"
)

class GfwRuleManager(context: Context) {
    private val prefs = context.getSharedPreferences("toolbox_gfw", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "gfw_rules_cache.txt")

    private val _state = MutableStateFlow(loadCachedState())
    val state: StateFlow<GfwRuleState> = _state.asStateFlow()

    @Volatile
    private var blockedDomains: Set<String> = loadCachedDomains()

    private fun loadCachedDomains(): Set<String> {
        if (!cacheFile.exists()) return DEFAULT_FALLBACK_DOMAINS
        val lines = runCatching { cacheFile.readLines(StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        return if (lines.isEmpty()) DEFAULT_FALLBACK_DOMAINS else lines.toSet()
    }

    private fun loadCachedState(): GfwRuleState {
        val count = if (cacheFile.exists()) {
            runCatching { cacheFile.readLines().size }.getOrDefault(DEFAULT_FALLBACK_DOMAINS.size)
        } else {
            DEFAULT_FALLBACK_DOMAINS.size
        }
        val savedTime = prefs.getString(KEY_LAST_UPDATE, null)
        val status = if (savedTime != null) {
            "代理规则更新时间：$savedTime"
        } else {
            "代理规则更新时间：尚未更新"
        }
        return GfwRuleState(
            ruleCount = count,
            isUpdating = false,
            lastStatus = status
        )
    }

    fun isGfwBlockedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase().trim()
        if (blockedDomains.contains(lower)) return true
        val parts = lower.split('.')
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (blockedDomains.contains(parent)) return true
        }
        return false
    }

    suspend fun updateRulesOnStartup() = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(
            isUpdating = true,
            lastStatus = "代理规则更新时间：更新中..."
        )

        val fetched = runCatching { fetchGfwListDomains() }.getOrNull()
        if (!fetched.isNullOrEmpty()) {
            blockedDomains = fetched
            runCatching {
                cacheFile.writeText(fetched.joinToString("\n"), StandardCharsets.UTF_8)
            }
            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }.format(Date())
            prefs.edit().putString(KEY_LAST_UPDATE, nowStr).apply()
            _state.value = GfwRuleState(
                ruleCount = fetched.size,
                isUpdating = false,
                lastStatus = "代理规则更新时间：$nowStr"
            )
        } else {
            val savedTime = prefs.getString(KEY_LAST_UPDATE, null) ?: "本地缓存"
            _state.value = GfwRuleState(
                ruleCount = blockedDomains.size,
                isUpdating = false,
                lastStatus = "代理规则更新时间：$savedTime"
            )
        }
    }

    private fun fetchGfwListDomains(): Set<String> {
        val url = URL(GFWLIST_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptySet()
            val b64 = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                .replace("\\s+".toRegex(), "")
            val decoded = String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
            val result = HashSet<String>(8192)
            decoded.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("@@")) {
                    return@forEach
                }
                val cleaned = line
                    .removePrefix("||")
                    .removePrefix("|https://")
                    .removePrefix("|http://")
                    .removePrefix(".")
                    .substringBefore("/")
                    .substringBefore("*")
                    .trim()
                if (cleaned.contains(".") && !cleaned.contains(" ")) {
                    result.add(cleaned.lowercase())
                }
            }
            result
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val KEY_LAST_UPDATE = "gfw_last_update"
        const val GFWLIST_URL = "https://raw.githubusercontent.com/gfwlist/gfwlist/master/gfwlist.txt"
        val DEFAULT_FALLBACK_DOMAINS = setOf(
            "google.com", "youtube.com", "pornhub.com", "twitter.com", "x.com",
            "github.com", "telegram.org", "t.me", "wikipedia.org", "reddit.com"
        )
    }
}
