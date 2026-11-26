package com.toolbox.pocket.ui.screens

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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.toolbox.pocket.data.AppSettings
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.UiStyle
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUiStyleChange: (UiStyle) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBackgroundPicked: (android.net.Uri) -> Unit,
    onClearBackground: () -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onDefaultLineNumbersChange: (Boolean) -> Unit,
    onDefaultSoftWrapChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visuals = LocalAppThemeVisuals.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val titleFontSize = lerp(24.sp, 18.sp, scrollBehavior.state.collapsedFraction)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onBackgroundPicked(uri)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                        alpha = if (visuals.isLiquidGlass || visuals.hasCustomBackground) 0.85f else 1f
                    )
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionLabel("外观")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = visuals.cardContainerColor,
                    border = visuals.cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = visuals.cardContainerColor,
                    border = visuals.cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        SwitchSettingRow(
                            icon = Icons.Rounded.Palette,
                            title = "系统动态取色",
                            checked = settings.useDynamicColor && supportsDynamicColor,
                            enabled = supportsDynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                }
            }

            item {
                SectionLabel("自定义背景图")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = visuals.cardContainerColor,
                    border = visuals.cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (settings.backgroundImagePath != null) "已设置背景图" else "未设置背景图",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (settings.backgroundImagePath != null) {
                                    TextButton(onClick = onClearBackground) {
                                        Text("清除")
                                    }
                                }
                                FilledTonalButton(
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Text(if (settings.backgroundImagePath != null) "更换" else "选择图片")
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
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Text(
                                        text = "${settings.backgroundBlurDp.roundToInt()} dp",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
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
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = visuals.cardContainerColor,
                    border = visuals.cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
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
            style = MaterialTheme.typography.bodyLarge,
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
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
