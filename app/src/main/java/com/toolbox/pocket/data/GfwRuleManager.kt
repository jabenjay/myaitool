package com.toolbox.pocket.data

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

data class GfwRuleState(
    val ruleCount: Int = 0,
    val isUpdating: Boolean = false,
    val lastStatus: String = "仅代理 GFW 封锁域名与 IP（固定规则）"
)

/**
 * Fetches and caches GFW-blocked domain/IP rules automatically on app startup.
 * Routing policy is strictly locked to: Proxy ONLY GFW-blocked targets (no user override).
 */
class GfwRuleManager(context: Context) {
    private val cacheFile = File(context.filesDir, "gfw_rules_cache.txt")

    private val _state = MutableStateFlow(loadCachedState())
    val state: StateFlow<GfwRuleState> = _state.asStateFlow()

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
        return GfwRuleState(
            ruleCount = count,
            isUpdating = false,
            lastStatus = "固定规则：仅代理 GFW 封锁域名/IP（已缓存 $count 条）"
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
            lastStatus = "正在自动更新 GFW 规则..."
        )

        val fetched = runCatching { fetchGfwListDomains() }.getOrNull()
        if (!fetched.isNullOrEmpty()) {
            blockedDomains = fetched
            runCatching {
                cacheFile.writeText(fetched.joinToString("\n"), StandardCharsets.UTF_8)
            }
            _state.value = GfwRuleState(
                ruleCount = fetched.size,
                isUpdating = false,
                lastStatus = "固定规则：仅代理 GFW 封锁域名/IP（已更新 ${fetched.size} 条）"
            )
        } else {
            _state.value = GfwRuleState(
                ruleCount = blockedDomains.size,
                isUpdating = false,
                lastStatus = "固定规则：仅代理 GFW 封锁域名/IP（本地缓存 ${blockedDomains.size} 条）"
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
        const val GFWLIST_URL = "https://raw.githubusercontent.com/gfwlist/gfwlist/master/gfwlist.txt"
        val DEFAULT_FALLBACK_DOMAINS = setOf(
            "google.com", "youtube.com", "pornhub.com", "twitter.com", "x.com",
            "github.com", "telegram.org", "t.me", "wikipedia.org", "reddit.com"
        )
    }
}
