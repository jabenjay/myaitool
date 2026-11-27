package com.jaben.tools.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jaben.tools.data.AppUpdateChecker
import com.jaben.tools.data.UpdateChannel
import com.jaben.tools.data.UpdateCheckResult
import kotlinx.coroutines.launch
import java.net.Proxy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import com.jaben.tools.data.AppSettings
import com.jaben.tools.data.ProxyState
import com.jaben.tools.ui.components.SmoothCollapsingHeader
import com.jaben.tools.ui.components.UpdateBottomSheet
import com.jaben.tools.ui.components.rememberSmoothHeaderScrollState
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jaben.tools.ui.theme.liquidPress

@Composable
fun SettingsScreen(
    settings: AppSettings,
    proxyState: ProxyState,
    bottomExtraPadding: Dp = 0.dp,
    onOpenThemeSettings: () -> Unit,
    onOpenLayoutSettings: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUpdateUrl: (String) -> Unit,
    activeJavaProxy: () -> Proxy? = { null },
    modifier: Modifier = Modifier
) {
    val headerState = rememberSmoothHeaderScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { AppUpdateChecker.getCurrentVersionName(context) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var selectedChannel by remember { mutableStateOf(AppUpdateChecker.getSelectedChannel(context)) }
    var channelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(headerState.nestedScrollConnection)
    ) {
        SmoothCollapsingHeader(
            title = "设置",
            collapseProgress = headerState.progress
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 16.dp + bottomExtraPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsEntryCard(
                    icon = Icons.Rounded.Palette,
                    title = "主题",
                    subtitle = "${settings.uiStyle.label} · ${settings.themeMode.label}",
                    onClick = onOpenThemeSettings
                )
            }

            item {
                SettingsEntryCard(
                    icon = Icons.Rounded.GridView,
                    title = "布局",
                    subtitle = "主页每行 ${settings.homeColumns} 个工具",
                    onClick = onOpenLayoutSettings
                )
            }

            item {
                SettingsEntryCard(
                    icon = Icons.Rounded.VpnKey,
                    title = "网络代理",
                    subtitle = proxyState.statusMessage,
                    onClick = onOpenProxySettings
                )
            }

            item {
                SettingsEntryCard(
                    icon = Icons.Rounded.FolderOpen,
                    title = "保存路径",
                    subtitle = settings.outputRootLabel,
                    onClick = onOpenStorageSettings
                )
            }

            item {
                SettingsEntryCard(
                    icon = Icons.Rounded.BugReport,
                    title = "运行日志",
                    subtitle = "查看运行状态与崩溃记录",
                    onClick = onOpenLogs
                )
            }

            item {
                val visuals = LocalAppThemeVisuals.current
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = visuals.cardContainerColor,
                    contentColor = visuals.cardContentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidPress(pressedScale = 0.98f)
                        .clip(MaterialTheme.shapes.large)
                        .liquidGlassSurface(26.dp)
                        .clickable {
                            if (!isCheckingUpdate) {
                                isCheckingUpdate = true
                                scope.launch {
                                    updateResult = AppUpdateChecker.checkLatestRelease(
                                        context = context,
                                        proxy = activeJavaProxy(),
                                        channel = selectedChannel
                                    )
                                    isCheckingUpdate = false
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(visuals.cardContentColor.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "检查更新",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = visuals.cardContentColor
                            )
                            Text(
                                text = if (isCheckingUpdate) "正在检查更新..." else "当前版本 v$currentVersion",
                                style = MaterialTheme.typography.bodySmall,
                                color = visuals.cardContentColor.copy(alpha = 0.65f)
                            )
                        }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { channelMenuExpanded = true }
                            ) {
                                Text(
                                    text = "${selectedChannel.label} ▾",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = channelMenuExpanded,
                                onDismissRequest = { channelMenuExpanded = false }
                            ) {
                                UpdateChannel.entries.forEach { ch ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = ch.label,
                                                fontWeight = if (ch == selectedChannel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedChannel = ch
                                            AppUpdateChecker.setSelectedChannel(context, ch)
                                            channelMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsEntryCard(
                    icon = Icons.Rounded.Info,
                    title = "关于",
                    subtitle = "开源地址与捐赠支持",
                    onClick = onOpenAbout
                )
            }
        }
    }

    if (isCheckingUpdate) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("检查更新") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.8.dp)
                    Text("正在检查更新...")
                }
            },
            confirmButton = {}
        )
    }

    updateResult?.let { res ->
        if (res is UpdateCheckResult.UpdateAvailable) {
            UpdateBottomSheet(
                updateInfo = res,
                onDismiss = { updateResult = null }
            )
        } else {
            AlertDialog(
                onDismissRequest = { updateResult = null },
                title = {
                    Text(
                        when (res) {
                            is UpdateCheckResult.UpToDate -> "已是最新版本"
                            is UpdateCheckResult.Error -> "检查更新失败"
                            else -> ""
                        }
                    )
                },
                text = {
                    Text(
                        when (res) {
                            is UpdateCheckResult.UpToDate -> "当前版本 v${res.currentVersion} 已是最新。"
                            is UpdateCheckResult.Error -> res.message
                            else -> ""
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { updateResult = null }) {
                        Text("确定")
                    }
                }
            )
        }
    }

}

@Composable
private fun SettingsEntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val visuals = LocalAppThemeVisuals.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = visuals.cardContainerColor,
        contentColor = visuals.cardContentColor,
        modifier = Modifier
            .fillMaxWidth()
            .liquidPress(pressedScale = 0.98f)
            .clip(MaterialTheme.shapes.large)
            .liquidGlassSurface(26.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
