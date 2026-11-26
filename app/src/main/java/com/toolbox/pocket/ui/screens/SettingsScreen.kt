package com.toolbox.pocket.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.toolbox.pocket.data.M3ColorSource
import com.toolbox.pocket.data.ProxyState
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.UiStyle
import com.toolbox.pocket.ui.components.SmoothCollapsingHeader
import com.toolbox.pocket.ui.components.rememberSmoothHeaderScrollState
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    proxyState: ProxyState,
    bottomExtraPadding: Dp = 0.dp,
    onOpenProxySettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUiStyleChange: (UiStyle) -> Unit,
    onM3ColorSourceChange: (M3ColorSource) -> Unit,
    onGlassOpacityChange: (Float) -> Unit,
    onHomeColumnsChange: (Int) -> Unit,
    onBackgroundPicked: (Uri) -> Unit,
    onClearBackground: () -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onDefaultLineNumbersChange: (Boolean) -> Unit,
    onDefaultSoftWrapChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val headerState = rememberSmoothHeaderScrollState()
    val supportsSystemDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onBackgroundPicked(uri)
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidPress(pressedScale = 0.98f)
                            .clickable(onClick = onOpenProxySettings)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "网络代理",
                                fontSize = 15.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = proxyState.statusMessage,
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

            item {
                GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidPress(pressedScale = 0.98f)
                            .clickable(onClick = onOpenLogs)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "运行日志",
                            fontSize = 15.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionLabel("外观")
                GlassCard {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val icon = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                ThemeMode.DARK -> Icons.Rounded.DarkMode
                            }
                            RadioSettingRow(
                                icon = icon,
                                title = mode.label,
                                selected = settings.themeMode == mode,
                                onClick = { onThemeModeChange(mode) }
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel("主题风格")
                GlassCard {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        UiStyle.entries.forEach { style ->
                            val icon = when (style) {
                                UiStyle.MATERIAL -> Icons.Rounded.Style
                                UiStyle.LIQUID_GLASS -> Icons.Rounded.AutoAwesome
                            }
                            RadioSettingRow(
                                icon = icon,
                                title = style.label,
                                selected = settings.uiStyle == style,
                                onClick = { onUiStyleChange(style) }
                            )
                        }

                        AnimatedVisibility(visible = settings.uiStyle == UiStyle.MATERIAL) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "配色来源",
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    M3ColorSource.entries.forEachIndexed { index, source ->
                                        val enabled = when (source) {
                                            M3ColorSource.DEFAULT -> true
                                            M3ColorSource.SYSTEM -> supportsSystemDynamic
                                            M3ColorSource.WALLPAPER -> settings.backgroundImagePath != null
                                        }
                                        SegmentedButton(
                                            selected = settings.m3ColorSource == source,
                                            enabled = enabled,
                                            onClick = { onM3ColorSourceChange(source) },
                                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                                            modifier = Modifier.liquidPress(pressedScale = 0.96f)
                                        ) {
                                            Text(
                                                text = source.label,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = settings.uiStyle == UiStyle.LIQUID_GLASS) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Opacity,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "液态玻璃可视度",
                                            fontSize = 14.5.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${(settings.glassOpacity * 100f).roundToInt()}%",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Slider(
                                    value = settings.glassOpacity,
                                    onValueChange = onGlassOpacityChange,
                                    valueRange = 0.15f..0.90f
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel("主页布局")
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "每行工具数量",
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${settings.homeColumns} 个/行",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            (1..4).forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = settings.homeColumns == count,
                                    onClick = { onHomeColumnsChange(count) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                                    modifier = Modifier.liquidPress(pressedScale = 0.96f)
                                ) {
                                    Text("$count", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel("自定义背景图")
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (settings.backgroundImagePath != null) "已设置背景" else "未设置背景",
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (settings.backgroundImagePath != null) {
                                    TextButton(onClick = onClearBackground, modifier = Modifier.liquidPress()) {
                                        Text("清除", fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                                FilledTonalButton(
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.liquidPress()
                                ) {
                                    Text(
                                        text = if (settings.backgroundImagePath != null) "更换" else "选择图片",
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = settings.backgroundImagePath != null) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.BlurOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "模糊程度",
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Text(
                                        text = "${settings.backgroundBlurDp.roundToInt()} dp",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Slider(
                                    value = settings.backgroundBlurDp,
                                    onValueChange = onBackgroundBlurChange,
                                    valueRange = 0f..30f
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel("文本默认")
                GlassCard {
                    Column {
                        SwitchSettingRow(
                            icon = Icons.Rounded.FormatListNumbered,
                            title = "默认显示行号",
                            checked = settings.defaultShowLineNumbers,
                            enabled = true,
                            onCheckedChange = onDefaultLineNumbersChange
                        )
                        SwitchSettingRow(
                            icon = Icons.Rounded.WrapText,
                            title = "默认自动换行",
                            checked = settings.defaultSoftWrap,
                            enabled = true,
                            onCheckedChange = onDefaultSoftWrapChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    val visuals = LocalAppThemeVisuals.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = visuals.cardContainerColor,
        contentColor = visuals.cardContentColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .liquidGlassSurface(26.dp)
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun RadioSettingRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidPress(pressedScale = 0.985f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 14.5.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidPress(pressedScale = 0.985f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 14.5.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
