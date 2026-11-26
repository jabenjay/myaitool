package com.jaben.tools.data

import android.content.Context
import android.webkit.WebResourceResponse
import org.json.JSONArray
import java.io.ByteArrayInputStream

class AdBlockManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getRules(): List<String> {
        val raw = prefs.getString(KEY_RULES, null) ?: return DEFAULT_RULES
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        }.getOrElse { DEFAULT_RULES }
    }

    fun saveRules(rules: List<String>) {
        val cleaned = rules.map { normalizeRule(it) }.filter { it.isNotEmpty() }.distinct()
        val arr = JSONArray()
        cleaned.forEach { arr.put(it) }
        prefs.edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun addRule(rawRule: String): List<String> {
        val rule = normalizeRule(rawRule)
        if (rule.isEmpty()) return getRules()
        val updated = (listOf(rule) + getRules()).distinct()
        saveRules(updated)
        return updated
    }

    fun removeRule(rule: String): List<String> {
        val updated = getRules().filterNot { it.equals(rule, ignoreCase = true) }
        saveRules(updated)
        return updated
    }

    fun resetToDefault(): List<String> {
        saveRules(DEFAULT_RULES)
        return DEFAULT_RULES
    }

    fun shouldBlock(url: String, rules: List<String> = getRules()): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return rules.any { rule ->
            val r = rule.trim().lowercase()
            r.isNotEmpty() && lower.contains(r)
        }
    }

    private fun normalizeRule(raw: String): String {
        return raw.trim()
            .removePrefix("||")
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("^")
            .trim()
    }

    companion object {
        private const val PREFS_NAME = "jools_adblock_prefs"
        private const val KEY_ENABLED = "adblock_enabled"
        private const val KEY_RULES = "adblock_rules"

        val DEFAULT_RULES = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.",
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
            "openx.net",
            "/pagead/",
            "adx.js",
            "popunder"
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

        const val FORCE_VIDEO_FULLSCREEN_JS = """
            (function(){
              try {
                var videos = Array.from(document.querySelectorAll('video'));
                var target = videos.find(function(v){ return !v.paused; }) || videos[0];
                if (!target) {
                  var iframes = document.querySelectorAll('iframe');
                  for (var i = 0; i < iframes.length; i++) {
                    try {
                      var iv = iframes[i].contentDocument && iframes[i].contentDocument.querySelector('video');
                      if (iv) { target = iv; break; }
                    } catch(e) {}
                  }
                }
                if (!target) return 'NO_VIDEO';
                target.controls = true;
                if (target.requestFullscreen) { target.requestFullscreen(); return 'OK'; }
                if (target.webkitRequestFullscreen) { target.webkitRequestFullscreen(); return 'OK'; }
                if (target.webkitEnterFullscreen) { target.webkitEnterFullscreen(); return 'OK'; }
                return 'NO_API';
              } catch(e) { return 'ERR'; }
            })();
        """
    }
}
