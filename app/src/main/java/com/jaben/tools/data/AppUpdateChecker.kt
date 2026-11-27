package com.jaben.tools.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

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
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/jabenjay/myaitool/releases/latest"

    fun getCurrentVersionName(context: Context): String {
        return runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    suspend fun checkLatestRelease(context: Context, proxy: Proxy? = null): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val current = getCurrentVersionName(context)
            var conn: HttpURLConnection? = null
            try {
                val url = URL(LATEST_RELEASE_API)
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
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val releaseName = json.optString("name", tagName).trim().ifBlank { tagName }
                val rawNotes = json.optString("body", "").trim()
                val releaseNotes = rawNotes.lines()
                    .filterNot { line ->
                        val t = line.trim()
                        t.startsWith("**Full Changelog**") || t.startsWith("Full Changelog:")
                    }
                    .joinToString("\n")
                    .trim()
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


    fun startSystemDownload(context: Context, downloadUrl: String, latestVersion: String): Boolean {
        return runCatching {
            if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
                val cleanVer = latestVersion.removePrefix("v").removePrefix("V").ifBlank { "update" }
                val fileName = "Jools-release-$cleanVer.apk"
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val req = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
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
