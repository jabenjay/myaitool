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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.tools.git.PushFileEntry
import com.jaben.tools.tools.git.PushGitEngine
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import kotlinx.coroutines.launch

@Composable
fun PushGitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val visuals = LocalAppThemeVisuals.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { PushGitEngine(context) }

    var pat by remember { mutableStateOf(engine.loadSavedToken()) }
    var showToken by remember { mutableStateOf(false) }
    var connectedUser by remember { mutableStateOf<String?>(null) }

    var targetRepo by remember { mutableStateOf(engine.loadSavedRepo()) }
    var repoUrl by remember { mutableStateOf("https://github.com/${engine.loadSavedRepo()}") }
    var repos by remember { mutableStateOf<List<String>>(emptyList()) }
    var repoMenuExpanded by remember { mutableStateOf(false) }

    var branch by remember { mutableStateOf(engine.loadSavedBranch()) }
    var branches by remember { mutableStateOf(listOf("main")) }
    var commitMessage by remember { mutableStateOf(engine.loadSavedMessage()) }
    var targetSubpath by remember { mutableStateOf("") }

    var selectedFiles by remember { mutableStateOf<List<PushFileEntry>>(emptyList()) }
    var isFetchingMeta by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }
    var pushDone by remember { mutableIntStateOf(0) }
    var pushTotal by remember { mutableIntStateOf(0) }
    var pushStatus by remember { mutableStateOf("") }

    fun refreshGitHubMeta() {
        if (pat.isBlank()) return
        scope.launch {
            isFetchingMeta = true
            engine.verifyToken(pat).onSuccess { user ->
                connectedUser = user
            }.onFailure {
                connectedUser = null
            }
            engine.listRepos(pat).onSuccess { list ->
                if (list.isNotEmpty()) repos = list
            }
            engine.listBranches(pat, targetRepo).onSuccess { bList ->
                if (bList.isNotEmpty()) branches = bList
            }
            isFetchingMeta = false
        }
    }

    LaunchedEffect(Unit) {
        if (pat.isNotBlank()) refreshGitHubMeta()
    }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                engine.readZipUri(uri).onSuccess { list ->
                    selectedFiles = list
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
                    selectedFiles = list
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
                engine.readFileUris(uris, targetSubpath).onSuccess { list ->
                    selectedFiles = list
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
            IconButton(
                onClick = { refreshGitHubMeta() },
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
            // Card 1: Target Repository & PAT
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
                    Text("目标仓库与认证", fontSize = 14.sp, fontWeight = FontWeight.Bold)

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
                                    text = targetRepo.ifBlank { "选择或输入仓库" },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(if (repos.isEmpty()) "手动输入" else "▾", fontSize = 12.sp)
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
                                        repoUrl = "https://github.com/$r"
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

                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = {
                            repoUrl = it
                            targetRepo = engine.parseOwnerRepo(it)
                        },
                        label = { Text("GitHub Repository URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pat,
                        onValueChange = { pat = it },
                        label = { Text("Personal Access Token (ghp_...)") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "显示令牌"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

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
                        onValueChange = { branch = it },
                        label = { Text("Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(branches) { b ->
                            FilterChip(
                                selected = branch == b,
                                onClick = { branch = b },
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

            // Card 3: Native Android File Picker (ZIP / Folder / Files)
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
                    Text("选择上传内容（调用系统文件管理器）", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = targetSubpath,
                        onValueChange = { targetSubpath = it },
                        label = { Text("单文件目标路径前缀（可选，如 app/src/...）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ZIP", fontSize = 13.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { folderPicker.launch(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("文件夹", fontSize = 13.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { filesPicker.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("文件", fontSize = 13.sp, maxLines = 1)
                        }
                    }

                    if (selectedFiles.isNotEmpty()) {
                        val totalKb = selectedFiles.sumOf { it.sizeBytes } / 1024.0
                        Text(
                            text = "已就绪 ${selectedFiles.size} 个文件 (${String.format("%.1f KB", totalKb)})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
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

        // Bottom Push Button
        Button(
            onClick = {
                engine.saveConfig(pat, targetRepo, branch, commitMessage)
                scope.launch {
                    isPushing = true
                    pushDone = 0
                    pushTotal = selectedFiles.size
                    engine.pushFiles(
                        pat = pat,
                        ownerRepoInput = targetRepo,
                        branchInput = branch,
                        commitMessageInput = commitMessage,
                        files = selectedFiles
                    ) { done, total, cur ->
                        pushDone = done
                        pushTotal = total
                        pushStatus = "推送中 ($done/$total): $cur"
                    }.onSuccess { res ->
                        pushStatus = "推送成功: ${res.fileCount} 个文件 (${res.commitSha.take(7)})"
                        Toast.makeText(context, "推送成功", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        pushStatus = "推送失败: ${err.message}"
                        Toast.makeText(context, "推送失败", Toast.LENGTH_SHORT).show()
                    }
                    isPushing = false
                }
            },
            enabled = !isPushing && pat.isNotBlank() && selectedFiles.isNotEmpty(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(50.dp)
        ) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPushing) "正在推送..." else "Push 到 GitHub",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
