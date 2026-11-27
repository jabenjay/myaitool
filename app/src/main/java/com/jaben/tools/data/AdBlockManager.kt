package com.jaben.tools.data

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AdBlockSubscription(
    val id: String,
    val name: String,
    val url: String,
    val fallbackUrl: String? = null,
    val enabled: Boolean = true,
    val ruleCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
    val isBuiltIn: Boolean = false
)

data class CompiledAdBlockRules(
    val domains: Set<String>,
    val keywords: List<String>
) {
    val totalCount: Int get() = domains.size + keywords.size
}

class AdBlockManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "adblock").apply { mkdirs() }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getSubscriptions(): List<AdBlockSubscription> {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null)
        val parsed = if (raw != null) {
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(
                            AdBlockSubscription(
                                id = obj.optString("id"),
                                name = obj.optString("name"),
                                url = obj.optString("url"),
                                fallbackUrl = obj.optString("fallbackUrl").ifBlank { null },
                                enabled = obj.optBoolean("enabled", true),
                                ruleCount = obj.optInt("ruleCount", 0),
                                lastUpdatedMs = obj.optLong("lastUpdatedMs", 0L),
                                isBuiltIn = obj.optBoolean("isBuiltIn", false)
                            )
                        )
                    }
                }
            }.getOrNull()
        } else null

        if (parsed.isNullOrEmpty()) {
            val initial = listOf(DEFAULT_AWAVENUE_SUB)
            saveSubscriptions(initial)
            return initial
        }
        return parsed
    }

    fun saveSubscriptions(list: List<AdBlockSubscription>) {
        val arr = JSONArray()
        list.forEach { sub ->
            arr.put(
                JSONObject().apply {
                    put("id", sub.id)
                    put("name", sub.name)
                    put("url", sub.url)
                    put("fallbackUrl", sub.fallbackUrl ?: "")
                    put("enabled", sub.enabled)
                    put("ruleCount", sub.ruleCount)
                    put("lastUpdatedMs", sub.lastUpdatedMs)
                    put("isBuiltIn", sub.isBuiltIn)
                }
            )
        }
        prefs.edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply()
    }

    fun addSubscription(name: String, url: String): List<AdBlockSubscription> {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return getSubscriptions()
        val current = getSubscriptions()
        if (current.any { it.url.equals(cleanUrl, ignoreCase = true) }) return current
        val cleanName = name.trim().ifBlank {
            runCatching { Uri.parse(cleanUrl).lastPathSegment?.substringBeforeLast('.') }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: "自定义订阅"
        }
        val newSub = AdBlockSubscription(
            id = "sub_${System.currentTimeMillis()}",
            name = cleanName,
            url = cleanUrl,
            enabled = true,
            ruleCount = 0,
            lastUpdatedMs = 0L,
            isBuiltIn = false
        )
        val updated = current + newSub
        saveSubscriptions(updated)
        return updated
    }

    fun toggleSubscription(id: String, enabled: Boolean): List<AdBlockSubscription> {
        val updated = getSubscriptions().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveSubscriptions(updated)
        return updated
    }

    fun removeSubscription(id: String): List<AdBlockSubscription> {
        val updated = getSubscriptions().filterNot { it.id == id && !it.isBuiltIn }
        runCatching { File(cacheDir, "$id.txt").delete() }
        saveSubscriptions(updated)
        return updated
    }

    fun getCustomDomains(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_DOMAINS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveCustomDomains(domains: List<String>) {
        val cleaned = domains.map { normalizeInputRule(it) }.filter { it.isNotEmpty() }.distinct()
        val arr = JSONArray()
        cleaned.forEach { arr.put(it) }
        prefs.edit().putString(KEY_CUSTOM_DOMAINS, arr.toString()).apply()
    }

    fun addCustomDomain(rawDomain: String): List<String> {
        val cleaned = normalizeInputRule(rawDomain)
        if (cleaned.isEmpty()) return getCustomDomains()
        val updated = (listOf(cleaned) + getCustomDomains()).distinct()
        saveCustomDomains(updated)
        return updated
    }

    fun removeCustomDomain(domain: String): List<String> {
        val updated = getCustomDomains().filterNot { it.equals(domain, ignoreCase = true) }
        saveCustomDomains(updated)
        return updated
    }

    suspend fun refreshSubscription(id: String): Result<AdBlockSubscription> = withContext(Dispatchers.IO) {
        val sub = getSubscriptions().firstOrNull { it.id == id }
            ?: return@withContext Result.failure(IllegalArgumentException("未找到该订阅"))

        val urlsToTry = listOfNotNull(sub.url, sub.fallbackUrl)
        var downloadedText: String? = null
        var lastErr: Throwable? = null

        for (targetUrl in urlsToTry) {
            val res = runCatching { fetchText(targetUrl) }
            if (res.isSuccess) {
                downloadedText = res.getOrNull()
                if (!downloadedText.isNullOrBlank()) break
            } else {
                lastErr = res.exceptionOrNull()
            }
        }

        val text = downloadedText
        if (text.isNullOrBlank()) {
            return@withContext Result.failure(lastErr ?: IllegalStateException("订阅内容为空"))
        }

        val compiled = parseSubscriptionText(text)
        File(cacheDir, "${sub.id}.txt").writeText(text, Charsets.UTF_8)

        val updatedSub = sub.copy(
            ruleCount = compiled.totalCount,
            lastUpdatedMs = System.currentTimeMillis()
        )
        val updatedList = getSubscriptions().map { if (it.id == id) updatedSub else it }
        saveSubscriptions(updatedList)
        Result.success(updatedSub)
    }

    suspend fun refreshAllEnabled(): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        getSubscriptions().filter { it.enabled }.forEach { sub ->
            if (refreshSubscription(sub.id).isSuccess) {
                successCount++
            }
        }
        successCount
    }

    fun compileActiveRules(): CompiledAdBlockRules {
        val domains = HashSet<String>(4096)
        val keywords = ArrayList<String>(64)

        // Built-in core fallback domains ensure immediate protection before first download completes
        domains.addAll(CORE_FALLBACK_DOMAINS)

        getSubscriptions().filter { it.enabled }.forEach { sub ->
            val cacheFile = File(cacheDir, "${sub.id}.txt")
            if (cacheFile.exists()) {
                runCatching {
                    val compiled = parseSubscriptionText(cacheFile.readText(Charsets.UTF_8))
                    domains.addAll(compiled.domains)
                    keywords.addAll(compiled.keywords)
                }
            }
        }

        getCustomDomains().forEach { custom ->
            val lower = custom.lowercase()
            if (lower.contains("/") || lower.contains("*")) {
                keywords.add(lower)
            } else {
                domains.add(lower)
            }
        }

        return CompiledAdBlockRules(domains = domains, keywords = keywords.distinct())
    }

    fun shouldBlock(url: String, compiled: CompiledAdBlockRules): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false

        val host = runCatching { Uri.parse(lower).host?.lowercase() }.getOrNull()
        if (!host.isNullOrEmpty()) {
            var currentHost: String = host
            while (true) {
                if (compiled.domains.contains(currentHost)) return true
                val dotIdx = currentHost.indexOf('.')
                if (dotIdx == -1 || dotIdx == currentHost.length - 1) break
                currentHost = currentHost.substring(dotIdx + 1)
            }
        }

        if (compiled.keywords.isNotEmpty()) {
            for (kw in compiled.keywords) {
                if (lower.contains(kw)) return true
            }
        }
        return false
    }

    private fun parseSubscriptionText(text: String): CompiledAdBlockRules {
        val domains = HashSet<String>(4096)
        val keywords = ArrayList<String>(64)

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() ||
                line.startsWith("!") ||
                line.startsWith("#") ||
                line.startsWith("[") ||
                line.startsWith("@@") ||
                line.contains("##") ||
                line.contains("#@#") ||
                line.contains("#?#")
            ) {
                return@forEach
            }

            // Hosts format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
            if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
                val hostPart = line.substringAfter(' ').substringBefore('#').trim().lowercase()
                if (isValidDomain(hostPart)) {
                    domains.add(hostPart)
                }
                return@forEach
            }

            // Adblock domain rule: "||example.com^" or "||example.com^$third-party"
            if (line.startsWith("||")) {
                val clean = line.removePrefix("||")
                    .substringBefore("$")
                    .substringBefore("^")
                    .trim()
                    .lowercase()
                if (isValidDomain(clean)) {
                    domains.add(clean)
                } else if (clean.length >= 4 && !clean.contains(" ")) {
                    keywords.add(clean)
                }
                return@forEach
            }

            // Plain domain
            val clean = line.substringBefore("$").substringBefore("^").trim().lowercase()
            if (isValidDomain(clean)) {
                domains.add(clean)
            }
        }

        return CompiledAdBlockRules(domains = domains, keywords = keywords.distinct())
    }

    private fun isValidDomain(s: String): Boolean {
        if (s.length < 4 || s.length > 253) return false
        if (!s.contains('.') || s.contains('/') || s.contains('*') || s.contains(' ')) return false
        return s.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }
    }

    private fun normalizeInputRule(raw: String): String {
        return raw.trim()
            .removePrefix("||")
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .removeSuffix("^")
            .trim()
            .lowercase()
    }

    private fun fetchText(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Jools-AdBlock/1.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val PREFS_NAME = "jools_adblock_v2_prefs"
        private const val KEY_ENABLED = "adblock_enabled"
        private const val KEY_SUBSCRIPTIONS = "adblock_subscriptions"
        private const val KEY_CUSTOM_DOMAINS = "adblock_custom_domains"

        val DEFAULT_AWAVENUE_SUB = AdBlockSubscription(
            id = "awavenue_default",
            name = "AWAvenue 秋风广告规则",
            url = "https://raw.githubusercontent.com/TG-Twilight/AWAvenue-Ads-Rule/main/AWAvenue-Ads-Rule.txt",
            fallbackUrl = "https://cdn.jsdelivr.net/gh/TG-Twilight/AWAvenue-Ads-Rule@main/AWAvenue-Ads-Rule.txt",
            enabled = true,
            ruleCount = 0,
            lastUpdatedMs = 0L,
            isBuiltIn = true
        )

        private val CORE_FALLBACK_DOMAINS = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "pos.baidu.com",
            "cpro.baidu.com",
            "eclick.baidu.com",
            "cbjs.baidu.com",
            "hm.baidu.com",
            "union.sogou.com",
            "tanx.com",
            "alimama.com",
            "mmstat.com",
            "cnzz.com",
            "umeng.com",
            "51.la",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "adsterra.com",
            "exoclick.com",
            "juicyads.com",
            "trafficjunky.com",
            "tsyndicate.com",
            "realsrv.com",
            "clickadu.com",
            "hilltopads.net",
            "monetag.com",
            "taboola.com",
            "outbrain.com",
            "criteo.com",
            "adnxs.com",
            "pubmatic.com",
            "rubiconproject.com",
            "openx.net"
        )

        fun emptyResponse(): WebResourceResponse {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        const val COSMETIC_JS = """
            (function(){
              try {
                var id = 'jools-adblock-style';
                if (!document.getElementById(id)) {
                  var s = document.createElement('style');
                  s.id = id;
                  s.textContent = 'ins.adsbygoogle, [id*="google_ads_iframe"], [class*="ad-banner"], [class*="floating-ad"], [class*="float-ad"], [class*="popup-ad"], [id*="ad_banner"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"], iframe[src*="exoclick"], iframe[src*="juicyads"] { display: none !important; visibility: hidden !important; height: 0 !important; pointer-events: none !important; }';
                  (document.head || document.documentElement).appendChild(s);
                }
              } catch(e) {}
            })();
        """
    }
}
