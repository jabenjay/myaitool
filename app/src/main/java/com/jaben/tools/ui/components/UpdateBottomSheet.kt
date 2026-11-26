package com.jaben.tools.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.data.AppUpdateChecker
import com.jaben.tools.data.UpdateCheckResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    updateInfo: UpdateCheckResult.UpdateAvailable,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "发现新版本 ${updateInfo.latestVersion}",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "当前 v${updateInfo.currentVersion}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (updateInfo.releaseNotes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormattedChangelogText(notes = updateInfo.releaseNotes)
                }
            } else {
                Text(
                    text = "已有新版本可供下载更新。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("取消", fontSize = 15.sp)
                }

                Button(
                    onClick = {
                        val ok = AppUpdateChecker.startSystemDownload(
                            context = context,
                            downloadUrl = updateInfo.downloadUrl,
                            latestVersion = updateInfo.latestVersion
                        )
                        onDismiss()
                        if (ok) {
                            Toast.makeText(context, "已调用系统下载器开始下载，请在通知栏查看进度", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "启动系统下载器失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("立即下载", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun cleanChangelogLine(raw: String): String {
    return raw
        .replace(Regex("\\*\\*"), "")
        .replace(Regex("(?i)what'?s\\s+changed"), "更新日志")
        .replace(Regex("(?i)\\bchangelog\\b"), "更新日志")
        .trim()
}

@Composable
fun FormattedChangelogText(notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notes.lines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                line.startsWith("# ") -> {
                    Text(
                        text = cleanChangelogLine(line.removePrefix("# ")),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = cleanChangelogLine(line.removePrefix("## ")),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = cleanChangelogLine(line.removePrefix("### ")),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(14.dp)
                        )
                        Text(
                            text = cleanChangelogLine(line.drop(2)),
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val cleaned = cleanChangelogLine(line)
                    val isStandaloneHeading = cleaned == "更新日志"
                    Text(
                        text = cleaned,
                        fontSize = if (isStandaloneHeading) 15.5.sp else 13.5.sp,
                        fontWeight = if (isStandaloneHeading) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = 19.sp,
                        color = if (isStandaloneHeading) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
