package com.jaben.tools.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AppLogManager {
    private const val MAX_LINES = 300
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (logFile != null) return
        val file = File(context.filesDir, "app_runtime_logs.txt")
        logFile = file

        if (file.exists()) {
            val existing = runCatching {
                file.readLines(StandardCharsets.UTF_8).takeLast(MAX_LINES)
            }.getOrDefault(emptyList())
            _logs.value = existing
        }

        log(
            "SYSTEM",
            "App启动 | 设备=${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            logSync("CRASH", "线程[${thread.name}]未捕获异常:\n$sw")
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    @Synchronized
    fun log(tag: String, message: String) {
        logSync(tag, message)
    }

    @Synchronized
    private fun logSync(tag: String, message: String) {
        val ts = dateFormat.format(Date())
        val entry = "[$ts][$tag] $message"
        val updated = (_logs.value + entry).takeLast(MAX_LINES)
        _logs.value = updated
        runCatching {
            logFile?.writeText(updated.joinToString("\n"), StandardCharsets.UTF_8)
        }
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
        runCatching { logFile?.writeText("", StandardCharsets.UTF_8) }
        log("SYSTEM", "日志已清空")
    }

    fun exportAllText(): String {
        return _logs.value.joinToString("\n")
    }
}
