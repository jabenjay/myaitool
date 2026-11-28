package com.jaben.tools.tools.pushgit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jaben.tools.MainActivity
import com.jaben.tools.R
import com.jaben.tools.data.AppShortcutHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object BuildMonitorManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastKnownRunStates = ConcurrentHashMap<Long, String>()
    @Volatile
    private var hasSeeded = false
    @Volatile
    private var pollJob: Job? = null

    private fun normalizeStateKey(status: String, conclusion: String): String = when (status) {
        "queued", "waiting", "pending" -> "queued"
        "in_progress" -> "in_progress"
        "completed" -> "completed:$conclusion"
        else -> "$status:$conclusion"
    }

    private fun formatStatusBody(stateKey: String, branch: String): String {
        val label = when {
            stateKey == "queued" -> "工作流排队中"
            stateKey == "in_progress" -> "工作流构建中"
            stateKey == "completed:success" -> "工作流构建成功"
            stateKey == "completed:failure" -> "工作流构建失败"
            stateKey == "completed:cancelled" -> "工作流已取消"
            stateKey.startsWith("completed:") -> "工作流已完成"
            else -> "状态更新"
        }
        return "$label（$branch）"
    }

    fun onRunsObserved(
        context: Context,
        pat: String,
        repo: String,
        runs: List<WorkflowRunInfo>
    ) {
        val appCtx = context.applicationContext
        if (runs.isNotEmpty()) {
            if (!hasSeeded) {
                runs.forEach { r ->
                    lastKnownRunStates[r.id] = normalizeStateKey(r.status, r.conclusion)
                }
                hasSeeded = true
            } else {
                runs.forEach { r ->
                    val newKey = normalizeStateKey(r.status, r.conclusion)
                    val oldKey = lastKnownRunStates[r.id]
                    if (oldKey != newKey) {
                        if (oldKey != null || r.status != "completed") {
                            sendNotification(
                                context = appCtx,
                                runId = r.id,
                                stateKey = newKey,
                                title = "#${r.runNumber} · ${r.name}",
                                body = formatStatusBody(newKey, r.branch)
                            )
                        }
                        lastKnownRunStates[r.id] = newKey
                    }
                }
            }
        }
        val hasActive = runs.any { it.status != "completed" }
        if (hasActive && pat.isNotBlank() && repo.isNotBlank()) {
            ensurePolling(appCtx, pat, repo, gracePolls = 0)
        }
    }

    fun startMonitoringAfterDispatch(
        context: Context,
        pat: String,
        repo: String
    ) {
        if (pat.isBlank() || repo.isBlank()) return
        ensurePolling(context.applicationContext, pat, repo, gracePolls = 24)
    }

    @Synchronized
    private fun ensurePolling(
        appCtx: Context,
        pat: String,
        repo: String,
        gracePolls: Int
    ) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            val engine = PushGitEngine(appCtx)
            var remainingGrace = gracePolls
            while (true) {
                delay(4500)
                val result = engine.listWorkflowRuns(pat, repo)
                val runs = result.getOrNull()
                if (runs != null) {
                    onRunsObserved(appCtx, pat, repo, runs)
                    val hasActive = runs.any { it.status != "completed" }
                    if (hasActive) {
                        remainingGrace = 0
                    } else if (remainingGrace > 0) {
                        remainingGrace--
                    } else {
                        break
                    }
                } else if (remainingGrace > 0) {
                    remainingGrace--
                } else {
                    break
                }
            }
        }
    }

    private fun sendNotification(
        context: Context,
        runId: Long,
        stateKey: String,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val channelId = "jools_build_status_v2"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "构建状态通知", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "PushGit 工作流构建状态通知"
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("jools://shortcut/tool/pushgit")
            putExtra(AppShortcutHelper.EXTRA_SHORTCUT_ROUTE, "tool/pushgit")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notifId = ((runId.toString() + ":" + stateKey).hashCode() and 0x7FFFFFFF)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_jools)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        nm.notify(notifId, builder.build())
    }
}
