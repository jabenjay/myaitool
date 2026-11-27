package com.jaben.tools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.jaben.tools.data.PendingNoticeItem

@Composable
fun NoticeConfirmDialog(
    item: PendingNoticeItem,
    onAcknowledge: () -> Unit
) {
    val scrollState = rememberScrollState()
    var hasReachedBottom by remember(item.contentHash) { mutableStateOf(false) }

    LaunchedEffect(scrollState.value, scrollState.maxValue, item.contentHash) {
        if (scrollState.maxValue <= 0 || scrollState.value >= scrollState.maxValue - 16) {
            hasReachedBottom = true
        }
    }

    AlertDialog(
        onDismissRequest = { /* Non-dismissible until scrolled to bottom & acknowledged */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = item.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.content.lines().forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.isEmpty() -> {}
                        line.startsWith("# ") -> {
                            Text(
                                text = line.removePrefix("# ").trim(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        line.startsWith("## ") -> {
                            Text(
                                text = line.removePrefix("## ").trim(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        line.startsWith("- ") -> {
                            Text(
                                text = "• ${line.removePrefix("- ").replace("**", "").trim()}",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = line.replace("**", ""),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = hasReachedBottom,
                onClick = onAcknowledge,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (hasReachedBottom) "我已知晓" else "请滑动阅读至末尾",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
