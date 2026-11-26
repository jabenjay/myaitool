package com.toolbox.pocket.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toolbox.pocket.data.AppSettings
import com.toolbox.pocket.tools.base64.Base64Mode
import com.toolbox.pocket.tools.base64.Base64Processor
import com.toolbox.pocket.tools.base64.Base64Variant
import com.toolbox.pocket.tools.base64.LineWrapWidth
import com.toolbox.pocket.ui.components.PinchZoomTextViewer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Base64Screen(
    appSettings: AppSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var inputText by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(Base64Mode.ENCODE) }
    var variant by rememberSaveable { mutableStateOf(Base64Variant.STANDARD) }
    var wrapWidth by rememberSaveable { mutableStateOf(LineWrapWidth.COL_64) }
    var preserveParagraphs by rememberSaveable { mutableStateOf(true) }
    var autoFormatJson by rememberSaveable { mutableStateOf(true) }

    var showLineNumbers by rememberSaveable { mutableStateOf(appSettings.defaultShowLineNumbers) }
    var softWrap by rememberSaveable { mutableStateOf(appSettings.defaultSoftWrap) }
    var fontSizeSp by rememberSaveable { mutableFloatStateOf(14f) }
    var isFullScreenReader by rememberSaveable { mutableStateOf(false) }

    val result = remember(
        inputText,
        mode,
        variant,
        wrapWidth,
        autoFormatJson,
        preserveParagraphs
    ) {
        Base64Processor.process(
            input = inputText,
            mode = mode,
            variant = variant,
            wrapWidth = wrapWidth,
            autoFormatJson = autoFormatJson,
            preserveParagraphs = preserveParagraphs
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Base64 加解密",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mode Switch: Encode / Decode
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Base64Mode.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = mode == item,
                        onClick = { mode = item },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Base64Mode.entries.size
                        )
                    ) {
                        Text(item.label)
                    }
                }
            }

            // Formatting & Display Options Chips (horizontally scrollable for small screens)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = variant == Base64Variant.URL_SAFE,
                    onClick = {
                        variant = if (variant == Base64Variant.STANDARD) {
                            Base64Variant.URL_SAFE
                        } else {
                            Base64Variant.STANDARD
                        }
                    },
                    label = { Text(variant.label) }
                )

                if (mode == Base64Mode.ENCODE) {
                    FilterChip(
                        selected = wrapWidth != LineWrapWidth.NONE,
                        onClick = {
                            val entries = LineWrapWidth.entries
                            wrapWidth = entries[(entries.indexOf(wrapWidth) + 1) % entries.size]
                        },
                        label = { Text("分段: ${wrapWidth.label}") }
                    )
                    FilterChip(
                        selected = preserveParagraphs,
                        onClick = { preserveParagraphs = !preserveParagraphs },
                        label = { Text("保留原换行") }
                    )
                } else {
                    FilterChip(
                        selected = autoFormatJson,
                        onClick = { autoFormatJson = !autoFormatJson },
                        label = { Text("JSON 自动排版") }
                    )
                }

                FilterChip(
                    selected = showLineNumbers,
                    onClick = { showLineNumbers = !showLineNumbers },
                    label = { Text("显示行号") }
                )

                FilterChip(
                    selected = softWrap,
                    onClick = { softWrap = !softWrap },
                    label = { Text(if (softWrap) "自动换行" else "横向滚动") }
                )
            }

            // Input Card
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (mode == Base64Mode.ENCODE) "原始文本输入" else "Base64 密文输入",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrEmpty()) {
                                        inputText = clip
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("剪贴板为空")
                                        }
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = "粘贴",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (result.output.isNotEmpty() && !result.isError) {
                                IconButton(
                                    onClick = {
                                        inputText = result.output
                                        mode = if (mode == Base64Mode.ENCODE) {
                                            Base64Mode.DECODE
                                        } else {
                                            Base64Mode.ENCODE
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapVert,
                                        contentDescription = "互换输入输出",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (inputText.isNotEmpty()) {
                                IconButton(
                                    onClick = { inputText = "" },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "清空",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (mode == Base64Mode.ENCODE) {
                                    "在此粘贴或输入长文本（支持多行）..."
                                } else {
                                    "在此粘贴 Base64 字符串（自动忽略多余换行）..."
                                }
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                    )
                }
            }

            // Output Card with Pinch-to-Zoom & Organized Line Display
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Output Header & Zoom Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (result.isPrettyJson) "输出结果（已自动格式化 JSON）" else "输出结果 · 支持双指捏合缩放",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (result.output.isNotEmpty() && !result.isError) {
                                Text(
                                    text = "${result.lineCount} 行 · ${result.charCount} 字符 · ${result.byteCount} 字节",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { fontSizeSp = (fontSizeSp - 2f).coerceAtLeast(8f) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomOut,
                                    contentDescription = "缩小字体",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${((fontSizeSp / 14f) * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { fontSizeSp = (fontSizeSp + 2f).coerceAtMost(32f) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomIn,
                                    contentDescription = "放大字体",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (fontSizeSp != 14f) {
                                IconButton(
                                    onClick = { fontSizeSp = 14f },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestartAlt,
                                        contentDescription = "重置缩放",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { isFullScreenReader = true },
                                enabled = result.output.isNotEmpty() && !result.isError,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInFull,
                                    contentDescription = "全屏阅读",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    if (result.output.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(result.output))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("已复制结果到剪贴板")
                                        }
                                    }
                                },
                                enabled = result.output.isNotEmpty() && !result.isError,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "复制结果",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = result.isError) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = result.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                    ) {
                        PinchZoomTextViewer(
                            text = if (result.isError) "" else result.output,
                            fontSizeSp = fontSizeSp,
                            onFontSizeChange = { fontSizeSp = it },
                            showLineNumbers = showLineNumbers,
                            softWrap = softWrap,
                            placeholder = "处理结果将在此处按行整理展示\n可用双指捏合手势随时放大或缩小文字",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Full-screen distraction-free reader modal for very long text
    if (isFullScreenReader) {
        Dialog(
            onDismissRequest = { isFullScreenReader = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "全屏阅读模式",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "双指捏合可缩放 · ${result.lineCount} 行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(result.output))
                                scope.launch {
                                    snackbarHostState.showSnackbar("已复制结果")
                                }
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { isFullScreenReader = false }) {
                                Icon(Icons.Rounded.CloseFullscreen, contentDescription = "退出全屏")
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        PinchZoomTextViewer(
                            text = result.output,
                            fontSizeSp = fontSizeSp,
                            onFontSizeChange = { fontSizeSp = it },
                            showLineNumbers = showLineNumbers,
                            softWrap = softWrap,
                            placeholder = "",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
