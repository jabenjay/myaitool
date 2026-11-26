package com.toolbox.pocket.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.toolbox.pocket.data.AppSettings
import com.toolbox.pocket.data.ProxyNode
import com.toolbox.pocket.data.ProxyProtocol
import com.toolbox.pocket.data.ProxyState
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.UiStyle
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    proxyState: ProxyState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUiStyleChange: (UiStyle) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBackgroundPicked: (Uri) -> Unit,
    onClearBackground: () -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onDefaultLineNumbersChange: (Boolean) -> Unit,
    onDefaultSoftWrapChange: (Boolean) -> Unit,
    onProxyToggle: (Boolean) -> Unit,
    onSelectProxy: (String) -> Unit,
    onAddProxy: (ProxyNode) -> Unit,
    onDeleteProxy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visuals = LocalAppThemeVisuals.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val titleFontSize = lerp(24.sp, 18.sp, scrollBehavior.state.collapsedFraction)

    var showAddProxyDialog by remember { mutableStateOf(false) }

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
                SectionLabel("应用内网络代理")
                GlassCard {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        SwitchSettingRow(
                            icon = Icons.Rounded.VpnKey,
                            title = "启用网络代理",
                            checked = proxyState.enabled,
                            enabled = proxyState.nodes.isNotEmpty(),
                            onCheckedChange = onProxyToggle
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = proxyState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                shape = RoundedCornerShape(50),
                                onClick = { showAddProxyDialog = true },
                                modifier = Modifier.liquidPress()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("添加节点")
                            }
                        }

                        proxyState.nodes.forEach { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectProxy(node.id) }
                                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = proxyState.selectedId == node.id,
                                    onClick = { onSelectProxy(node.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = node.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = node.summary(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteProxy(node.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除"
                                    )
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
                                    TextButton(onClick = onClearBackground, modifier = Modifier.liquidPress()) {
                                        Text("清除")
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

    if (showAddProxyDialog) {
        AddProxyDialog(
            onDismiss = { showAddProxyDialog = false },
            onSave = { node ->
                onAddProxy(node)
                showAddProxyDialog = false
            }
        )
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    val visuals = LocalAppThemeVisuals.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = visuals.cardContainerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .liquidGlassSurface(26.dp)
    ) {
        content()
    }
}

@Composable
private fun AddProxyDialog(
    onDismiss: () -> Unit,
    onSave: (ProxyNode) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(ProxyProtocol.SOCKS5) }
    var host by remember { mutableStateOf("127.0.0.1") }
    var portText by remember { mutableStateOf("1080") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加代理节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProxyProtocol.entries.forEach { item ->
                        FilterChip(
                            selected = protocol == item,
                            onClick = { protocol = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (protocol != ProxyProtocol.MTPROXY) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = {
                        Text(if (protocol == ProxyProtocol.MTPROXY) "MTProxy Secret" else "密码（可选）")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 1080
                    val cleanHost = host.trim().ifEmpty { "127.0.0.1" }
                    val finalName = name.trim().ifEmpty { "${protocol.label} $cleanHost" }
                    onSave(
                        ProxyNode(
                            name = finalName,
                            protocol = protocol,
                            host = cleanHost,
                            port = port,
                            username = username.trim(),
                            passwordOrSecret = secret.trim()
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
