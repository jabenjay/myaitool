package com.jaben.tools.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.tools.ssh.SshAuthType
import com.jaben.tools.tools.ssh.SshEngine
import com.jaben.tools.tools.ssh.SshKeyItem
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
import java.util.UUID

private enum class SshPage {
    SERVER_LIST,
    SERVER_EDITOR,
    KEY_MANAGER,
    KEY_EDITOR,
    TERMINAL,
    SFTP_FILES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val visuals = LocalAppThemeVisuals.current
    val repo = remember { SshRepository(context) }

    var page by remember { mutableStateOf(SshPage.SERVER_LIST) }
    var servers by remember { mutableStateOf(repo.loadServers()) }
    var sshKeys by remember { mutableStateOf(repo.loadKeys()) }

    // Server editor draft state (persisted across jump to KEY_EDITOR and back!)
    var editingServerId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftHost by remember { mutableStateOf("") }
    var draftPort by remember { mutableStateOf("22") }
    var draftUsername by remember { mutableStateOf("root") }
    var draftAuthType by remember { mutableStateOf(SshAuthType.PASSWORD) }
    var draftPassword by remember { mutableStateOf("") }
    var draftKeyId by remember { mutableStateOf("") }
    var draftExistingPassphrase by remember { mutableStateOf("") }
    var draftPassphraseInput by remember { mutableStateOf("") }
    var draftLastConnected by remember { mutableStateOf(0L) }
    var keyDropdownExpanded by remember { mutableStateOf(false) }

    // Key editor state
    var keyReturnToServerEditor by remember { mutableStateOf(false) }
    var selectedKeyForDetail by remember { mutableStateOf<SshKeyItem?>(null) }
    var keyNameInput by remember { mutableStateOf("") }
    var keyPemInput by remember { mutableStateOf("") }
    var generatedPublicKey by remember { mutableStateOf("") }

    // Active connection state
    var activeProfile by remember { mutableStateOf<SshServerProfile?>(null) }
    var activeSession by remember { mutableStateOf<Session?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf(SshServerStatus()) }

    // SFTP state
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

    // Terminal state
    var terminalLog by remember { mutableStateOf("") }
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

    fun openServerEditor(profile: SshServerProfile?) {
        editingServerId = profile?.id
        draftName = profile?.name ?: ""
        draftHost = profile?.host ?: ""
        draftPort = (profile?.port ?: 22).toString()
        draftUsername = profile?.username ?: "root"
        draftAuthType = profile?.authType ?: SshAuthType.PASSWORD
        draftPassword = profile?.password ?: ""
        draftKeyId = profile?.keyId?.ifBlank { sshKeys.firstOrNull()?.id ?: "" } ?: (sshKeys.firstOrNull()?.id ?: "")
        draftExistingPassphrase = profile?.keyPassphrase ?: ""
        draftPassphraseInput = ""
        draftLastConnected = profile?.lastConnectedAtMs ?: 0L
        page = SshPage.SERVER_EDITOR
    }

    fun openKeyEditor(returnToServer: Boolean) {
        keyReturnToServerEditor = returnToServer
        keyNameInput = ""
        keyPemInput = ""
        generatedPublicKey = ""
        page = SshPage.KEY_EDITOR
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
            val resolvedKey = repo.resolvePrivateKey(profile)
            runCatching {
                SshEngine.connect(profile, resolvedKey)
            }.onSuccess { sess ->
                activeSession = sess
                activeProfile = profile
                servers = repo.touchLastConnected(profile.id)
                val homeDir = runCatching { SshEngine.getDefaultHomeDirectory(sess) }.getOrDefault("/")
                currentPath = homeDir
                terminalLog = "Connected to ${profile.username}@${profile.host}:${profile.port}\nDefault path: $homeDir\n"
                page = SshPage.TERMINAL
                refreshStatus()
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
        page = SshPage.SERVER_LIST
        Thread { runCatching { sess?.disconnect() } }.start()
    }

    fun handleBackNavigation() {
        when (page) {
            SshPage.SERVER_LIST -> onBack()
            SshPage.SERVER_EDITOR -> page = SshPage.SERVER_LIST
            SshPage.KEY_MANAGER -> page = SshPage.SERVER_LIST
            SshPage.KEY_EDITOR -> page = if (keyReturnToServerEditor) SshPage.SERVER_EDITOR else SshPage.KEY_MANAGER
            SshPage.SFTP_FILES -> page = SshPage.TERMINAL
            SshPage.TERMINAL -> disconnect()
        }
    }

    BackHandler(enabled = page != SshPage.SERVER_LIST) {
        handleBackNavigation()
    }

    val keyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            }.onSuccess { content ->
                if (content.isNotBlank()) {
                    keyPemInput = content
                            SshEngine.derivePublicKey(content)?.let { generatedPublicKey = it }
                    if (keyNameInput.isBlank()) {
                        keyNameInput = resolveFileName(context, uri) ?: "导入私钥"
                    }
                }
            }
        }
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
            IconButton(onClick = { handleBackNavigation() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (page) {
                        SshPage.SERVER_LIST -> "SSH 工具"
                        SshPage.SERVER_EDITOR -> if (editingServerId == null) "添加主机" else "编辑主机"
                        SshPage.KEY_MANAGER -> "私钥管理"
                        SshPage.KEY_EDITOR -> "添加新密钥"
                        SshPage.TERMINAL -> activeProfile?.name ?: "SSH 终端"
                        SshPage.SFTP_FILES -> "文件管理 · ${activeProfile?.name ?: ""}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if ((page == SshPage.TERMINAL || page == SshPage.SFTP_FILES) && activeProfile != null) {
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

            when (page) {
                SshPage.SERVER_LIST -> {
                    IconButton(onClick = { page = SshPage.KEY_MANAGER }) {
                        Icon(Icons.Rounded.Key, contentDescription = "私钥管理", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    FilledTonalButton(onClick = { openServerEditor(null) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加")
                    }
                }
                SshPage.KEY_MANAGER -> {
                    FilledTonalButton(onClick = { openKeyEditor(returnToServer = false) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建/导入")
                    }
                }
                SshPage.TERMINAL -> {
                    IconButton(onClick = {
                        page = SshPage.SFTP_FILES
                        refreshDirectory(currentPath)
                    }) {
                        Icon(Icons.Rounded.Folder, contentDescription = "文件管理", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { refreshStatus() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { disconnect() }) {
                        Icon(Icons.Rounded.LinkOff, contentDescription = "断开", tint = MaterialTheme.colorScheme.error)
                    }
                }
                SshPage.SFTP_FILES -> {
                    IconButton(onClick = { refreshDirectory(currentPath) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = "上传", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        mkdirName = ""
                        showMkdirDialog = true
                    }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "新建文件夹", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                else -> {}
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
        } else {
            when (page) {
                SshPage.SERVER_LIST -> {
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
                                        "支持密码与私钥管理、SFTP 文件传输及 SSH 终端",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { openServerEditor(null) }) {
                                        Icon(Icons.Rounded.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("添加主机")
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
                                            IconButton(onClick = { openServerEditor(server) }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "编辑", modifier = Modifier.size(19.dp))
                                            }
                                            IconButton(onClick = { servers = repo.deleteServer(server.id) }) {
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
                                                text = server.authType.label,
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
                }

                SshPage.SERVER_EDITOR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = draftName,
                            onValueChange = { draftName = it },
                            label = { Text("主机名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draftHost,
                            onValueChange = { draftHost = it },
                            label = { Text("主机地址 (IP / 域名)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = draftUsername,
                                onValueChange = { draftUsername = it },
                                label = { Text("用户名") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = draftPort,
                                onValueChange = { draftPort = it.filter { c -> c.isDigit() } },
                                label = { Text("端口") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(100.dp)
                            )
                        }

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SshAuthType.entries.forEachIndexed { idx, type ->
                                SegmentedButton(
                                    selected = draftAuthType == type,
                                    onClick = { draftAuthType = type },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = SshAuthType.entries.size)
                                ) {
                                    Text(type.label)
                                }
                            }
                        }

                        if (draftAuthType == SshAuthType.PASSWORD) {
                            OutlinedTextField(
                                value = draftPassword,
                                onValueChange = { draftPassword = it },
                                label = { Text("登录密码") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // 私钥选择下拉框（按保存时间顺序，最下方为“添加新密钥”）
                            val selectedKey = sshKeys.firstOrNull { it.id == draftKeyId }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedKey?.name ?: "请选择私钥",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("选择私钥") },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { keyDropdownExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = keyDropdownExpanded,
                                    onDismissRequest = { keyDropdownExpanded = false }
                                ) {
                                    sshKeys.forEach { k ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(k.name, fontWeight = FontWeight.Medium)
                                                    Text(
                                                        formatTime(k.createdAtMs),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                draftKeyId = k.id
                                                keyDropdownExpanded = false
                                            }
                                        )
                                    }
                                    if (sshKeys.isNotEmpty()) {
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        },
                                        text = {
                                            Text("添加新密钥", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        },
                                        onClick = {
                                            keyDropdownExpanded = false
                                            openKeyEditor(returnToServer = true)
                                        }
                                    )
                                }
                            }

                            val isEditingExistingHost = editingServerId != null && draftExistingPassphrase.isNotEmpty()
                            OutlinedTextField(
                                value = draftPassphraseInput,
                                onValueChange = { draftPassphraseInput = it },
                                label = { Text("密钥密码") },
                                placeholder = {
                                    Text(if (isEditingExistingHost) "输入以更改私钥密码" else "无密码可留空")
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            enabled = draftHost.isNotBlank() && draftUsername.isNotBlank() &&
                                (draftAuthType == SshAuthType.PASSWORD || draftKeyId.isNotBlank()),
                            onClick = {
                                val p = draftPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
                                val finalPassphrase = if (draftPassphraseInput.isNotEmpty()) {
                                    draftPassphraseInput
                                } else {
                                    draftExistingPassphrase
                                }
                                val saved = SshServerProfile(
                                    id = editingServerId ?: UUID.randomUUID().toString(),
                                    name = draftName.trim().ifBlank { draftHost.trim() },
                                    host = draftHost.trim(),
                                    port = p,
                                    username = draftUsername.trim(),
                                    authType = draftAuthType,
                                    password = draftPassword,
                                    keyId = draftKeyId,
                                    privateKey = "",
                                    keyPassphrase = finalPassphrase,
                                    lastConnectedAtMs = draftLastConnected
                                )
                                servers = repo.upsertServer(saved)
                                page = SshPage.SERVER_LIST
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("保存主机配置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                SshPage.KEY_MANAGER -> {
                    if (sshKeys.isEmpty()) {
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
                                    Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("暂无保存的私钥", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(onClick = { openKeyEditor(returnToServer = false) }) {
                                        Text("新建或导入密钥")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sshKeys, key = { it.id }) { keyItem ->
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = visuals.cardContainerColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .liquidGlassSurface(18.dp)
                                        .clickable { selectedKeyForDetail = keyItem }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(keyItem.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                "保存于 ${formatTime(keyItem.createdAtMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { sshKeys = repo.deleteKey(keyItem.id) }) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SshPage.KEY_EDITOR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = keyNameInput,
                            onValueChange = { keyNameInput = it },
                            label = { Text("密钥名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { keyFileLauncher.launch(arrayOf("*/*")) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("从文件导入", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = {
                                    runCatching { SshEngine.generateRsaKeyPair() }
                                        .onSuccess { (priv, pub) ->
                                            keyPemInput = priv
                                            generatedPublicKey = pub
                                            if (keyNameInput.isBlank()) keyNameInput = "RSA_${System.currentTimeMillis() % 10000}"
                                        }
                                        .onFailure { Toast.makeText(context, "生成失败: ${it.message}", Toast.LENGTH_SHORT).show() }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("新建 RSA 密钥", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (generatedPublicKey.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("已生成公钥（可复制到服务器 authorized_keys）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                        TextButton(onClick = {
                                            clipboard.setText(AnnotatedString(generatedPublicKey))
                                            Toast.makeText(context, "已复制公钥", Toast.LENGTH_SHORT).show()
                                        }) { Text("复制公钥") }
                                    }
                                    Text(
                                        generatedPublicKey,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = keyPemInput,
                            onValueChange = { nextVal ->
                                keyPemInput = nextVal
                                val derived = SshEngine.derivePublicKey(nextVal)
                                if (!derived.isNullOrBlank()) {
                                    generatedPublicKey = derived
                                } else if (nextVal.isBlank()) {
                                    generatedPublicKey = ""
                                }
                            },
                            label = { Text("私钥内容 (PEM / OpenSSH)") },
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )

                        Button(
                            enabled = keyPemInput.isNotBlank(),
                            onClick = {
                                val resolvedPub = generatedPublicKey.trim().ifBlank {
                                    SshEngine.derivePublicKey(keyPemInput.trim()).orEmpty()
                                }
                                val newItem = SshKeyItem(
                                    name = keyNameInput.trim().ifBlank { "密钥 ${sshKeys.size + 1}" },
                                    privateKey = keyPemInput.trim(),
                                    publicKey = resolvedPub,
                                    createdAtMs = System.currentTimeMillis()
                                )
                                sshKeys = repo.upsertKey(newItem)
                                if (keyReturnToServerEditor) {
                                    draftKeyId = newItem.id
                                    page = SshPage.SERVER_EDITOR
                                } else {
                                    page = SshPage.KEY_MANAGER
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("保存密钥", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                SshPage.TERMINAL -> {
                    Column(modifier = Modifier.fillMaxSize()) {
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

                        Spacer(modifier = Modifier.height(8.dp))

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

                SshPage.SFTP_FILES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    }
                }
            }
        }
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
    selectedKeyForDetail?.let { keyItem ->
        SshKeyDetailDialog(
            keyItem = keyItem,
            onDismiss = { selectedKeyForDetail = null },
            onSave = { updated ->
                sshKeys = repo.upsertKey(updated)
                selectedKeyForDetail = null
                Toast.makeText(context, "密钥已更新保存", Toast.LENGTH_SHORT).show()
            }
        )
    }

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

@Composable
private fun SshKeyDetailDialog(
    keyItem: SshKeyItem,
    onDismiss: () -> Unit,
    onSave: (SshKeyItem) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var editPublicKey by remember(keyItem.id) {
        mutableStateOf(keyItem.publicKey.ifBlank { SshEngine.derivePublicKey(keyItem.privateKey).orEmpty() })
    }
    var editPrivateKey by remember(keyItem.id) { mutableStateOf(keyItem.privateKey) }
    var showPrivateKey by remember(keyItem.id) { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }

    val hasChanges = editPublicKey.trim() != keyItem.publicKey.trim() ||
        editPrivateKey.trim() != keyItem.privateKey.trim()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = keyItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    TextButton(
                        enabled = hasChanges && editPrivateKey.isNotBlank(),
                        onClick = { showSaveConfirm = true }
                    ) {
                        Text(
                            text = "保存",
                            fontWeight = FontWeight.Bold,
                            color = if (hasChanges && editPrivateKey.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }

                // 公钥区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "公钥",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(editPublicKey))
                            Toast.makeText(context, "已复制公钥内容", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制公钥",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = editPublicKey,
                    onValueChange = { editPublicKey = it },
                    placeholder = { Text("暂无公钥，可粘贴或编辑") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                // 私钥区域（小眼睛在复制按钮左侧，默认不显示明文）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "私钥",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showPrivateKey = !showPrivateKey },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (showPrivateKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPrivateKey) "隐藏私钥" else "显示私钥",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(editPrivateKey))
                            Toast.makeText(context, "已复制私钥内容", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制私钥",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = editPrivateKey,
                    onValueChange = { editPrivateKey = it },
                    visualTransformation = if (showPrivateKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }

    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("确认保存") },
            text = { Text("确定保存对密钥“${keyItem.name}”的修改吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    onSave(
                        keyItem.copy(
                            publicKey = editPublicKey.trim(),
                            privateKey = editPrivateKey.trim()
                        )
                    )
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
