package com.jaben.tools.data

import android.app.DownloadManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class RootCheckResult(
    val isRooted: Boolean,
    val detail: String,
    val suPath: String? = null
)

object RootHelper {
    private const val PREFS_NAME = "jools_root_prefs"
    private const val KEY_SILENT_INSTALL = "silent_install_enabled"
    private const val KEY_AUTO_DELETE_APK = "auto_delete_apk_enabled"
    private const val KEY_PENDING_CLEANUP_APK = "pending_cleanup_apk_path"
    private const val KEY_PENDING_CLEANUP_DM_ID = "pending_cleanup_dm_id"

    fun isSilentInstallEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SILENT_INSTALL, false)

    fun setSilentInstallEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SILENT_INSTALL, enabled).apply()
    }

    fun isAutoDeleteApkEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_DELETE_APK, false)

    fun setAutoDeleteApkEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_DELETE_APK, enabled).apply()
    }

    fun savePendingCleanup(context: Context, downloadId: Long, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PENDING_CLEANUP_DM_ID, downloadId)
            .putString(KEY_PENDING_CLEANUP_APK, path)
            .apply()
    }

    fun cleanupPendingApkIfNeeded(context: Context) {
        if (!isAutoDeleteApkEnabled(context)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PENDING_CLEANUP_APK, null)
        val dmId = prefs.getLong(KEY_PENDING_CLEANUP_DM_ID, -1L)
        if (path == null && dmId < 0L) return

        prefs.edit()
            .remove(KEY_PENDING_CLEANUP_APK)
            .remove(KEY_PENDING_CLEANUP_DM_ID)
            .apply()

        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            if (dmId >= 0L) {
                runCatching {
                    val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.remove(dmId)
                }
            }
            if (!path.isNullOrBlank()) {
                deleteFileWithRootFallback(File(path))
            }
        }
    }

    suspend fun checkRootAccess(): RootCheckResult = withContext(Dispatchers.IO) {
        val candidates = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/vendor/bin/su")
        val foundSu = candidates.firstOrNull { File(it).exists() }

        runCatching {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(6, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext RootCheckResult(false, "未获取 Root 权限", foundSu)
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val ok = process.exitValue() == 0 && output.contains("uid=0")
            if (ok) {
                RootCheckResult(true, "已获取 Root 权限", foundSu ?: "su")
            } else {
                RootCheckResult(false, "未获取 Root 权限", foundSu)
            }
        }.getOrElse {
            RootCheckResult(false, "未获取 Root 权限", foundSu)
        }
    }

    suspend fun silentInstallApk(apkFile: File, deleteAfterInstall: Boolean = false): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (!apkFile.exists()) return@withContext false to "安装包不存在"
            val escaped = apkFile.absolutePath.replace("'", "'\\''")
            val size = apkFile.length()
            val installCmd = "(pm install -r -d '$escaped' || cat '$escaped' | pm install -r -d -S $size)"
            val cmd = if (deleteAfterInstall) "$installCmd && rm -f '$escaped'" else installCmd
            runCatching {
                val process = ProcessBuilder("su", "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(90, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@withContext false to "静默安装超时"
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                val ok = process.exitValue() == 0 || output.contains("Success", ignoreCase = true)
                ok to output.ifEmpty { if (ok) "Success" else "Exit ${process.exitValue()}" }
            }.getOrElse { err ->
                false to (err.message ?: "执行失败")
            }
        }

    fun deleteFileWithRootFallback(file: File): Boolean {
        if (!file.exists()) return true
        if (runCatching { file.delete() }.getOrDefault(false)) return true
        return runCatching {
            val escaped = file.absolutePath.replace("'", "'\\''")
            val p = ProcessBuilder("su", "-c", "rm -f '$escaped'").start()
            p.waitFor(5, TimeUnit.SECONDS) && !file.exists()
        }.getOrDefault(false)
    }
}
