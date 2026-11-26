package com.jaben.tools.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.data.OutputStorageHelper
import com.jaben.tools.tools.compress.CompressFileType
import com.jaben.tools.tools.compress.CompressOutputResult
import com.jaben.tools.tools.compress.CompressSourceMeta
import com.jaben.tools.tools.compress.FileCompressorEngine
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jaben.tools.ui.theme.liquidPress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileCompressorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            FileCompressorEngine.clearCache(context)
        }
    }

    var sourceMeta by remember { mutableStateOf<CompressSourceMeta?>(null) }
    var outputResult by remember { mutableStateOf<CompressOutputResult?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var isCompressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var compressJob by remember { mutableStateOf<Job?>(null) }

    // Slider: 5 (left = largest/lightest) .. 90 (right = smallest/strongest)
    var compressionPercent by rememberSaveable { mutableIntStateOf(50) }
    val targetSizePercent = (100 - compressionPercent).coerceIn(10, 95)
    var videoMaxHeight by rememberSaveable { mutableIntStateOf(0) }
    var videoTargetFps by rememberSaveable { mutableIntStateOf(0) }
    var useHevc by rememberSaveable { mutableStateOf(true) }
    var imageScalePercent by rememberSaveable { mutableIntStateOf(100) }
    var imageOutputExt by rememberSaveable { mutableStateOf("jpg") }

    val thumbBitmap = remember(sourceMeta?.uri, sourceMeta?.fileType) {
        if (sourceMeta?.fileType == CompressFileType.IMAGE) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(sourceMeta!!.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        } else null
    }

    val estimate = remember(
        sourceMeta,
        targetSizePercent,
        videoMaxHeight,
        videoTargetFps,
        useHevc,
        imageScalePercent
    ) {
        sourceMeta?.let {
            FileCompressorEngine.estimateCompressedSize(
                meta = it,
                targetSizePercent = targetSizePercent,
                videoMaxHeight = videoMaxHeight,
                videoTargetFps = videoTargetFps,
                useHevc = useHevc,
                imageScalePercent = imageScalePercent
            )
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            FileCompressorEngine.clearCache(context)
            val meta = FileCompressorEngine.inspectFile(context, uri)
            sourceMeta = meta
            if (meta.fileType == CompressFileType.VIDEO) {
                val (autoH, autoFps) = FileCompressorEngine.suggestVideoParams(meta, targetSizePercent)
                videoMaxHeight = autoH
                videoTargetFps = autoFps
            }
            outputResult = null
            savedUri = null
            errorMsg = null
        }
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
                text = "文件压缩",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = visuals.cardContentColor,
                modifier = Modifier.weight(1f)
            )

        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                border = if (!visuals.isLiquidGlass) BorderStroke(1.dp, visuals.cardContentColor.copy(alpha = 0.08f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
                    .clickable(enabled = !isCompressing) {
                        filePicker.launch(arrayOf("*/*"))
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sourceMeta?.displayName ?: "点击选择需要压缩的文件",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val subtitle = sourceMeta?.let { m ->
                            buildString {
                                append(m.fileType.label)
                                if (m.width > 0 && m.height > 0) append(" · ${m.width}×${m.height}")
                                if (m.fileType == CompressFileType.VIDEO) append(" · ${m.fps}fps")
                                if (m.durationMs > 0) append(" · ${m.durationMs / 1000}s")
                            }
                        } ?: "支持视频、图片、音频或任意文件 (ZIP)"
                        Text(
                            text = subtitle,
                            fontSize = 12.5.sp,
                            color = visuals.cardContentColor.copy(alpha = 0.68f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            sourceMeta?.let { meta ->
                if (meta.fileType == CompressFileType.VIDEO || meta.fileType == CompressFileType.IMAGE) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (meta.fileType == CompressFileType.VIDEO) {
                                Text("分辨率", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0 to "原画", 1080 to "1080P", 720 to "720P", 480 to "480P", 360 to "360P").forEach { (h, label) ->
                                        FilterChip(
                                            selected = videoMaxHeight == h,
                                            enabled = !isCompressing,
                                            onClick = { videoMaxHeight = h },
                                            label = { Text(label, fontSize = 12.5.sp) }
                                        )
                                    }
                                }

                                Text("帧率限制", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0 to "原帧率", 30 to "30fps", 24 to "24fps", 15 to "15fps").forEach { (f, label) ->
                                        FilterChip(
                                            selected = videoTargetFps == f,
                                            enabled = !isCompressing,
                                            onClick = { videoTargetFps = f },
                                            label = { Text(label, fontSize = 12.5.sp) }
                                        )
                                    }
                                }

                                Text("视频编码", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = useHevc,
                                        enabled = !isCompressing,
                                        onClick = { useHevc = true },
                                        label = { Text("HEVC (体积更小)") }
                                    )
                                    FilterChip(
                                        selected = !useHevc,
                                        enabled = !isCompressing,
                                        onClick = { useHevc = false },
                                        label = { Text("H.264 (兼容最好)") }
                                    )
                                }
                            } else if (meta.fileType == CompressFileType.IMAGE) {
                                Text("尺寸缩放", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(100, 75, 50, 25).forEach { pct ->
                                        FilterChip(
                                            selected = imageScalePercent == pct,
                                            enabled = !isCompressing,
                                            onClick = { imageScalePercent = pct },
                                            label = { Text(if (pct == 100) "原尺寸" else "${pct}%") }
                                        )
                                    }
                                }

                                Text("输出格式", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = imageOutputExt == "jpg",
                                        enabled = !isCompressing,
                                        onClick = { imageOutputExt = "jpg" },
                                        label = { Text("JPG") }
                                    )
                                    FilterChip(
                                        selected = imageOutputExt == "webp",
                                        enabled = !isCompressing,
                                        onClick = { imageOutputExt = "webp" },
                                        label = { Text("WebP (更小)") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reversed slider: Left (smaller) <-> Right (closer to original)
            val warnColor = Color(0xFFF59E0B)
            val isBelowFloor = estimate?.isBelowFloor == true

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "压缩程度",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${compressionPercent}%",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBelowFloor) warnColor else MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.width(74.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "原文件",
                                fontSize = 11.sp,
                                color = visuals.cardContentColor.copy(alpha = 0.6f)
                            )
                            Text(
                                text = sourceMeta?.let { formatBytes(it.sizeBytes) } ?: "--",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        Slider(
                            value = compressionPercent.toFloat(),
                            onValueChange = { raw ->
                                val cmp = raw.roundToInt().coerceIn(5, 90)
                                compressionPercent = cmp
                                val tPct = (100 - cmp).coerceIn(10, 95)
                                sourceMeta?.let { meta ->
                                    if (meta.fileType == CompressFileType.VIDEO) {
                                        val (autoH, autoFps) = FileCompressorEngine.suggestVideoParams(meta, tPct)
                                        videoMaxHeight = autoH
                                        videoTargetFps = autoFps
                                    }
                                }
                            },
                            valueRange = 5f..90f,
                            enabled = sourceMeta != null && !isCompressing,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        )

                        Column(
                            modifier = Modifier.width(74.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "预估大小",
                                fontSize = 11.sp,
                                color = visuals.cardContentColor.copy(alpha = 0.6f),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = if (estimate != null) formatBytes(estimate.estimatedBytes) else "--",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBelowFloor) warnColor else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    if (isBelowFloor) {
                        Text(
                            text = "当前分辨率/帧率较高，无法压至 ${formatBytes(estimate!!.requestedBytes)}，将以所选分辨率和帧率为准输出",
                            fontSize = 11.5.sp,
                            color = warnColor,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            if (isCompressing) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = visuals.cardContainerColor,
                    contentColor = visuals.cardContentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("正在压缩...", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${(progress * 100).roundToInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        OutlinedButton(
                            onClick = {
                                compressJob?.cancel()
                                isCompressing = false
                                progress = 0f
                                FileCompressorEngine.clearCache(context)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("取消")
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        val meta = sourceMeta ?: return@Button
                        errorMsg = null
                        outputResult = null
                        savedUri = null
                        isCompressing = true
                        progress = 0f
                        compressJob = scope.launch {
                            try {
                                val res = FileCompressorEngine.compress(
                                    context = context,
                                    meta = meta,
                                    targetSizePercent = targetSizePercent,
                                    videoMaxHeight = videoMaxHeight,
                                    videoTargetFps = videoTargetFps,
                                    useHevc = useHevc,
                                    imageScalePercent = imageScalePercent,
                                    imageOutputExt = imageOutputExt,
                                    onProgress = { progress = it }
                                )
                                outputResult = res
                            } catch (_: CancellationException) {
                                FileCompressorEngine.clearCache(context)
                                errorMsg = "已取消压缩"
                            } catch (t: Throwable) {
                                FileCompressorEngine.clearCache(context)
                                errorMsg = t.message ?: "压缩失败，请尝试降低参数或更换编码"
                            } finally {
                                isCompressing = false
                            }
                        }
                    },
                    enabled = sourceMeta != null,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidPress()
                ) {
                    Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始压缩", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (!errorMsg.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg!!,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            AnimatedVisibility(visible = outputResult != null) {
                outputResult?.let { res ->
                    val saveDirLabel = OutputStorageHelper.getDisplayPath(context, res.subFolder)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = res.outputName,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val srcBytes = sourceMeta?.sizeBytes ?: 0L
                                    val ratioText = if (srcBytes > 0) {
                                        val pct = ((res.outputSizeBytes - srcBytes) * 100.0 / srcBytes).roundToInt()
                                        val sign = if (pct > 0) "+" else ""
                                        "${formatBytes(srcBytes)} → ${formatBytes(res.outputSizeBytes)} (${sign}${pct}%)"
                                    } else formatBytes(res.outputSizeBytes)
                                    Text(
                                        text = ratioText,
                                        fontSize = 12.5.sp,
                                        color = visuals.cardContentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val uri = OutputStorageHelper.saveFile(
                                            context = context,
                                            subFolder = res.subFolder,
                                            fileName = res.outputName,
                                            mimeType = res.mimeType,
                                            sourceFile = res.tempFile
                                        )
                                        if (uri != null) {
                                            savedUri = uri
                                            FileCompressorEngine.clearCache(context)
                                            Toast.makeText(context, "已保存到 $saveDirLabel", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "保存失败，请检查目录权限", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = savedUri == null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (savedUri != null) "已保存" else "保存")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val shareUri = savedUri ?: OutputStorageHelper.saveFile(
                                            context = context,
                                            subFolder = res.subFolder,
                                            fileName = res.outputName,
                                            mimeType = res.mimeType,
                                            sourceFile = res.tempFile
                                        )?.also {
                                            savedUri = it
                                            FileCompressorEngine.clearCache(context)
                                        }
                                        if (shareUri != null) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = res.mimeType
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享压缩文件"))
                                        } else {
                                            Toast.makeText(context, "无法准备分享文件", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("分享")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
