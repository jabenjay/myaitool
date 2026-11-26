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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.data.OutputStorageHelper
import com.jaben.tools.tools.format.ConversionResult
import com.jaben.tools.tools.format.FormatFactoryEngine
import com.jaben.tools.tools.format.MediaCategory
import com.jaben.tools.tools.format.SourceMediaInfo
import com.jaben.tools.tools.format.TargetFormat
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jaben.tools.ui.theme.liquidPress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun FormatFactoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { FormatFactoryEngine.clearCache(context) }
    }

    var selectedCategoryIdx by rememberSaveable { mutableIntStateOf(0) }
    val category = MediaCategory.entries[selectedCategoryIdx]

    val formatsForCategory = remember(category) {
        TargetFormat.entries.filter { it.category == category }
    }
    var selectedFormatId by rememberSaveable(category) {
        mutableStateOf(formatsForCategory.first().id)
    }
    val selectedFormat = formatsForCategory.firstOrNull { it.id == selectedFormatId }
        ?: formatsForCategory.first()

    var formatDropdownExpanded by remember { mutableStateOf(false) }

    var sourceInfo by remember { mutableStateOf<SourceMediaInfo?>(null) }
    var resultInfo by remember { mutableStateOf<ConversionResult?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var isConverting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var convertJob by remember { mutableStateOf<Job?>(null) }

    // Image options: pixel dimensions + locked aspect ratio by default
    var imageQuality by rememberSaveable { mutableIntStateOf(90) }
    var lockAspectRatio by rememberSaveable { mutableStateOf(true) }
    var widthInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }

    // Video & Audio options
    var videoMaxHeight by rememberSaveable { mutableIntStateOf(0) }
    var audioBitrateKbps by rememberSaveable { mutableIntStateOf(192) }

    val thumbBitmap = remember(sourceInfo?.uri, category) {
        if (category == MediaCategory.IMAGE && sourceInfo != null) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(sourceInfo!!.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        } else null
    }

    val estimatedOutputBytes = remember(
        sourceInfo,
        category,
        selectedFormat,
        widthInput,
        heightInput,
        imageQuality
    ) {
        val src = sourceInfo ?: return@remember 0L
        if (category == MediaCategory.IMAGE) {
            val w = widthInput.toIntOrNull()?.takeIf { it > 0 } ?: src.width
            val h = heightInput.toIntOrNull()?.takeIf { it > 0 } ?: src.height
            if (w > 0 && h > 0) {
                FormatFactoryEngine.estimateImageOutputBytes(w, h, selectedFormat, imageQuality)
            } else 0L
        } else 0L
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            FormatFactoryEngine.clearCache(context)
            val info = FormatFactoryEngine.inspectSource(context, uri, category)
            sourceInfo = info
            if (category == MediaCategory.IMAGE && info.width > 0 && info.height > 0) {
                widthInput = info.width.toString()
                heightInput = info.height.toString()
                lockAspectRatio = true
            } else {
                widthInput = ""
                heightInput = ""
            }
            resultInfo = null
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
                text = "格式工厂",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = visuals.cardContentColor
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = visuals.cardContainerColor,
            contentColor = visuals.cardContentColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(20.dp) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaCategory.entries.forEachIndexed { idx, cat ->
                    val selected = idx == selectedCategoryIdx
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else visuals.cardContainerColor.copy(alpha = 0f),
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else visuals.cardContentColor,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(15.dp))
                            .clickable(enabled = !isConverting) {
                                if (selectedCategoryIdx != idx) {
                                    selectedCategoryIdx = idx
                                    sourceInfo = null
                                    widthInput = ""
                                    heightInput = ""
                                    resultInfo = null
                                    savedUri = null
                                    errorMsg = null
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (cat) {
                                    MediaCategory.IMAGE -> Icons.Rounded.Image
                                    MediaCategory.VIDEO -> Icons.Rounded.VideoFile
                                    MediaCategory.AUDIO -> Icons.Rounded.AudioFile
                                },
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. File Picker
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                border = if (!visuals.isLiquidGlass) BorderStroke(1.dp, visuals.cardContentColor.copy(alpha = 0.08f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
                    .clickable(enabled = !isConverting) {
                        filePicker.launch(arrayOf(category.pickerMime))
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
                            text = sourceInfo?.displayName ?: "点击选择${category.label}文件",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = visuals.cardContentColor
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (sourceInfo != null) {
                                "${sourceInfo!!.detailText} · ${formatSize(sourceInfo!!.sizeBytes)}"
                            } else {
                                when (category) {
                                    MediaCategory.IMAGE -> "支持 JPG / PNG / WebP / HEIC / AVIF / BMP / GIF 等"
                                    MediaCategory.VIDEO -> "支持 MP4 / MKV / MOV / WebM / 3GP 等"
                                    MediaCategory.AUDIO -> "支持 MP3 / M4A / AAC / WAV / FLAC / OGG / Opus 等"
                                }
                            },
                            fontSize = 12.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = visuals.cardContentColor.copy(alpha = 0.68f)
                        )
                    }
                }
            }

            // 2. Compact Target Format Dropdown Selector
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(enabled = !isConverting) { formatDropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "目标格式",
                                fontSize = 12.sp,
                                color = visuals.cardContentColor.copy(alpha = 0.65f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedFormat.label}  ·  ${selectedFormat.desc}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = visuals.cardContentColor
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "选择目标格式",
                            tint = visuals.cardContentColor
                        )
                    }

                    DropdownMenu(
                        expanded = formatDropdownExpanded,
                        onDismissRequest = { formatDropdownExpanded = false }
                    ) {
                        formatsForCategory.forEach { fmt ->
                            val isSel = fmt.id == selectedFormat.id
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = fmt.label,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = fmt.desc,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingIcon = if (isSel) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    selectedFormatId = fmt.id
                                    formatDropdownExpanded = false
                                    resultInfo = null
                                    savedUri = null
                                    errorMsg = null
                                }
                            )
                        }
                    }
                }
            }

            // 3. Parameters & Pixel Dimensions
            val showQuality = selectedFormat in setOf(TargetFormat.IMG_JPG, TargetFormat.IMG_WEBP_LOSSY)
            val showImgDimensions = category == MediaCategory.IMAGE
            val showVideoRes = selectedFormat in setOf(TargetFormat.VID_MP4_H264, TargetFormat.VID_MP4_HEVC, TargetFormat.VID_MOV)
            val showAudioBitrate = selectedFormat in setOf(
                TargetFormat.AUD_M4A, TargetFormat.AUD_AAC, TargetFormat.AUD_OGG, TargetFormat.VID_TO_M4A
            )

            if (showQuality || showImgDimensions || showVideoRes || showAudioBitrate) {
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
                        if (showImgDimensions) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("像素尺寸", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (lockAspectRatio) "已锁定原图比例" else "已解锁自定义比例",
                                    fontSize = 12.sp,
                                    color = if (lockAspectRatio) MaterialTheme.colorScheme.primary else visuals.cardContentColor.copy(alpha = 0.6f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = widthInput,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }
                                        widthInput = digits
                                        val newW = digits.toIntOrNull()
                                        val src = sourceInfo
                                        if (lockAspectRatio && newW != null && newW > 0 && src != null && src.width > 0 && src.height > 0) {
                                            val calcH = max(1, (newW.toLong() * src.height / src.width.toDouble()).roundToInt())
                                            heightInput = calcH.toString()
                                        }
                                    },
                                    label = { Text("宽 (px)", fontSize = 12.sp) },
                                    singleLine = true,
                                    enabled = !isConverting && sourceInfo != null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        lockAspectRatio = !lockAspectRatio
                                        if (lockAspectRatio) {
                                            val src = sourceInfo
                                            val curW = widthInput.toIntOrNull()
                                            if (src != null && src.width > 0 && src.height > 0 && curW != null && curW > 0) {
                                                val calcH = max(1, (curW.toLong() * src.height / src.width.toDouble()).roundToInt())
                                                heightInput = calcH.toString()
                                            }
                                        }
                                    },
                                    enabled = !isConverting && sourceInfo != null,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (lockAspectRatio) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = if (lockAspectRatio) "解锁长宽比" else "锁定长宽比",
                                        tint = if (lockAspectRatio) MaterialTheme.colorScheme.primary else visuals.cardContentColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = heightInput,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }
                                        heightInput = digits
                                        val newH = digits.toIntOrNull()
                                        val src = sourceInfo
                                        if (lockAspectRatio && newH != null && newH > 0 && src != null && src.width > 0 && src.height > 0) {
                                            val calcW = max(1, (newH.toLong() * src.width / src.height.toDouble()).roundToInt())
                                            widthInput = calcW.toString()
                                        }
                                    },
                                    label = { Text("高 (px)", fontSize = 12.sp) },
                                    singleLine = true,
                                    enabled = !isConverting && sourceInfo != null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Quick scale chips that update width & height pixels
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(100, 75, 50, 25).forEach { pct ->
                                    val src = sourceInfo
                                    val expectedW = if (src != null && src.width > 0) max(1, (src.width * pct / 100f).roundToInt()) else -1
                                    val expectedH = if (src != null && src.height > 0) max(1, (src.height * pct / 100f).roundToInt()) else -1
                                    val isChipSelected = expectedW > 0 &&
                                        widthInput.toIntOrNull() == expectedW &&
                                        heightInput.toIntOrNull() == expectedH
                                    FilterChip(
                                        selected = isChipSelected,
                                        enabled = !isConverting && src != null && src.width > 0,
                                        onClick = {
                                            if (expectedW > 0 && expectedH > 0) {
                                                widthInput = expectedW.toString()
                                                heightInput = expectedH.toString()
                                            }
                                        },
                                        label = { Text(if (pct == 100) "原尺寸" else "${pct}%") }
                                    )
                                }
                            }
                        }

                        if (showQuality) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("输出质量", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${imageQuality}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = imageQuality.toFloat(),
                                onValueChange = { imageQuality = it.roundToInt() },
                                valueRange = 10f..100f,
                                enabled = !isConverting
                            )
                        }

                        if (category == MediaCategory.IMAGE && estimatedOutputBytes > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "预计输出大小",
                                    fontSize = 13.sp,
                                    color = visuals.cardContentColor.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "约 ${formatSize(estimatedOutputBytes)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (showVideoRes) {
                            Text("输出分辨率", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "原画", 1080 to "1080P", 720 to "720P", 480 to "480P").forEach { (h, label) ->
                                    FilterChip(
                                        selected = videoMaxHeight == h,
                                        enabled = !isConverting,
                                        onClick = { videoMaxHeight = h },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        if (showAudioBitrate) {
                            Text("音频码率", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(128, 192, 256, 320).forEach { kbps ->
                                    FilterChip(
                                        selected = audioBitrateKbps == kbps,
                                        enabled = !isConverting,
                                        onClick = { audioBitrateKbps = kbps },
                                        label = { Text("${kbps}k") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isConverting) {
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
                            Text("正在转换...", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                                convertJob?.cancel()
                                isConverting = false
                                progress = 0f
                                FormatFactoryEngine.clearCache(context)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("取消转换")
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        val src = sourceInfo ?: return@Button
                        errorMsg = null
                        resultInfo = null
                        savedUri = null
                        isConverting = true
                        progress = 0f
                        convertJob = scope.launch {
                            try {
                                val res = FormatFactoryEngine.convert(
                                    context = context,
                                    source = src,
                                    target = selectedFormat,
                                    imageQuality = imageQuality,
                                    targetWidth = widthInput.toIntOrNull() ?: 0,
                                    targetHeight = heightInput.toIntOrNull() ?: 0,
                                    videoMaxHeight = videoMaxHeight,
                                    audioBitrateKbps = audioBitrateKbps,
                                    onProgress = { progress = it }
                                )
                                resultInfo = res
                            } catch (_: CancellationException) {
                                FormatFactoryEngine.clearCache(context)
                                errorMsg = "已取消转换"
                            } catch (t: Throwable) {
                                FormatFactoryEngine.clearCache(context)
                                errorMsg = t.message ?: "转换失败，请检查源文件"
                            } finally {
                                isConverting = false
                            }
                        }
                    },
                    enabled = sourceInfo != null,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidPress()
                ) {
                    Icon(Icons.Rounded.Transform, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始转换为 ${selectedFormat.label}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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

            AnimatedVisibility(visible = resultInfo != null) {
                resultInfo?.let { res ->
                    val subFolder = when {
                        res.format.mime.startsWith("image/") -> OutputStorageHelper.SUB_PICTURES
                        res.format.mime.startsWith("video/") -> OutputStorageHelper.SUB_VIDEOS
                        else -> OutputStorageHelper.SUB_SOUNDS
                    }
                    val saveDirLabel = OutputStorageHelper.getDisplayPath(context, subFolder)
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
                                    val srcSize = sourceInfo?.sizeBytes ?: -1L
                                    val sizeLabel = if (srcSize > 0) {
                                        val diffPct = ((res.outputSizeBytes - srcSize) * 100.0 / srcSize).roundToInt()
                                        val sign = if (diffPct > 0) "+" else ""
                                        "${formatSize(srcSize)} → ${formatSize(res.outputSizeBytes)} (${sign}${diffPct}%)"
                                    } else {
                                        formatSize(res.outputSizeBytes)
                                    }
                                    Text(
                                        text = sizeLabel,
                                        fontSize = 12.5.sp,
                                        color = visuals.cardContentColor.copy(alpha = 0.68f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val uri = FormatFactoryEngine.saveToGalleryOrDownloads(context, res)
                                        if (uri != null) {
                                            savedUri = uri
                                            FormatFactoryEngine.clearCache(context)
                                            Toast.makeText(context, "已保存到 $saveDirLabel", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
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
                                    Text(if (savedUri != null) "已保存" else "保存文件")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val shareUri = savedUri ?: FormatFactoryEngine.saveToGalleryOrDownloads(context, res)?.also {
                                            savedUri = it
                                            FormatFactoryEngine.clearCache(context)
                                        }
                                        if (shareUri != null) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = res.format.mime
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "分享转换结果"))
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

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "未知大小"
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
