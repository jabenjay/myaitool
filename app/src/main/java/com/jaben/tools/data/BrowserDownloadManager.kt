package com.jaben.tools.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.URLUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTaskItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val url: String,
    val subFolder: String,
    val mimeType: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val savedUri: String? = null,
    val errorMessage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

class BrowserDownloadManager(
    private val context: Context,
    private val proxyManager: ProxyManager
) {
    private val prefs = context.getSharedPreferences("toolbox_downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private val _items = MutableStateFlow(loadHistory())
    val items: StateFlow<List<DownloadTaskItem>> = _items.asStateFlow()

    private fun loadHistory(): List<DownloadTaskItem> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                val statusStr = obj.optString("status", DownloadStatus.FAILED.name)
                val parsedStatus = runCatching { DownloadStatus.valueOf(statusStr) }
                    .getOrDefault(DownloadStatus.FAILED)
                val finalStatus = if (parsedStatus == DownloadStatus.DOWNLOADING) {
                    DownloadStatus.CANCELLED
                } else {
                    parsedStatus
                }
                DownloadTaskItem(
                    id = obj.getString("id"),
                    fileName = obj.getString("fileName"),
                    url = obj.getString("url"),
                    subFolder = obj.optString("subFolder", OutputStorageHelper.SUB_FILES),
                    mimeType = obj.optString("mimeType", "application/octet-stream"),
                    totalBytes = obj.optLong("totalBytes", -1L),
                    downloadedBytes = obj.optLong("downloadedBytes", 0L),
                    status = finalStatus,
                    savedUri = obj.optString("savedUri", "").ifBlank { null },
                    errorMessage = obj.optString("errorMessage", "").ifBlank { null },
                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun persistHistory(list: List<DownloadTaskItem>) {
        val arr = JSONArray()
        list.take(80).forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("url", item.url)
                    put("subFolder", item.subFolder)
                    put("mimeType", item.mimeType)
                    put("totalBytes", item.totalBytes)
                    put("downloadedBytes", item.downloadedBytes)
                    put("status", item.status.name)
                    put("savedUri", item.savedUri ?: "")
                    put("errorMessage", item.errorMessage ?: "")
                    put("createdAtMs", item.createdAtMs)
                }
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun detectSubFolder(fileName: String, mimeType: String): String {
        val mime = mimeType.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "ico", "tif", "tiff", "svg") ->
                OutputStorageHelper.SUB_PICTURES
            mime.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "webm", "avi", "3gp", "m4v", "ts", "flv") ->
                OutputStorageHelper.SUB_VIDEOS
            mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "aiff") ->
                OutputStorageHelper.SUB_SOUNDS
            else -> OutputStorageHelper.SUB_FILES
        }
    }

    fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ): DownloadTaskItem {
        val cleanMime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: "application/octet-stream"
        val guessedName = URLUtil.guessFileName(url, contentDisposition, cleanMime)
            .ifBlank { "download_${System.currentTimeMillis()}" }
        val subFolder = detectSubFolder(guessedName, cleanMime)

        val item = DownloadTaskItem(
            fileName = guessedName,
            url = url,
            subFolder = subFolder,
            mimeType = cleanMime,
            totalBytes = contentLength,
            status = DownloadStatus.DOWNLOADING
        )
        updateList { listOf(item) + it }

        val job = scope.launch {
            val cacheDir = File(context.cacheDir, "browser_dl").apply { mkdirs() }
            val tempFile = File(cacheDir, "${item.id}.tmp")
            var conn: HttpURLConnection? = null

            try {
                val proxy = proxyManager.activeJavaProxy()
                var currentUrl = url
                var redirects = 0
                while (redirects < 5) {
                    val u = URL(currentUrl)
                    conn = (if (proxy != null) u.openConnection(proxy) else u.openConnection()) as HttpURLConnection
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 30_000
                    conn.instanceFollowRedirects = false
                    if (!userAgent.isNullOrBlank()) conn.setRequestProperty("User-Agent", userAgent)
                    val cookie = runCatching { CookieManager.getInstance().getCookie(currentUrl) }.getOrNull()
                    if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)

                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location") ?: break
                        currentUrl = URL(u, loc).toString()
                        conn.disconnect()
                        redirects++
                    } else {
                        break
                    }
                }

                val activeConn = conn ?: throw IllegalStateException("无法建立连接")
                val code = activeConn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }

                val total = activeConn.contentLengthLong.takeIf { it > 0L } ?: contentLength
                val resolvedMime = activeConn.contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
                    ?: cleanMime
                val resolvedSub = detectSubFolder(guessedName, resolvedMime)

                updateItem(item.id) {
                    it.copy(totalBytes = total, mimeType = resolvedMime, subFolder = resolvedSub)
                }

                var downloaded = 0L
                var lastUiUpdate = 0L
                val buf = ByteArray(16 * 1024)

                activeConn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { fos ->
                        while (isActive) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            fos.write(buf, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate >= 180L) {
                                lastUiUpdate = now
                                updateItem(item.id) { it.copy(downloadedBytes = downloaded) }
                            }
                        }
                    }
                }

                if (!isActive) throw CancellationException("Cancelled")

                val savedUri = OutputStorageHelper.saveFile(
                    context = context,
                    subFolder = resolvedSub,
                    fileName = guessedName,
                    mimeType = resolvedMime,
                    sourceFile = tempFile
                ) ?: throw IllegalStateException("写入存储目录失败")

                updateItem(item.id) {
                    it.copy(
                        downloadedBytes = tempFile.length(),
                        totalBytes = if (total > 0) total else tempFile.length(),
                        status = DownloadStatus.COMPLETED,
                        savedUri = savedUri.toString()
                    )
                }
                AppLogManager.log("BROWSER", "下载完成: $guessedName -> $resolvedSub")
            } catch (_: CancellationException) {
                updateItem(item.id) { it.copy(status = DownloadStatus.CANCELLED) }
            } catch (t: Throwable) {
                AppLogManager.log("BROWSER", "下载失败: $guessedName (${t.message})")
                updateItem(item.id) {
                    it.copy(status = DownloadStatus.FAILED, errorMessage = t.message ?: "下载失败")
                }
            } finally {
                runCatching { conn?.disconnect() }
                runCatching { tempFile.delete() }
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
        return item
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
    }

    fun removeRecord(id: String) {
        cancelDownload(id)
        updateList { it.filterNot { item -> item.id == id } }
    }

    fun clearFinished() {
        updateList { it.filter { item -> item.status == DownloadStatus.DOWNLOADING } }
    }

    @Synchronized
    private fun updateList(transform: (List<DownloadTaskItem>) -> List<DownloadTaskItem>) {
        val next = transform(_items.value)
        _items.value = next
        persistHistory(next)
    }

    @Synchronized
    private fun updateItem(id: String, transform: (DownloadTaskItem) -> DownloadTaskItem) {
        val next = _items.value.map { if (it.id == id) transform(it) else it }
        _items.value = next
        persistHistory(next)
    }

    private companion object {
        const val KEY_HISTORY = "download_history_json"
    }
}
