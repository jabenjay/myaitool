package com.toolbox.pocket.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VpnKey
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
import com.toolbox.pocket.data.AppSettings
import com.toolbox.pocket.data.ProxyState
import com.toolbox.pocket.ui.components.SmoothCollapsingHeader
import com.toolbox.pocket.ui.components.rememberSmoothHeaderScrollState
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress

@Composable
fun SettingsScreen(
    settings: AppSettings,
    proxyState: ProxyState,
    bottomExtraPadding: Dp = 0.dp,
    onOpenThemeSettings: () -> Unit,
    onOpenLayoutSettings: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headerState = rememberSmoothHeaderScrollState()

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
                    icon = Icons.Rounded.BugReport,
                    title = "运行日志",
                    subtitle = "查看运行状态与崩溃记录",
                    onClick = onOpenLogs
                )
            }
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
