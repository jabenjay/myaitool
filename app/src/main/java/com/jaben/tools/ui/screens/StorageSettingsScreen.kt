package com.jaben.tools.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.jaben.tools.data.AppSettings
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jaben.tools.ui.theme.liquidPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AppBrowserEntry(
    val id: String,
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val mimeType: String?,
    val contentUri: Uri?,
    val file: File?,
    val documentFile: DocumentFile?,
    val childCount: Int = 0
)

@Composable
fun StorageSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSelectOutputRoot: (Uri, String) -> Unit,
    onResetOutputRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val context = LocalContext.current
    var showFileBrowser by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val rawSeg = treeUri.lastPathSegment.orEmpty()
            val friendly = rawSeg
                .removePrefix("primary:")
                .replace(":", "/")
                .ifBlank { "自定义文件夹" }
            onSelectOutputRoot(treeUri, friendly)
            Toast.makeText(context, "保存主路径已设为: $friendly", Toast.LENGTH_SHORT).show()
        }
    }

    if (showFileBrowser) {
        AppFolderBrowserView(
            settings = settings,
            onBack = { showFileBrowser = false },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.liquidPress()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = visuals.cardContentColor
                )
            }
            Text(
                text = "保存路径",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = visuals.cardContentColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "当前输出主路径",
                        fontSize = 13.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.65f)
                    )
                    Text(
                        text = settings.outputRootLabel,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "各功能输出文件将自动分类保存到以下子目录：",
                        fontSize = 13.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• ${settings.outputRootLabel}/pictures  (图片、二维码)\n" +
                                "• ${settings.outputRootLabel}/videos    (视频、动图)\n" +
                                "• ${settings.outputRootLabel}/sounds    (音频)\n" +
                                "• ${settings.outputRootLabel}/files     (压缩归档、日志)",
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.72f)
                    )
                }
            }

            Button(
                onClick = { folderPicker.launch(null) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .liquidPress()
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择其他主文件夹", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            if (settings.outputRootUri != null) {
                OutlinedButton(
                    onClick = {
                        onResetOutputRoot()
                        Toast.makeText(context, "已恢复默认路径: Download/Jools", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复默认 (Download/Jools)")
                }
            }

            FilledTonalButton(
                onClick = { showFileBrowser = true },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .liquidPress()
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("浏览当前文件", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AppFolderBrowserView(
    settings: AppSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentRelPath by remember { mutableStateOf("") }
    var safStack by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var entries by remember { mutableStateOf<List<AppBrowserEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var previewEntry by remember { mutableStateOf<AppBrowserEntry?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<AppBrowserEntry?>(null) }

    val navigateUp: () -> Unit = {
        if (settings.outputRootUri != null) {
            if (safStack.size > 1) {
                safStack = safStack.dropLast(1)
                currentRelPath = currentRelPath.substringBeforeLast('/', "")
            } else {
                onBack()
            }
        } else {
            if (currentRelPath.isNotEmpty()) {
                currentRelPath = currentRelPath.substringBeforeLast('/', "")
            } else {
                onBack()
            }
        }
    }

    BackHandler(onBack = navigateUp)

    LaunchedEffect(settings.outputRootUri, currentRelPath, safStack, refreshTrigger) {
        isLoading = true
        entries = withContext(Dispatchers.IO) {
            if (settings.outputRootUri != null) {
                val rootDoc = safStack.lastOrNull() ?: run {
                    val uri = Uri.parse(settings.outputRootUri)
                    val doc = DocumentFile.fromTreeUri(context, uri)
                    if (doc != null) safStack = listOf(doc)
                    doc
                }
                loadSafEntries(rootDoc, currentRelPath)
            } else {
                loadDefaultJoolsEntries(context, currentRelPath)
            }
        }
        isLoading = false
    }

    val headerTitle = if (currentRelPath.isBlank()) {
        settings.outputRootLabel
    } else {
        "${settings.outputRootLabel}/$currentRelPath"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = navigateUp, modifier = Modifier.liquidPress()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = visuals.cardContentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "浏览当前文件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = visuals.cardContentColor
                )
                Text(
                    text = headerTitle,
                    fontSize = 12.sp,
                    color = visuals.cardContentColor.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新",
                    tint = visuals.cardContentColor
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = visuals.cardContentColor.copy(alpha = 0.45f)
                    )
                    Text(
                        text = "当前文件夹为空",
                        fontSize = 14.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.65f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(18.dp) else Modifier)
                            .clickable {
                                if (entry.isDirectory) {
                                    if (entry.documentFile != null) {
                                        safStack = safStack + entry.documentFile
                                    }
                                    currentRelPath = if (currentRelPath.isBlank()) entry.name else "$currentRelPath/${entry.name}"
                                } else {
                                    scope.launch {
                                        previewText = withContext(Dispatchers.IO) {
                                            readTextPreviewIfSupported(context, entry)
                                        }
                                        previewEntry = entry
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = iconForEntry(entry),
                                contentDescription = null,
                                tint = if (entry.isDirectory) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatEntrySubtitle(context, entry),
                                    fontSize = 12.sp,
                                    color = visuals.cardContentColor.copy(alpha = 0.65f)
                                )
                            }
                            if (entry.isDirectory) {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = visuals.cardContentColor.copy(alpha = 0.5f)
                                )
                            } else {
                                IconButton(onClick = { deleteTarget = entry }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
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

    previewEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = {
                previewEntry = null
                previewText = null
            },
            title = {
                Text(
                    text = entry.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val text = previewText
                if (text != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("类型：${entry.mimeType ?: entry.name.substringAfterLast('.', "未知")}", fontSize = 13.sp)
                        Text("大小：${Formatter.formatShortFileSize(context, entry.sizeBytes)}", fontSize = 13.sp)
                        if (entry.modifiedMs > 0L) {
                            Text("修改时间：${formatDate(entry.modifiedMs)}", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (previewText != null) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(entry.name, previewText))
                            Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制")
                        }
                    }
                    if (entry.contentUri != null) {
                        TextButton(onClick = {
                            shareOrOpenUri(context, entry.contentUri, entry.mimeType ?: "*/*", share = true)
                        }) {
                            Text("分享")
                        }
                        TextButton(onClick = {
                            shareOrOpenUri(context, entry.contentUri, entry.mimeType ?: "*/*", share = false)
                        }) {
                            Text("打开")
                        }
                    }
                    TextButton(onClick = {
                        previewEntry = null
                        previewText = null
                    }) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除？") },
            text = { Text("确定删除“${target.name}”吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val item = target
                    deleteTarget = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { deleteEntry(context, item) }
                        if (ok) {
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        } else {
                            Toast.makeText(context, "删除失败（系统可能限制删除该文件）", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun loadSafEntries(dir: DocumentFile?, relPrefix: String): List<AppBrowserEntry> {
    if (dir == null || !dir.isDirectory) return emptyList()
    return dir.listFiles().mapNotNull { doc ->
        val name = doc.name ?: return@mapNotNull null
        if (name.startsWith(".")) return@mapNotNull null
        val isDir = doc.isDirectory
        val rel = if (relPrefix.isBlank()) name else "$relPrefix/$name"
        AppBrowserEntry(
            id = doc.uri.toString(),
            name = name,
            relativePath = rel,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else doc.length(),
            modifiedMs = doc.lastModified(),
            mimeType = doc.type,
            contentUri = doc.uri,
            file = null,
            documentFile = doc,
            childCount = if (isDir) doc.listFiles().size else 0
        )
    }.sortedWith(
        compareByDescending<AppBrowserEntry> { it.isDirectory }
            .thenBy { if (it.isDirectory) it.name.lowercase(Locale.ROOT) else "" }
            .thenByDescending { if (it.isDirectory) 0L else it.modifiedMs }
    )
}

private fun loadDefaultJoolsEntries(context: Context, currentRelPath: String): List<AppBrowserEntry> {
    val resultMap = LinkedHashMap<String, AppBrowserEntry>()

    // 1. Direct File inspection (works on Android <= 9 and for app-owned / accessible files)
    @Suppress("DEPRECATION")
    val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val baseDir = File(downloadsRoot, "Jools")
    val targetDir = if (currentRelPath.isBlank()) baseDir else File(baseDir, currentRelPath)
    if (targetDir.exists() && targetDir.isDirectory) {
        targetDir.listFiles()?.forEach { f ->
            if (f.name.startsWith(".")) return@forEach
            val rel = if (currentRelPath.isBlank()) f.name else "$currentRelPath/${f.name}"
            resultMap[rel] = AppBrowserEntry(
                id = "file:${f.absolutePath}",
                name = f.name,
                relativePath = rel,
                isDirectory = f.isDirectory,
                sizeBytes = if (f.isDirectory) 0L else f.length(),
                modifiedMs = f.lastModified(),
                mimeType = null,
                contentUri = null,
                file = f,
                documentFile = null,
                childCount = if (f.isDirectory) (f.listFiles()?.size ?: 0) else 0
            )
        }
    }

    // 2. MediaStore query (Android 10+ scoped storage for Download/Jools/...)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val collections = listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val rootPrefix = "${Environment.DIRECTORY_DOWNLOADS}/Jools/"
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$rootPrefix%")

        for (collectionUri in collections) {
            runCatching {
                context.contentResolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val modIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val name = cursor.getString(nameIdx) ?: continue
                        val rawRel = cursor.getString(pathIdx).orEmpty()
                        val subPath = rawRel.removePrefix(rootPrefix).trim('/')
                        val itemUri = ContentUris.withAppendedId(collectionUri, id)
                        val size = cursor.getLong(sizeIdx)
                        val modMs = cursor.getLong(modIdx) * 1000L
                        val mime = cursor.getString(mimeIdx)

                        if (currentRelPath.isBlank()) {
                            if (subPath.isBlank()) {
                                resultMap[name] = AppBrowserEntry(
                                    id = itemUri.toString(),
                                    name = name,
                                    relativePath = name,
                                    isDirectory = false,
                                    sizeBytes = size,
                                    modifiedMs = modMs,
                                    mimeType = mime,
                                    contentUri = itemUri,
                                    file = resultMap[name]?.file,
                                    documentFile = null
                                )
                            } else {
                                val topFolder = subPath.substringBefore('/')
                                val existing = resultMap[topFolder]
                                if (existing == null || existing.isDirectory) {
                                    resultMap[topFolder] = AppBrowserEntry(
                                        id = "dir:$topFolder",
                                        name = topFolder,
                                        relativePath = topFolder,
                                        isDirectory = true,
                                        sizeBytes = 0L,
                                        modifiedMs = maxOf(existing?.modifiedMs ?: 0L, modMs),
                                        mimeType = null,
                                        contentUri = null,
                                        file = existing?.file,
                                        documentFile = null,
                                        childCount = (existing?.childCount ?: 0) + 1
                                    )
                                }
                            }
                        } else if (subPath == currentRelPath) {
                            val rel = "$currentRelPath/$name"
                            resultMap[rel] = AppBrowserEntry(
                                id = itemUri.toString(),
                                name = name,
                                relativePath = rel,
                                isDirectory = false,
                                sizeBytes = size,
                                modifiedMs = modMs,
                                mimeType = mime,
                                contentUri = itemUri,
                                file = resultMap[rel]?.file,
                                documentFile = null
                            )
                        } else if (subPath.startsWith("$currentRelPath/")) {
                            val rest = subPath.removePrefix("$currentRelPath/")
                            val nextFolder = rest.substringBefore('/')
                            val rel = "$currentRelPath/$nextFolder"
                            val existing = resultMap[rel]
                            if (existing == null || existing.isDirectory) {
                                resultMap[rel] = AppBrowserEntry(
                                    id = "dir:$rel",
                                    name = nextFolder,
                                    relativePath = rel,
                                    isDirectory = true,
                                    sizeBytes = 0L,
                                    modifiedMs = maxOf(existing?.modifiedMs ?: 0L, modMs),
                                    mimeType = null,
                                    contentUri = null,
                                    file = existing?.file,
                                    documentFile = null,
                                    childCount = (existing?.childCount ?: 0) + 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    return resultMap.values.sortedWith(
        compareByDescending<AppBrowserEntry> { it.isDirectory }
            .thenBy { if (it.isDirectory) it.name.lowercase(Locale.ROOT) else "" }
            .thenByDescending { if (it.isDirectory) 0L else it.modifiedMs }
    )
}

private fun readTextPreviewIfSupported(context: Context, entry: AppBrowserEntry): String? {
    val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val textExts = setOf("txt", "log", "json", "md", "xml", "csv", "yml", "yaml", "sh", "py", "kt", "swift", "html", "css", "js", "conf", "ini")
    if (ext !in textExts) return null
    val maxBytes = 200 * 1024
    return runCatching {
        val stream = when {
            entry.contentUri != null -> context.contentResolver.openInputStream(entry.contentUri)
            entry.file != null && entry.file.canRead() -> entry.file.inputStream()
            else -> null
        } ?: return null
        stream.use { input ->
            val buf = ByteArray(maxBytes + 1)
            var total = 0
            while (total < buf.size) {
                val read = input.read(buf, total, buf.size - total)
                if (read <= 0) break
                total += read
            }
            val truncated = total > maxBytes
            val len = if (truncated) maxBytes else total
            val text = String(buf, 0, len, Charsets.UTF_8)
            if (text.isEmpty()) "(空文件)"
            else if (truncated) "$text\n\n...(文件较大，仅预览前 200 KB)"
            else text
        }
    }.getOrNull()
}

private fun deleteEntry(context: Context, entry: AppBrowserEntry): Boolean {
    return runCatching {
        when {
            entry.documentFile != null -> entry.documentFile.delete()
            entry.contentUri != null -> context.contentResolver.delete(entry.contentUri, null, null) > 0
            entry.file != null -> entry.file.deleteRecursively()
            else -> false
        }
    }.getOrDefault(false)
}

private fun shareOrOpenUri(context: Context, uri: Uri, mimeType: String, share: Boolean) {
    runCatching {
        val intent = if (share) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "分享文件") }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "无法打开该文件: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun iconForEntry(entry: AppBrowserEntry): ImageVector {
    if (entry.isDirectory) return Icons.Rounded.Folder
    return when (entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "txt", "log", "md", "json", "xml", "csv", "yml", "yaml" -> Icons.Rounded.Description
        "png", "jpg", "jpeg", "gif", "webp", "heic" -> Icons.Rounded.Image
        "mp4", "mov", "m4v", "mkv", "avi" -> Icons.Rounded.VideoFile
        "mp3", "m4a", "wav", "aac", "flac" -> Icons.Rounded.AudioFile
        "zip", "rar", "7z", "tar", "gz", "apk", "ipa" -> Icons.Rounded.Archive
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

private fun formatEntrySubtitle(context: Context, entry: AppBrowserEntry): String {
    val dateStr = if (entry.modifiedMs > 0L) formatDate(entry.modifiedMs) else ""
    return if (entry.isDirectory) {
        if (dateStr.isNotEmpty()) "${entry.childCount} 项 · $dateStr" else "${entry.childCount} 项"
    } else {
        val sizeStr = Formatter.formatShortFileSize(context, entry.sizeBytes)
        if (dateStr.isNotEmpty()) "$sizeStr · $dateStr" else sizeStr
    }
}

private fun formatDate(ms: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
}
