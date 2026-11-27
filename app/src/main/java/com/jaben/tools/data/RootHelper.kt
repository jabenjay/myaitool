package com.jaben.tools.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
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

    fun savePendingCleanupApk(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING_CLEANUP_APK, path).apply()
    }

    fun cleanupPendingApkIfNeeded(context: Context) {
        if (!isAutoDeleteApkEnabled(context)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PENDING_CLEANUP_APK, null) ?: return
        prefs.edit().remove(KEY_PENDING_CLEANUP_APK).apply()
        val f = File(path)
        if (f.exists()) {
            runCatching { f.delete() }
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
                return@withContext RootCheckResult(false, "Root 授权请求超时", foundSu)
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val ok = process.exitValue() == 0 && output.contains("uid=0")
            if (ok) {
                RootCheckResult(true, "已获取 Root 权限 ($output)", foundSu ?: "su")
            } else {
                RootCheckResult(false, if (output.isNotEmpty()) "未授权 Root: $output" else "未获取到 Root 权限", foundSu)
            }
        }.getOrElse { err ->
            RootCheckResult(false, "未检测到可用 su (${err.message ?: "无权限"})", foundSu)
        }
    }

    suspend fun silentInstallApk(apkFile: File): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) return@withContext false to "安装包不存在"
        val escaped = apkFile.absolutePath.replace("'", "'\\''")
        val size = apkFile.length()
        val cmd = "pm install -r -d '$escaped' || (cat '$escaped' | pm install -r -d -S $size)"
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
