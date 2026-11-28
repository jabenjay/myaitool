package com.jaben.tools.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

enum class UpdateChannel(val key: String, val label: String) {
    ALL("all", "全部"),
    RELEASE("release", "release"),
    PRERELEASE("prerelease", "prerelease"),
    BETA("beta", "beta"),
    DEBUG("debug", "debug");

    companion object {
        fun fromKey(key: String?): UpdateChannel =
            entries.firstOrNull { it.key == key } ?: ALL
    }
}

sealed class UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseName: String,
        val releaseNotes: String,
        val downloadUrl: String
    ) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateChecker {
    const val REPO_URL = "https://github.com/jabenjay/myaitool"
    private const val RELEASES_LIST_API = "https://api.github.com/repos/jabenjay/myaitool/releases?per_page=20"
    private const val PREFS_NAME = "jools_update_prefs"
    private const val KEY_CHANNEL = "update_channel"

    fun getSelectedChannel(context: Context): UpdateChannel {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return UpdateChannel.fromKey(prefs.getString(KEY_CHANNEL, UpdateChannel.ALL.key))
    }

    fun setSelectedChannel(context: Context, channel: UpdateChannel) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHANNEL, channel.key)
            .apply()
    }

    fun getCurrentVersionName(context: Context): String {
        return runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    private fun matchesChannel(release: JSONObject, channel: UpdateChannel): Boolean {
        if (release.optBoolean("draft", false)) return false
        val tag = release.optString("tag_name", "").lowercase()
        val isPre = release.optBoolean("prerelease", false)
        return when (channel) {
            UpdateChannel.ALL -> true
            UpdateChannel.RELEASE -> !isPre && !tag.contains("-beta") && !tag.contains("-pre") && !tag.contains("-debug")
            UpdateChannel.PRERELEASE -> tag.contains("-pre") || (isPre && !tag.contains("beta") && !tag.contains("debug"))
            UpdateChannel.BETA -> tag.contains("beta")
            UpdateChannel.DEBUG -> tag.contains("debug")
        }
    }

    suspend fun checkLatestRelease(
        context: Context,
        proxy: Proxy? = null,
        channel: UpdateChannel = getSelectedChannel(context)
    ): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val current = getCurrentVersionName(context)
            var conn: HttpURLConnection? = null
            try {
                val url = URL(RELEASES_LIST_API)
                conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 12_000
                conn.readTimeout = 12_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "Jools-Android")

                val code = conn.responseCode
                if (code == 404) {
                    return@withContext UpdateCheckResult.UpToDate(current)
                }
                if (code !in 200..299) {
                    return@withContext UpdateCheckResult.Error("检查失败 (HTTP $code)")
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                var matched: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (matchesChannel(item, channel)) {
                        matched = item
                        break
                    }
                }
                val json = matched ?: return@withContext UpdateCheckResult.UpToDate(current)

                val tagName = json.optString("tag_name", "").trim()
                val releaseName = json.optString("name", tagName).trim().ifBlank { tagName }
                val rawNotes = json.optString("body", "").trim()
                val releaseNotes = extractPlatformNotes(rawNotes, "android")
                val htmlUrl = json.optString("html_url", "$REPO_URL/releases")

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val dl = asset.optString("browser_download_url", "")
                        if (dl.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = dl
                            break
                        }
                    }
                }

                if (tagName.isNotBlank() && compareVersions(tagName, current) > 0) {
                    UpdateCheckResult.UpdateAvailable(
                        currentVersion = current,
                        latestVersion = tagName,
                        releaseName = releaseName,
                        releaseNotes = releaseNotes,
                        downloadUrl = apkUrl ?: htmlUrl
                    )
                } else {
                    UpdateCheckResult.UpToDate(current)
                }
            } catch (t: Throwable) {
                UpdateCheckResult.Error(t.message ?: "网络连接失败")
            } finally {
                runCatching { conn?.disconnect() }
            }
        }


    private fun extractPlatformNotes(body: String, platform: String): String {
        val lines = body.lines().filterNot { line ->
            val t = line.trim()
            t.startsWith("**Full Changelog**") || t.startsWith("Full Changelog:")
        }
        val targetHeader = if (platform == "android") "## android" else "## ios"
        val hasPlatformSections = lines.any {
            val t = it.trim().lowercase()
            t.startsWith("## android") || t.startsWith("## ios")
        }
        if (!hasPlatformSections) {
            return lines.joinToString("\n").trim()
        }
        var capturing = false
        val collected = ArrayList<String>()
        for (line in lines) {
            val trimmed = line.trim().lowercase()
            if (trimmed.startsWith("## ")) {
                if (trimmed.startsWith(targetHeader)) {
                    capturing = true
                } else if (capturing) {
                    break
                }
            } else if (capturing) {
                collected.add(line)
            }
        }
        return collected.joinToString("\n").trim()
    }

    fun startSystemDownload(context: Context, downloadUrl: String, latestVersion: String): Boolean {
        return runCatching {
            if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
                val cleanVer = latestVersion.removePrefix("v").removePrefix("V").ifBlank { "update" }
                val fileName = "Jools-$cleanVer.apk"
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val targetUrl = GitHubMirrorHelper.accelerateUrl(context, downloadUrl)
                val req = DownloadManager.Request(Uri.parse(targetUrl)).apply {
                    setTitle("Jools $latestVersion")
                    setDescription("正在下载更新安装包...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setMimeType("application/vnd.android.package-archive")
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Jools/$fileName")
                }
                val downloadId = dm.enqueue(req)
                val appCtx = context.applicationContext
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val completedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                        if (completedId != downloadId) return
                        runCatching { appCtx.unregisterReceiver(this) }
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        dm.query(query)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    val apkFile = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        "Jools/$fileName"
                                    )
                                    val silent = RootHelper.isSilentInstallEnabled(appCtx)
                                    val autoDel = RootHelper.isAutoDeleteApkEnabled(appCtx)
                                    if (autoDel) {
                                        RootHelper.savePendingCleanup(appCtx, downloadId, apkFile.absolutePath)
                                    }
                                    if (silent && apkFile.exists()) {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            val (ok, msg) = RootHelper.silentInstallApk(apkFile, deleteAfterInstall = autoDel)
                                            AppLogManager.log("Update", "Silent install: ok=$ok msg=$msg")
                                            if (!ok) {
                                                val apkUri = dm.getUriForDownloadedFile(downloadId)
                                                if (apkUri != null) {
                                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    runCatching { appCtx.startActivity(installIntent) }
                                                }
                                            }
                                        }
                                    } else {
                                        val apkUri = dm.getUriForDownloadedFile(downloadId) ?: return
                                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { appCtx.startActivity(installIntent) }
                                    }
                                }
                            }
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    appCtx,
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_EXPORTED
                )
                true
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
        }.getOrDefault(false)
    }

    private fun compareVersions(remote: String, local: String): Int {
        val rParts = remote.removePrefix("v").removePrefix("V").split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
        val lParts = local.removePrefix("v").removePrefix("V").split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }
}
