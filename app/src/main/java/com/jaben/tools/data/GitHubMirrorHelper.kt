package com.jaben.tools.data

import android.content.Context
import android.net.Uri

object GitHubMirrorHelper {
    private const val PREFS_NAME = "jools_github_mirror_prefs"
    private const val KEY_ENABLED = "mirror_enabled"

    // Stable public GitHub file/release acceleration mirrors (not exposed in UI)
    private val MIRROR_PREFIXES = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.1888866.xyz/",
        "https://github.akams.cn/"
    )

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isGitHubResourceUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isEmpty()) return false
        return host == "github.com" ||
            host == "raw.githubusercontent.com" ||
            host == "gist.githubusercontent.com" ||
            host == "objects.githubusercontent.com" ||
            host == "release-assets.githubusercontent.com" ||
            host.endsWith(".github.com") && host != "api.github.com"
    }

    fun accelerateUrl(context: Context, originalUrl: String): String {
        if (!isEnabled(context) || !isGitHubResourceUrl(originalUrl)) return originalUrl
        if (MIRROR_PREFIXES.any { originalUrl.startsWith(it, ignoreCase = true) }) return originalUrl
        return "${MIRROR_PREFIXES.first()}$originalUrl"
    }

    fun buildCandidateUrls(context: Context, originalUrl: String): List<String> {
        if (!isEnabled(context) || !isGitHubResourceUrl(originalUrl)) return listOf(originalUrl)
        if (MIRROR_PREFIXES.any { originalUrl.startsWith(it, ignoreCase = true) }) return listOf(originalUrl)
        return MIRROR_PREFIXES.map { "$it$originalUrl" } + originalUrl
    }
}
