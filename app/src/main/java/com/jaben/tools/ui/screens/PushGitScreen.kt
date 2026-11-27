package com.jaben.tools.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.tools.git.GitDiffSummary
import com.jaben.tools.tools.git.PushFileEntry
import com.jaben.tools.data.OutputStorageHelper
import com.jaben.tools.tools.git.PushGitEngine
import java.io.ByteArrayInputStream
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.mutableStateMapOf
import com.jaben.tools.tools.git.WorkflowInfo
import com.jaben.tools.tools.git.WorkflowInputField
import com.jaben.tools.tools.git.WorkflowRunInfo
import kotlinx.coroutines.delay
import com.jaben.tools.ui.theme.liquidGlassSurface
import kotlinx.coroutines.launch

@Composable
fun PushGitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { PushGitEngine(context) }

    var pat by remember { mutableStateOf(engine.loadSavedToken()) }
    var connectedUser by remember { mutableStateOf<String?>(null) }

    var savedTokens by remember { mutableStateOf(engine.getSavedTokens()) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var showTokenManagerDialog by remember { mutableStateOf(false) }
    var newTokenInput by remember { mutableStateOf("") }

    var targetRepo by remember { mutableStateOf(engine.loadSavedRepo()) }
    var repos by remember { mutableStateOf<List<String>>(emptyList()) }
    var repoMenuExpanded by remember { mutableStateOf(false) }

    var branch by remember { mutableStateOf(engine.loadSavedBranch()) }
    var branches by remember { mutableStateOf(listOf("main")) }
    var commitMessage by remember { mutableStateOf(engine.loadSavedMessage()) }

    var selectedFiles by remember { mutableStateOf<List<PushFileEntry>>(emptyList()) }
    var lastPickerMode by remember { mutableStateOf(engine.loadLastPickerMode()) }
    var diffSummary by remember { mutableStateOf<GitDiffSummary?>(null) }
    var syncDeletions by remember { mutableStateOf(false) }
    var forceFullPush by remember { mutableStateOf(false) }

    var isFetchingMeta by remember { mutableStateOf(false) }
    var isInspecting by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }
    var pushDone by remember { mutableIntStateOf(0) }
    var pushTotal by remember { mutableIntStateOf(0) }
    var pushStatus by remember { mutableStateOf("") }

    var workflows by remember { mutableStateOf<List<WorkflowInfo>>(emptyList()) }
    var selectedWorkflow by remember { mutableStateOf<WorkflowInfo?>(null) }
    var workflowInputs by remember { mutableStateOf<List<WorkflowInputField>>(emptyList()) }
    val workflowInputValues = remember { mutableStateMapOf<String, String>() }
    var showWorkflowPickerDialog by remember { mutableStateOf(false) }
    var showRunWorkflowDialog by remember { mutableStateOf(false) }
    var isLoadingWorkflowInputs by remember { mutableStateOf(false) }
    var isDispatchingWorkflow by remember { mutableStateOf(false) }
    var workflowRuns by remember { mutableStateOf<List<WorkflowRunInfo>>(emptyList()) }
    var showRunHistoryMenu by remember { mutableStateOf(false) }
    var isFetchingRuns by remember { mutableStateOf(false) }
    var failedRunLog by remember { mutableStateOf("") }
    var fetchedLogRunId by remember { mutableStateOf<Long?>(null) }
    var isFetchingFailedLog by remember { mutableStateOf(false) }

    fun refreshGitHubMeta(tokenToUse: String = pat) {
        if (tokenToUse.isBlank()) return
        scope.launch {
            isFetchingMeta = true
            engine.verifyToken(tokenToUse).onSuccess { user ->
                connectedUser = user
            }.onFailure {
                connectedUser = null
            }
            engine.listRepos(tokenToUse).onSuccess { list ->
                if (list.isNotEmpty()) {
                    repos = list
                    if (targetRepo.isBlank() || targetRepo !in list) {
                        targetRepo = list.first()
                    }
                }
            }
            engine.listBranches(tokenToUse, targetRepo).onSuccess { bList ->
                if (bList.isNotEmpty()) branches = bList
            }
            engine.listWorkflows(tokenToUse, targetRepo).onSuccess { wfList ->
                workflows = wfList
                if (selectedWorkflow == null || wfList.none { it.id == selectedWorkflow?.id }) {
                    selectedWorkflow = wfList.firstOrNull()
                }
            }
            engine.listWorkflowRuns(tokenToUse, targetRepo).onSuccess { runs ->
                workflowRuns = runs
            }
            isFetchingMeta = false
        }
    }

    fun openWorkflowInputDialog(wf: WorkflowInfo) {
        selectedWorkflow = wf
        scope.launch {
            isLoadingWorkflowInputs = true
            val nextVer = engine.fetchNextReleaseVersion(pat, targetRepo)
            val remoteChangelog = engine.fetchChangelogContent(pat, targetRepo, branch)
            engine.fetchWorkflowInputs(pat, targetRepo, wf.path, branch)
                .onSuccess { fields ->
                    workflowInputs = fields
                    workflowInputValues.clear()
                    fields.forEach { f ->
                        val prefill = when {
                            f.options.isNotEmpty() -> f.defaultValue.takeIf { it in f.options } ?: f.options.first()
                            f.key.contains("version", ignoreCase = true) && !nextVer.isNullOrBlank() -> nextVer
                            f.key.contains("changelog", ignoreCase = true) && !remoteChangelog.isNullOrBlank() -> remoteChangelog
                            else -> f.defaultValue
                        }
                        workflowInputValues[f.key] = prefill
                    }
                    showRunWorkflowDialog = true
                }
                .onFailure {
                    workflowInputs = emptyList()
                    showRunWorkflowDialog = true
                }
            isLoadingWorkflowInputs = false
        }
    }

    LaunchedEffect(Unit) {
        if (pat.isNotBlank()) refreshGitHubMeta(pat)
    }

    val activeRuns = workflowRuns.filter { it.status != "completed" }
    val latestRun = workflowRuns.firstOrNull()
    LaunchedEffect(activeRuns.size, latestRun?.id, latestRun?.status, latestRun?.conclusion, pat, targetRepo) {
        if (pat.isNotBlank() && activeRuns.isNotEmpty()) {
            delay(5000)
            engine.listWorkflowRuns(pat, targetRepo).onSuccess { runs ->
                workflowRuns = runs
            }
        } else if (latestRun != null && latestRun.conclusion == "failure" && fetchedLogRunId != latestRun.id && pat.isNotBlank()) {
            isFetchingFailedLog = true
            engine.fetchFailedRunLog(pat, targetRepo, latestRun.id)
                .onSuccess { logText ->
                    failedRunLog = logText
                    fetchedLogRunId = latestRun.id
                }
                .onFailure { err ->
                    failedRunLog = "获取日志失败: ${err.message}"
                }
            isFetchingFailedLog = false
        } else if (latestRun?.conclusion == "success") {
            failedRunLog = ""
            fetchedLogRunId = null
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                engine.readZipUri(uri).onSuccess { list ->
                    lastPickerMode = "zip"
                    engine.saveLastPickerMode("zip")
                    selectedFiles = list
                    diffSummary = null
                    pushStatus = ""
                    Toast.makeText(context, "已解析 ${list.size} 个文件", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "读取压缩包失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                engine.readFolderUri(uri).onSuccess { list ->
                    lastPickerMode = "folder"
                    engine.saveLastPickerMode("folder")
                    selectedFiles = list
                    diffSummary = null
                    pushStatus = ""
                    Toast.makeText(context, "已读取 ${list.size} 个文件", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "读取文件夹失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                engine.readFileUris(uris, "").onSuccess { list ->
                    lastPickerMode = "files"
                    engine.saveLastPickerMode("files")
                    selectedFiles = list
                    diffSummary = null
                    pushStatus = ""
                    Toast.makeText(context, "已选择 ${list.size} 个文件", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "PushGit",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            if (!connectedUser.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B8E3E)
                ) {
                    Text(
                        text = "@$connectedUser",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showTokenManagerDialog = true }) {
                Icon(Icons.Rounded.Key, contentDescription = "管理 Token")
            }
            IconButton(
                onClick = { refreshGitHubMeta(pat) },
                enabled = !isFetchingMeta
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新仓库")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Saved Account Selector + Target Repository + PAT
            Surface(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(cornerRadius = 18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("用户与目标仓库", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    // Saved Account / Token Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (savedTokens.isNotEmpty()) accountMenuExpanded = true
                                    else showTokenManagerDialog = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (savedTokens.isEmpty()) {
                                        "点击右上角钥匙图标保存 Token"
                                    } else {
                                        "已存账号: ${connectedUser?.let { "@$it" } ?: "选择用户"} (${savedTokens.size})"
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("▾", fontSize = 12.sp)
                            }
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            savedTokens.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("@${acc.username} (${acc.token.take(8)}...)") },
                                    onClick = {
                                        accountMenuExpanded = false
                                        pat = acc.token
                                        connectedUser = acc.username
                                        diffSummary = null
                                        refreshGitHubMeta(acc.token)
                                    }
                                )
                            }
                        }
                    }

                    // Target Repository Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { if (repos.isNotEmpty()) repoMenuExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = targetRepo.ifBlank { "选择目标仓库" },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(if (repos.isEmpty()) "待拉取" else "▾ (${repos.size})", fontSize = 12.sp)
                            }
                        }
                        DropdownMenu(
                            expanded = repoMenuExpanded,
                            onDismissRequest = { repoMenuExpanded = false }
                        ) {
                            repos.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r) },
                                    onClick = {
                                        repoMenuExpanded = false
                                        targetRepo = r
                                        diffSummary = null
                                        scope.launch {
                                            engine.listBranches(pat, r).onSuccess {
                                                if (it.isNotEmpty()) branches = it
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (!connectedUser.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF1B8E3E),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Connected as @$connectedUser",
                                fontSize = 12.sp,
                                color = Color(0xFF1B8E3E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Card 2: Branch & Commit
            Surface(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(cornerRadius = 18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("分支与提交信息", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = branch,
                        onValueChange = {
                            branch = it
                            diffSummary = null
                        },
                        label = { Text("Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(branches) { b ->
                            FilterChip(
                                selected = branch == b,
                                onClick = {
                                    branch = b
                                    diffSummary = null
                                },
                                label = { Text(b, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit Message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Card 3: Native Android File Picker & Diff Preview
            Surface(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(cornerRadius = 18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("选择上传内容", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PickerModeButton(
                            label = "ZIP",
                            icon = Icons.Rounded.FolderZip,
                            selected = lastPickerMode == "zip",
                            onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                        PickerModeButton(
                            label = "文件夹",
                            icon = Icons.Rounded.Folder,
                            selected = lastPickerMode == "folder",
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.weight(1f)
                        )
                        PickerModeButton(
                            label = "文件",
                            icon = Icons.Rounded.InsertDriveFile,
                            selected = lastPickerMode == "files",
                            onClick = { filesPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val diff = diffSummary
                    if (diff != null) {
                        Text(
                            text = "比对结果：新增 ${diff.addedFiles.size} · 修改 ${diff.modifiedFiles.size} · 远程独有 ${diff.deletedPaths.size} · 未变 ${diff.unchangedCount}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { forceFullPush = !forceFullPush }
                        ) {
                            Checkbox(checked = forceFullPush, onCheckedChange = { forceFullPush = it })
                            Text("全量覆盖推送（默认仅推送变动文件）", fontSize = 12.sp)
                        }
                        if (diff.deletedPaths.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { syncDeletions = !syncDeletions }
                            ) {
                                Checkbox(checked = syncDeletions, onCheckedChange = { syncDeletions = it })
                                Text("同步删除远程独有文件 (${diff.deletedPaths.size})", fontSize = 12.sp)
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(diff.addedFiles, key = { "add_${it.relativePath}" }) { f ->
                                DiffRowItem("新增", Color(0xFF1B8E3E), f.relativePath)
                            }
                            items(diff.modifiedFiles, key = { "mod_${it.relativePath}" }) { f ->
                                DiffRowItem("修改", Color(0xFFD97706), f.relativePath)
                            }
                            items(diff.deletedPaths, key = { "del_$it" }) { p ->
                                DiffRowItem("删除", Color(0xFFDC2626), p)
                            }
                        }
                    } else if (selectedFiles.isNotEmpty()) {
                        val totalKb = selectedFiles.sumOf { it.sizeBytes } / 1024.0
                        Text(
                            text = "已就绪 ${selectedFiles.size} 个文件 (${String.format("%.1f KB", totalKb)})，请先比对差异",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(selectedFiles, key = { it.relativePath }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.relativePath,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            selectedFiles = selectedFiles.filterNot { it.relativePath == entry.relativePath }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "移除", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isPushing && pushTotal > 0) {
                LinearProgressIndicator(
                    progress = { pushDone.toFloat() / pushTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (pushStatus.isNotEmpty()) {
                Text(
                    text = pushStatus,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bottom Action Button: First Inspect Diff, then Push Changes
        val currentDiff = diffSummary
        val readyToPushCount = if (currentDiff != null) {
            val uploadCnt = if (forceFullPush) selectedFiles.size else currentDiff.uploadFiles.size
            uploadCnt + if (syncDeletions) currentDiff.deletedPaths.size else 0
        } else 0

        Button(
            onClick = {
                engine.saveConfig(pat, targetRepo, branch, commitMessage)
                if (currentDiff == null) {
                    scope.launch {
                        isInspecting = true
                        pushStatus = "正在比对仓库文件..."
                        engine.inspectChanges(pat, targetRepo, branch, selectedFiles)
                            .onSuccess { diff ->
                                diffSummary = diff
                                pushStatus = if (diff.uploadFiles.isEmpty() && diff.deletedPaths.isEmpty()) {
                                    "与仓库完全一致，无需推送"
                                } else {
                                    "比对完成：${diff.addedFiles.size} 新增，${diff.modifiedFiles.size} 修改"
                                }
                            }
                            .onFailure { err ->
                                pushStatus = "比对失败: ${err.message}"
                                Toast.makeText(context, "比对失败", Toast.LENGTH_SHORT).show()
                            }
                        isInspecting = false
                    }
                } else {
                    scope.launch {
                        isPushing = true
                        pushDone = 0
                        pushTotal = if (forceFullPush) selectedFiles.size else currentDiff.uploadFiles.size
                        engine.pushDiff(
                            pat = pat,
                            ownerRepoInput = targetRepo,
                            branchInput = branch,
                            commitMessageInput = commitMessage,
                            diff = currentDiff,
                            syncDeletions = syncDeletions,
                            overrideFiles = if (forceFullPush) selectedFiles else null
                        ) { done, total, cur ->
                            pushDone = done
                            pushTotal = total
                            pushStatus = "推送中 ($done/$total): $cur"
                        }.onSuccess { res ->
                            pushStatus = "推送成功: ${res.fileCount} 个文件 (${res.commitSha.take(7)})"
                            diffSummary = null
                            Toast.makeText(context, "推送成功", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            pushStatus = "推送失败: ${err.message}"
                            Toast.makeText(context, "推送失败", Toast.LENGTH_SHORT).show()
                        }
                        isPushing = false
                    }
                }
            },
            enabled = !isInspecting && !isPushing && pat.isNotBlank() && selectedFiles.isNotEmpty() &&
                (currentDiff == null || readyToPushCount > 0),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp)
        ) {
            Icon(
                imageVector = if (currentDiff == null) Icons.Rounded.ManageSearch else Icons.Rounded.CloudUpload,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isInspecting -> "正在比对..."
                    isPushing -> "正在推送..."
                    currentDiff == null -> "比对仓库差异"
                    else -> if (forceFullPush) "全量 Push ($readyToPushCount)" else "Push 变动文件 ($readyToPushCount)"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isLoadingWorkflowInputs = true
                            val wfList = engine.listWorkflows(pat, targetRepo).getOrNull()?.takeIf { it.isNotEmpty() } ?: workflows
                            workflows = wfList
                            isLoadingWorkflowInputs = false
                            when {
                                wfList.isEmpty() -> {
                                    Toast.makeText(context, "未找到可用 Workflow", Toast.LENGTH_SHORT).show()
                                }
                                wfList.size == 1 -> {
                                    openWorkflowInputDialog(wfList.first())
                                }
                                else -> {
                                    showWorkflowPickerDialog = true
                                }
                            }
                        }
                    },
                    enabled = pat.isNotBlank() && targetRepo.isNotBlank() && !isLoadingWorkflowInputs && !isDispatchingWorkflow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLoadingWorkflowInputs) "读取参数..." else "Run Workflow",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isFetchingRuns = true
                                engine.listWorkflowRuns(pat, targetRepo).onSuccess { runs ->
                                    workflowRuns = runs
                                }
                                isFetchingRuns = false
                                showRunHistoryMenu = true
                            }
                        },
                        enabled = pat.isNotBlank() && targetRepo.isNotBlank() && !isFetchingRuns,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "运行历史")
                    }

                    DropdownMenu(
                        expanded = showRunHistoryMenu,
                        onDismissRequest = { showRunHistoryMenu = false }
                    ) {
                        if (workflowRuns.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无运行记录") },
                                onClick = { showRunHistoryMenu = false }
                            )
                        } else {
                            workflowRuns.forEach { run ->
                                val stateLabel = when {
                                    run.status != "completed" -> run.status
                                    run.conclusion == "success" -> "成功"
                                    run.conclusion.isNotBlank() -> run.conclusion
                                    else -> run.status
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "#${run.runNumber} · ${run.name} ($stateLabel)",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = { showRunHistoryMenu = false }
                                )
                            }
                        }
                    }
                }
            }

            val displayRuns = if (activeRuns.isNotEmpty()) activeRuns else listOfNotNull(latestRun)
            if (displayRuns.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    displayRuns.forEach { run ->
                        androidx.compose.runtime.key(run.id) {
                            val (badgeColor, statusText) = when {
                                run.status == "queued" || run.status == "waiting" -> Color(0xFFD97706) to "排队中"
                                run.status == "in_progress" -> Color(0xFF2563EB) to "构建中"
                                run.conclusion == "success" -> Color(0xFF1B8E3E) to "构建成功"
                                run.conclusion == "failure" -> Color(0xFFDC2626) to "构建失败"
                                else -> Color.Gray to (run.conclusion.ifBlank { run.status })
                            }
                            val isRunning = run.status != "completed"
                            var nowMs by remember(run.id) { mutableLongStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(run.id, isRunning) {
                                while (isRunning) {
                                    nowMs = System.currentTimeMillis()
                                    delay(1000)
                                }
                            }
                            val createdMs = remember(run.createdAt) {
                                runCatching {
                                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                        timeZone = TimeZone.getTimeZone("UTC")
                                    }.parse(run.createdAt)?.time ?: 0L
                                }.getOrDefault(0L)
                            }
                            val elapsedSec = if (createdMs > 0L) ((nowMs - createdMs) / 1000L).coerceAtLeast(0L) else 0L
                            val spinAngle = if (isRunning) {
                                val inf = rememberInfiniteTransition(label = "wfSpin_${run.id}")
                                inf.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "wfSpinAngle_${run.id}"
                                ).value
                            } else {
                                0f
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${run.runNumber} · ${run.name} · $statusText",
                                    color = badgeColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isRunning) {
                                    if (createdMs > 0L) {
                                        Text(
                                            text = "${elapsedSec}s",
                                            color = badgeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                    }
                                    TextButton(
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        onClick = {
                                            scope.launch {
                                                engine.cancelWorkflowRun(pat, targetRepo, run.id)
                                                    .onSuccess {
                                                        Toast.makeText(context, "已取消构建", Toast.LENGTH_SHORT).show()
                                                        delay(1000)
                                                        engine.listWorkflowRuns(pat, targetRepo).onSuccess { workflowRuns = it }
                                                    }
                                                    .onFailure {
                                                        Toast.makeText(context, "取消失败", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        },
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("取消", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            engine.listWorkflowRuns(pat, targetRepo).onSuccess { workflowRuns = it }
                                        }
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "刷新状态",
                                        tint = badgeColor,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .graphicsLayer { rotationZ = spinAngle }
                                    )
                                }
                            }

                            if (run.conclusion == "failure") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isFetchingFailedLog) "正在拉取失败日志..." else "失败日志摘要",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (failedRunLog.isNotBlank()) {
                                    TextButton(
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        onClick = {
                                            val fileName = "workflow_run_${run.runNumber}_error.txt"
                                            val uri = OutputStorageHelper.saveStream(
                                                context = context,
                                                subFolder = OutputStorageHelper.SUB_FILES,
                                                fileName = fileName,
                                                mimeType = "text/plain",
                                                inputStream = ByteArrayInputStream(failedRunLog.toByteArray(Charsets.UTF_8))
                                            )
                                            Toast.makeText(
                                                context,
                                                if (uri != null) "已保存 $fileName" else "保存失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) {
                                        Text("保存 .txt", color = Color(0xFF93C5FD), fontSize = 11.sp)
                                    }
                                }
                            }
                            if (failedRunLog.isNotBlank()) {
                                val preview = failedRunLog.lines().takeLast(45).joinToString("\n")
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = preview,
                                        color = Color(0xFFE5E7EB),
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
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

    if (showTokenManagerDialog) {
        AlertDialog(
            onDismissRequest = { showTokenManagerDialog = false },
            title = { Text("管理已保存 Token", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTokenInput,
                            onValueChange = { newTokenInput = it },
                            placeholder = { Text("输入 ghp_... Token", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val t = newTokenInput.trim()
                                if (t.isNotEmpty()) {
                                    scope.launch {
                                        engine.verifyToken(t).onSuccess { uname ->
                                            savedTokens = engine.saveTokenEntry(uname, t)
                                            pat = t
                                            connectedUser = uname
                                            newTokenInput = ""
                                            refreshGitHubMeta(t)
                                            Toast.makeText(context, "已保存 @$uname", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Token 无效", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("保存")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(savedTokens, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        pat = item.token
                                        connectedUser = item.username
                                        showTokenManagerDialog = false
                                        refreshGitHubMeta(item.token)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("@${item.username}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${item.token.take(10)}...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { savedTokens = engine.deleteTokenEntry(item.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTokenManagerDialog = false }) { Text("关闭") }
            }
        )
    }
    if (showWorkflowPickerDialog) {
        AlertDialog(
            onDismissRequest = { showWorkflowPickerDialog = false },
            title = { Text("选择 Workflow", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(workflows, key = { it.id }) { wf ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showWorkflowPickerDialog = false
                                    openWorkflowInputDialog(wf)
                                }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(wf.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(wf.path, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkflowPickerDialog = false }) { Text("取消") }
            }
        )
    }

    if (showRunWorkflowDialog) {
        val wf = selectedWorkflow
        AlertDialog(
            onDismissRequest = { showRunWorkflowDialog = false },
            title = {
                Text(
                    text = wf?.name ?: "Run Workflow",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "分支: $branch",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (workflowInputs.isEmpty()) {
                        Text("该工作流无需额外参数，可直接运行。", fontSize = 13.sp)
                    } else {
                        workflowInputs.forEach { field ->
                            if (field.options.isNotEmpty()) {
                                var expanded by remember(field.key) { mutableStateOf(false) }
                                val currentVal = workflowInputValues[field.key] ?: field.defaultValue.ifBlank { field.options.first() }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (field.required) "${field.key} *" else field.key,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { expanded = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = currentVal,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            field.options.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt, fontSize = 14.sp) },
                                                    onClick = {
                                                        workflowInputValues[field.key] = opt
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (field.description.isNotBlank()) {
                                        Text(
                                            text = field.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = workflowInputValues[field.key] ?: "",
                                    onValueChange = { workflowInputValues[field.key] = it },
                                    label = {
                                        Text(
                                            text = if (field.required) "${field.key} *" else field.key,
                                            fontSize = 12.sp
                                        )
                                    },
                                    supportingText = if (field.description.isNotBlank()) {
                                        { Text(field.description, fontSize = 11.sp) }
                                    } else null,
                                    singleLine = !field.key.contains("changelog", ignoreCase = true),
                                    minLines = if (field.key.contains("changelog", ignoreCase = true)) 3 else 1,
                                    maxLines = if (field.key.contains("changelog", ignoreCase = true)) 6 else 1,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isDispatchingWorkflow && wf != null,
                    onClick = {
                        val targetWf = wf ?: return@Button
                        scope.launch {
                            isDispatchingWorkflow = true
                            val inputsMap = workflowInputValues.toMap()
                            engine.dispatchWorkflow(pat, targetRepo, targetWf.id, branch, inputsMap)
                                .onSuccess {
                                    showRunWorkflowDialog = false
                                    Toast.makeText(context, "已触发 Run workflow", Toast.LENGTH_SHORT).show()
                                    delay(1500)
                                    engine.listWorkflowRuns(pat, targetRepo).onSuccess { workflowRuns = it }
                                }
                                .onFailure { err ->
                                    Toast.makeText(context, "触发失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                }
                            isDispatchingWorkflow = false
                        }
                    }
                ) {
                    Text(if (isDispatchingWorkflow) "触发中..." else "Run workflow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRunWorkflowDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DiffRowItem(label: String, badgeColor: Color, path: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.16f)) {
            Text(
                text = label,
                color = badgeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = path,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PickerModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            modifier = modifier
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 13.sp, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            modifier = modifier
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 13.sp, maxLines = 1)
        }
    }
}
