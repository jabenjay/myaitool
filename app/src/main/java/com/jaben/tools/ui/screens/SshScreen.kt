package com.jaben.tools.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.tools.ssh.SshAuthType
import com.jaben.tools.tools.ssh.SshEngine
import com.jaben.tools.tools.ssh.SshRemoteFile
import com.jaben.tools.tools.ssh.SshRepository
import com.jaben.tools.tools.ssh.SshServerProfile
import com.jaben.tools.tools.ssh.SshServerStatus
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visuals = LocalAppThemeVisuals.current
    val repo = remember { SshRepository(context) }

    var servers by remember { mutableStateOf(repo.loadServers()) }
    var editingServer by remember { mutableStateOf<SshServerProfile?>(null) }
    var showServerEditor by remember { mutableStateOf(false) }

    var activeProfile by remember { mutableStateOf<SshServerProfile?>(null) }
    var activeSession by remember { mutableStateOf<Session?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf(SshServerStatus()) }

    var workspaceTab by remember { mutableIntStateOf(0) } // 0: SFTP 文件, 1: SSH 终端
    var currentPath by remember { mutableStateOf("/") }
    var remoteFiles by remember { mutableStateOf<List<SshRemoteFile>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }

    var selectedFileForAction by remember { mutableStateOf<SshRemoteFile?>(null) }
    var pendingDownloadFile by remember { mutableStateOf<SshRemoteFile?>(null) }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var mkdirName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<SshRemoteFile?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SshRemoteFile?>(null) }

    var previewFile by remember { mutableStateOf<SshRemoteFile?>(null) }
    var previewContent by remember { mutableStateOf("") }
    var isSavingPreview by remember { mutableStateOf(false) }

    var terminalLog by remember { mutableStateOf("已连接 SSH 终端，输入命令后回车执行。\n") }
    var commandInput by remember { mutableStateOf("") }
    var isRunningCmd by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            val s = activeSession
            activeSession = null
            if (s != null) {
                Thread { runCatching { s.disconnect() } }.start()
            }
        }
    }

    fun refreshDirectory(path: String = currentPath) {
        val sess = activeSession ?: return
        scope.launch {
            isLoadingFiles = true
            runCatching {
                SshEngine.listFiles(sess, path)
            }.onSuccess { list ->
                remoteFiles = list
                currentPath = path
            }.onFailure { err ->
                Toast.makeText(context, "读取目录失败: ${err.message}", Toast.LENGTH_SHORT).show()
            }
            isLoadingFiles = false
        }
    }

    fun refreshStatus() {
        val sess = activeSession ?: return
        scope.launch {
            runCatching { SshEngine.fetchServerStatus(sess) }
                .onSuccess { serverStatus = it }
        }
    }

    fun connectToServer(profile: SshServerProfile) {
        if (isConnecting) return
        scope.launch {
            isConnecting = true
            runCatching {
                SshEngine.connect(profile)
            }.onSuccess { sess ->
                activeSession = sess
                activeProfile = profile
                servers = repo.touchLastConnected(profile.id)
                currentPath = profile.defaultPath.ifBlank { "/" }
                terminalLog = "Connected to ${profile.username}@${profile.host}:${profile.port}\n"
                refreshStatus()
                refreshDirectory(currentPath)
            }.onFailure { err ->
                Toast.makeText(context, "连接失败: ${err.message}", Toast.LENGTH_LONG).show()
            }
            isConnecting = false
        }
    }

    fun disconnect() {
        val sess = activeSession
        activeSession = null
        activeProfile = null
        remoteFiles = emptyList()
        Thread { runCatching { sess?.disconnect() } }.start()
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val sess = activeSession ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = resolveFileName(context, uri) ?: "upload_${System.currentTimeMillis()}"
        val destPath = if (currentPath == "/") "/$fileName" else "${currentPath.trimEnd('/')}/$fileName"
        scope.launch {
            isLoadingFiles = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        SshEngine.uploadFile(sess, input, destPath)
                    } ?: error("无法读取本地文件")
                }
            }.onSuccess {
                Toast.makeText(context, "上传成功: $fileName", Toast.LENGTH_SHORT).show()
                refreshDirectory(currentPath)
            }.onFailure { err ->
                Toast.makeText(context, "上传失败: ${err.message}", Toast.LENGTH_LONG).show()
                isLoadingFiles = false
            }
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val sess = activeSession ?: return@rememberLauncherForActivityResult
        val target = pendingDownloadFile ?: return@rememberLauncherForActivityResult
        pendingDownloadFile = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoadingFiles = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        SshEngine.downloadFile(sess, target.fullPath, output)
                    } ?: error("无法写入目标文件")
                }
            }.onSuccess {
                Toast.makeText(context, "下载完成: ${target.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "下载失败: ${err.message}", Toast.LENGTH_LONG).show()
            }
            isLoadingFiles = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (activeSession != null) disconnect() else onBack()
            }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeProfile?.name ?: "SSH 工具",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (activeProfile != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B8E3E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${activeProfile!!.username}@${activeProfile!!.host}:${activeProfile!!.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (activeSession != null) {
                IconButton(onClick = {
                    refreshStatus()
                    if (workspaceTab == 0) refreshDirectory(currentPath)
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = { disconnect() }) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = "断开", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                FilledTonalButton(onClick = {
                    editingServer = null
                    showServerEditor = true
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加")
                }
            }
        }

        if (isConnecting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在连接 SSH 服务器...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (activeSession == null) {
            // 服务器列表页
            if (servers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = visuals.cardContainerColor),
                        modifier = Modifier.fillMaxWidth().liquidGlassSurface(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Rounded.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("暂无保存的服务器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "支持密码与私钥登录、SFTP 文件上传下载删除及终端命令执行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                editingServer = null
                                showServerEditor = true
                            }) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("添加服务器")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(servers, key = { it.id }) { server ->
                        Card(
                            onClick = { connectToServer(server) },
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = visuals.cardContainerColor),
                            modifier = Modifier.fillMaxWidth().liquidGlassSurface(22.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Terminal,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.name.ifBlank { server.host },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${server.username}@${server.host}:${server.port}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = {
                                        editingServer = server
                                        showServerEditor = true
                                    }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "编辑", modifier = Modifier.size(19.dp))
                                    }
                                    IconButton(onClick = {
                                        servers = repo.deleteServer(server.id)
                                    }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${server.authType.label} · 初始路径 ${server.defaultPath}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (server.lastConnectedAtMs > 0) "最近: ${formatTime(server.lastConnectedAtMs)}" else "点击连接",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 已连接工作台（ServerBox 风格状态卡 + SFTP / 终端切换）
            Column(modifier = Modifier.fillMaxSize()) {
                // 系统状态概览卡片
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = visuals.cardContainerColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .liquidGlassSurface(18.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("系统: ${serverStatus.osInfo}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("运行: ${serverStatus.uptime}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("内存: ${serverStatus.memoryInfo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("磁盘: ${serverStatus.diskInfo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    SegmentedButton(
                        selected = workspaceTab == 0,
                        onClick = { workspaceTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("SFTP 文件")
                    }
                    SegmentedButton(
                        selected = workspaceTab == 1,
                        onClick = { workspaceTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("SSH 终端")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (workspaceTab == 0) {
                    // SFTP 工具条
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPath != "/") {
                                    val parent = currentPath.trimEnd('/').substringBeforeLast('/', "")
                                    refreshDirectory(if (parent.isEmpty()) "/" else parent)
                                }
                            },
                            enabled = currentPath != "/"
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "上一级")
                        }
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = "上传文件")
                        }
                        IconButton(onClick = {
                            mkdirName = ""
                            showMkdirDialog = true
                        }) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "新建文件夹")
                        }
                    }

                    if (isLoadingFiles) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(remoteFiles, key = { it.fullPath }) { item ->
                                Card(
                                    onClick = {
                                        if (item.isDir) {
                                            refreshDirectory(item.fullPath)
                                        } else {
                                            selectedFileForAction = item
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = visuals.cardContainerColor),
                                    modifier = Modifier.fillMaxWidth().liquidGlassSurface(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (item.isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = if (item.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    if (!item.isDir) append("${formatSize(item.size)} · ")
                                                    append(item.permissions)
                                                    if (item.modifiedAtMs > 0) append(" · ${formatTime(item.modifiedAtMs)}")
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!item.isDir) {
                                            IconButton(onClick = {
                                                pendingDownloadFile = item
                                                downloadLauncher.launch(item.name)
                                            }) {
                                                Icon(Icons.Rounded.Download, contentDescription = "下载", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        IconButton(onClick = { deleteTarget = item }) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = "删除",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // SSH 终端
                    val quickCmds = listOf("ls -la", "df -h", "free -h", "uptime", "ps aux | head -n 15")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickCmds.forEach { qc ->
                            OutlinedButton(
                                onClick = { commandInput = qc },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(qc, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0xFF111418),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = terminalLog,
                                color = Color(0xFFE6EDF3),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            placeholder = { Text("输入 Shell 命令...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                val cmd = commandInput.trim()
                                val sess = activeSession
                                if (cmd.isNotEmpty() && sess != null && !isRunningCmd) {
                                    commandInput = ""
                                    isRunningCmd = true
                                    terminalLog += "\n$ $cmd\n"
                                    scope.launch {
                                        runCatching { SshEngine.runCommand(sess, cmd, currentPath) }
                                            .onSuccess { res ->
                                                if (res.output.isNotEmpty()) terminalLog += "${res.output}\n"
                                            }
                                            .onFailure { err ->
                                                terminalLog += "[Error] ${err.message}\n"
                                            }
                                        isRunningCmd = false
                                    }
                                }
                            }),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val cmd = commandInput.trim()
                                val sess = activeSession
                                if (cmd.isNotEmpty() && sess != null && !isRunningCmd) {
                                    commandInput = ""
                                    isRunningCmd = true
                                    terminalLog += "\n$ $cmd\n"
                                    scope.launch {
                                        runCatching { SshEngine.runCommand(sess, cmd, currentPath) }
                                            .onSuccess { res ->
                                                if (res.output.isNotEmpty()) terminalLog += "${res.output}\n"
                                            }
                                            .onFailure { err ->
                                                terminalLog += "[Error] ${err.message}\n"
                                            }
                                        isRunningCmd = false
                                    }
                                }
                            },
                            enabled = !isRunningCmd
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "执行")
                        }
                    }
                }
            }
        }
    }

    // 服务器编辑弹窗
    if (showServerEditor) {
        SshServerEditorDialog(
            initial = editingServer,
            onDismiss = { showServerEditor = false },
            onSave = { saved ->
                servers = repo.upsertServer(saved)
                showServerEditor = false
            }
        )
    }

    // 文件操作弹窗
    selectedFileForAction?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedFileForAction = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("路径: ${file.fullPath}", style = MaterialTheme.typography.bodySmall)
                    Text("大小: ${formatSize(file.size)} · 权限: ${file.permissions}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    TextButton(onClick = {
                        selectedFileForAction = null
                        pendingDownloadFile = file
                        downloadLauncher.launch(file.name)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("下载到本地")
                    }
                    TextButton(onClick = {
                        val sess = activeSession
                        selectedFileForAction = null
                        if (sess != null) {
                            scope.launch {
                                runCatching { SshEngine.readTextFile(sess, file.fullPath) }
                                    .onSuccess { txt ->
                                        previewContent = txt
                                        previewFile = file
                                    }
                                    .onFailure { err ->
                                        Toast.makeText(context, "读取失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("查看 / 编辑文本")
                    }
                    TextButton(onClick = {
                        selectedFileForAction = null
                        renameTarget = file
                        renameInput = file.name
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("重命名")
                    }
                    TextButton(onClick = {
                        selectedFileForAction = null
                        deleteTarget = file
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFileForAction = null }) { Text("关闭") }
            }
        )
    }

    // 文本查看/编辑弹窗
    previewFile?.let { file ->
        AlertDialog(
            onDismissRequest = { previewFile = null },
            title = { Text("编辑 ${file.name}") },
            text = {
                OutlinedTextField(
                    value = previewContent,
                    onValueChange = { previewContent = it },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            },
            confirmButton = {
                Button(
                    enabled = !isSavingPreview,
                    onClick = {
                        val sess = activeSession ?: return@Button
                        isSavingPreview = true
                        scope.launch {
                            runCatching { SshEngine.writeTextFile(sess, file.fullPath, previewContent) }
                                .onSuccess {
                                    Toast.makeText(context, "已保存至服务器", Toast.LENGTH_SHORT).show()
                                    previewFile = null
                                    refreshDirectory(currentPath)
                                }
                                .onFailure { err ->
                                    Toast.makeText(context, "保存失败: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            isSavingPreview = false
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { previewFile = null }) { Text("取消") }
            }
        )
    }

    // 新建文件夹弹窗
    if (showMkdirDialog) {
        AlertDialog(
            onDismissRequest = { showMkdirDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = mkdirName,
                    onValueChange = { mkdirName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val name = mkdirName.trim()
                    val sess = activeSession
                    if (name.isNotEmpty() && sess != null) {
                        val target = if (currentPath == "/") "/$name" else "${currentPath.trimEnd('/')}/$name"
                        showMkdirDialog = false
                        scope.launch {
                            runCatching { SshEngine.createDirectory(sess, target) }
                                .onSuccess { refreshDirectory(currentPath) }
                                .onFailure { Toast.makeText(context, "创建失败: ${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false }) { Text("取消") }
            }
        )
    }

    // 重命名弹窗
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("新名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val newName = renameInput.trim()
                    val sess = activeSession
                    if (newName.isNotEmpty() && sess != null) {
                        val parent = target.fullPath.substringBeforeLast('/', "")
                        val newPath = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
                        renameTarget = null
                        scope.launch {
                            runCatching { SshEngine.renameRemote(sess, target.fullPath, newPath) }
                                .onSuccess { refreshDirectory(currentPath) }
                                .onFailure { Toast.makeText(context, "重命名失败: ${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    // 删除确认弹窗
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定删除「${target.name}」吗？${if (target.isDir) "将递归删除目录内全部文件。" else ""}") },
            confirmButton = {
                Button(onClick = {
                    val sess = activeSession
                    deleteTarget = null
                    if (sess != null) {
                        scope.launch {
                            runCatching { SshEngine.deleteRemote(sess, target) }
                                .onSuccess { refreshDirectory(currentPath) }
                                .onFailure { Toast.makeText(context, "删除失败: ${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SshServerEditorDialog(
    initial: SshServerProfile?,
    onDismiss: () -> Unit,
    onSave: (SshServerProfile) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "root") }
    var authType by remember { mutableStateOf(initial?.authType ?: SshAuthType.PASSWORD) }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var privateKey by remember { mutableStateOf(initial?.privateKey ?: "") }
    var keyPassphrase by remember { mutableStateOf(initial?.keyPassphrase ?: "") }
    var defaultPath by remember { mutableStateOf(initial?.defaultPath ?: "/") }

    val keyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            }.onSuccess { content ->
                if (content.isNotBlank()) privateKey = content
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加 SSH 服务器" else "编辑服务器") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("备注名称") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("主机地址 (IP / 域名)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SshAuthType.entries.forEachIndexed { idx, type ->
                        SegmentedButton(
                            selected = authType == type,
                            onClick = { authType = type },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = SshAuthType.entries.size)
                        ) {
                            Text(type.label, fontSize = 12.sp)
                        }
                    }
                }

                if (authType == SshAuthType.PASSWORD) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                } else {
                    OutlinedButton(onClick = { keyFileLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("从文件导入私钥")
                    }
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("私钥内容 (PEM / OpenSSH)") },
                        modifier = Modifier.height(100.dp)
                    )
                    OutlinedTextField(
                        value = keyPassphrase,
                        onValueChange = { keyPassphrase = it },
                        label = { Text("私钥密码（无密码留空）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }

                OutlinedTextField(
                    value = defaultPath,
                    onValueChange = { defaultPath = it },
                    label = { Text("默认远程目录") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = host.isNotBlank() && username.isNotBlank(),
                onClick = {
                    val p = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22
                    onSave(
                        SshServerProfile(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { host.trim() },
                            host = host.trim(),
                            port = p,
                            username = username.trim(),
                            authType = authType,
                            password = password,
                            privateKey = privateKey,
                            keyPassphrase = keyPassphrase,
                            defaultPath = defaultPath.trim().ifBlank { "/" },
                            lastConnectedAtMs = initial?.lastConnectedAtMs ?: 0L
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1fKB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1fMB", mb)
    return String.format(Locale.US, "%.2fGB", mb / 1024.0)
}

private fun formatTime(ms: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(Date(ms))
}
