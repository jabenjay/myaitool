package com.toolbox.pocket.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.pocket.data.GfwRuleState
import com.toolbox.pocket.data.ProxyLatency
import com.toolbox.pocket.data.ProxyNode
import com.toolbox.pocket.data.ProxyProtocol
import com.toolbox.pocket.data.ProxyState
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    proxyState: ProxyState,
    gfwRuleState: GfwRuleState,
    onBack: () -> Unit,
    onProxyToggle: (Boolean) -> Unit,
    onSelectProxy: (String) -> Unit,
    onAddProxy: (ProxyNode) -> Unit,
    onDeleteProxy: (String) -> Unit,
    onTestNode: (String) -> Unit,
    onTestAllNodes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "网络代理",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.liquidPress()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.liquidPress()) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "添加节点"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = visuals.cardContainerColor,
                    contentColor = visuals.cardContentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .liquidGlassSurface(26.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启用应用内代理",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
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
                            Switch(
                                checked = proxyState.enabled,
                                onCheckedChange = onProxyToggle,
                                enabled = proxyState.nodes.isNotEmpty()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = gfwRuleState.lastStatus,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "代理节点",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            shape = RoundedCornerShape(50),
                            onClick = onTestAllNodes,
                            enabled = proxyState.nodes.isNotEmpty() && !proxyState.isTestingAll,
                            modifier = Modifier.liquidPress()
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (proxyState.isTestingAll) "测试中" else "全部测试",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        FilledTonalButton(
                            shape = RoundedCornerShape(50),
                            onClick = { showAddDialog = true },
                            modifier = Modifier.liquidPress()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "添加",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (proxyState.nodes.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .liquidGlassSurface(26.dp)
                    ) {
                        Text(
                            text = "暂无节点，点击右上角「添加」配置 SOCKS / HTTP",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(proxyState.nodes, key = { it.id }) { node ->
                    val latency = proxyState.latencies[node.id]
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidPress(pressedScale = 0.98f)
                            .clip(MaterialTheme.shapes.large)
                            .liquidGlassSurface(26.dp)
                            .clickable { onSelectProxy(node.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = proxyState.selectedId == node.id,
                                onClick = { onSelectProxy(node.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = node.name,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = node.summary(),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Latency badge to the left of the Test button
                            if (latency != null) {
                                val (latText, latColor) = when (latency) {
                                    is ProxyLatency.Testing -> "测速中" to MaterialTheme.colorScheme.onSurfaceVariant
                                    is ProxyLatency.Success -> "${latency.ms} ms" to MaterialTheme.colorScheme.primary
                                    is ProxyLatency.Failed -> latency.reason to MaterialTheme.colorScheme.error
                                }
                                Text(
                                    text = latText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = latColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }

                            TextButton(
                                onClick = { onTestNode(node.id) },
                                enabled = latency !is ProxyLatency.Testing,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.liquidPress()
                            ) {
                                Text(
                                    text = "测试",
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            IconButton(
                                onClick = { onDeleteProxy(node.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProxyNodeDialog(
            onDismiss = { showAddDialog = false },
            onSave = { node ->
                onAddProxy(node)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddProxyNodeDialog(
    onDismiss: () -> Unit,
    onSave: (ProxyNode) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(ProxyProtocol.SOCKS5) }
    var host by remember { mutableStateOf("127.0.0.1") }
    var portText by remember { mutableStateOf("1080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加代理节点", maxLines = 1, softWrap = false) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProxyProtocol.entries.forEach { item ->
                        FilterChip(
                            selected = protocol == item,
                            onClick = { protocol = item },
                            label = { Text(item.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称（可选）", maxLines = 1, softWrap = false) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址", maxLines = 1, softWrap = false) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("端口", maxLines = 1, softWrap = false) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名（可选）", maxLines = 1, softWrap = false) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（可选）", maxLines = 1, softWrap = false) },
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
                            password = password.trim()
                        )
                    )
                }
            ) {
                Text("保存", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", maxLines = 1, softWrap = false) }
        }
    )
}
