package com.toolbox.pocket.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toolbox.pocket.data.AppSettings
import com.toolbox.pocket.tools.base64.Base64Mode
import com.toolbox.pocket.tools.base64.Base64Processor
import com.toolbox.pocket.tools.base64.Base64Variant
import com.toolbox.pocket.tools.base64.LineWrapWidth
import com.toolbox.pocket.ui.components.PinchZoomTextViewer
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Base64Screen(
    appSettings: AppSettings,
    onDefaultLineNumbersChange: (Boolean) -> Unit,
    onDefaultSoftWrapChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val visuals = LocalAppThemeVisuals.current

    var topText by rememberSaveable { mutableStateOf("") }
    var bottomText by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var lastActionInfo by rememberSaveable { mutableStateOf("") }

    var variant by rememberSaveable { mutableStateOf(Base64Variant.STANDARD) }
    var wrapWidth by rememberSaveable { mutableStateOf(LineWrapWidth.COL_64) }
    var preserveParagraphs by rememberSaveable { mutableStateOf(true) }
    var autoFormatJson by rememberSaveable { mutableStateOf(true) }

    var showLineNumbers by rememberSaveable { mutableStateOf(appSettings.defaultShowLineNumbers) }
    var softWrap by rememberSaveable { mutableStateOf(appSettings.defaultSoftWrap) }
    var inputFontSizeSp by rememberSaveable { mutableFloatStateOf(14f) }
    var fontSizeSp by rememberSaveable { mutableFloatStateOf(14f) }
    var outputLiveScale by remember { mutableFloatStateOf(1f) }
    val latestOutputFontSize by rememberUpdatedState(fontSizeSp)
    var inputLiveScale by remember { mutableFloatStateOf(1f) }
    val latestInputFontSize by rememberUpdatedState(inputFontSizeSp)
    var isFullScreenReader by rememberSaveable { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Resizable box heights (dp) via bottom-right drag handle
    var topBoxHeightDp by rememberSaveable { mutableFloatStateOf(170f) }
    var bottomBoxHeightDp by rememberSaveable { mutableFloatStateOf(270f) }

    fun runTransform(mode: Base64Mode) {
        if (topText.isEmpty()) {
            bottomText = ""
            errorMessage = null
            lastActionInfo = ""
            return
        }
        val res = Base64Processor.process(
            input = topText,
            mode = mode,
            variant = variant,
            wrapWidth = wrapWidth,
            autoFormatJson = autoFormatJson,
            preserveParagraphs = preserveParagraphs
        )
        if (res.isError) {
            errorMessage = res.errorMessage
        } else {
            errorMessage = null
            bottomText = res.output
            val jsonTag = if (res.isPrettyJson) " · JSON" else ""
            lastActionInfo = "${res.lineCount} 行 · ${res.charCount} 字符$jsonTag"
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Base64",
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
                actions = {
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "选项"
                            )
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编码模式：${variant.label}", maxLines = 1, softWrap = false) },
                                onClick = {
                                    variant = if (variant == Base64Variant.STANDARD) {
                                        Base64Variant.URL_SAFE
                                    } else {
                                        Base64Variant.STANDARD
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("编码分段：${wrapWidth.label}", maxLines = 1, softWrap = false) },
                                onClick = {
                                    val entries = LineWrapWidth.entries
                                    wrapWidth = entries[(entries.indexOf(wrapWidth) + 1) % entries.size]
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("保留原换行", maxLines = 1, softWrap = false) },
                                trailingIcon = {
                                    Checkbox(checked = preserveParagraphs, onCheckedChange = null)
                                },
                                onClick = { preserveParagraphs = !preserveParagraphs }
                            )
                            DropdownMenuItem(
                                text = { Text("JSON 自动排版", maxLines = 1, softWrap = false) },
                                trailingIcon = {
                                    Checkbox(checked = autoFormatJson, onCheckedChange = null)
                                },
                                onClick = { autoFormatJson = !autoFormatJson }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("显示行号（默认）", maxLines = 1, softWrap = false) },
                                trailingIcon = {
                                    Checkbox(checked = showLineNumbers, onCheckedChange = null)
                                },
                                onClick = {
                                    val next = !showLineNumbers
                                    showLineNumbers = next
                                    onDefaultLineNumbersChange(next)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("自动换行（默认）", maxLines = 1, softWrap = false) },
                                trailingIcon = {
                                    Checkbox(checked = softWrap, onCheckedChange = null)
                                },
                                onClick = {
                                    val next = !softWrap
                                    softWrap = next
                                    onDefaultSoftWrapChange(next)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {


            // Top Input Card (with bottom-right resize handle)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBoxHeightDp.dp)
                    .clip(MaterialTheme.shapes.large)
                    .liquidGlassSurface(26.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "输入",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(((inputFontSizeSp * inputLiveScale).coerceIn(1.4f, 56f) / 14f) * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                                if (inputFontSizeSp != 14f) {
                                    IconButton(
                                        onClick = { inputFontSizeSp = 14f },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.RestartAlt,
                                            contentDescription = "重置输入缩放",
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrEmpty()) {
                                            topText = clip
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar("剪贴板为空") }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "粘贴",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (topText.isNotEmpty()) {
                                    IconButton(
                                        onClick = { topText = "" },
                                        modifier = Modifier.size(32.dp)
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        var pinched = false
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.changes.count { it.pressed } >= 2) {
                                                val zoom = event.calculateZoom()
                                                if (zoom != 1f) {
                                                    pinched = true
                                                    val minR = 1.4f / latestInputFontSize
                                                    val maxR = 56f / latestInputFontSize
                                                    inputLiveScale = (inputLiveScale * zoom).coerceIn(minR, maxR)
                                                }
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        } while (event.changes.any { it.pressed })
                                        if (pinched) {
                                            inputFontSizeSp = (latestInputFontSize * inputLiveScale).coerceIn(1.4f, 56f)
                                            inputLiveScale = 1f
                                        }
                                    }
                                }
                        ) {
                            OutlinedTextField(
                                value = topText,
                                onValueChange = { topText = it },
                                placeholder = { Text("输入或粘贴需要处理的长文本...") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = inputFontSizeSp.sp,
                                    lineHeight = (inputFontSizeSp * 1.45f).sp
                                ),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = visuals.innerFieldColor,
                                    unfocusedContainerColor = visuals.innerFieldColor
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = inputLiveScale
                                        scaleY = inputLiveScale
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    }
                            )
                        }
                    }

                    ResizeCornerHandle(
                        onDragDeltaY = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            topBoxHeightDp = (topBoxHeightDp + deltaDp).coerceIn(120f, 460f)
                        },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            // Middle Action Row: Encode, Decode, and Swap Top/Bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { runTransform(Base64Mode.ENCODE) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("编码")
                }

                FilledTonalButton(
                    onClick = { runTransform(Base64Mode.DECODE) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("解码")
                }

                FilledTonalIconButton(
                    onClick = {
                        val tmp = topText
                        topText = bottomText
                        bottomText = tmp
                        errorMessage = null
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "上下互换"
                    )
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Bottom Output Card (with Pinch-to-Zoom + bottom-right resize handle)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomBoxHeightDp.dp)
                    .clip(MaterialTheme.shapes.large)
                    .liquidGlassSurface(26.dp)
                    .padding(bottom = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "输出",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (lastActionInfo.isNotEmpty()) {
                                    Text(
                                        text = lastActionInfo,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${((fontSizeSp / 14f) * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (fontSizeSp != 14f) {
                                    IconButton(
                                        onClick = { fontSizeSp = 14f },
                                        modifier = Modifier.size(32.dp)
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
                                    enabled = bottomText.isNotEmpty(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInFull,
                                        contentDescription = "全屏",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        if (bottomText.isNotEmpty()) {
                                            clipboardManager.setText(AnnotatedString(bottomText))
                                            scope.launch { snackbarHostState.showSnackbar("已复制") }
                                        }
                                    },
                                    enabled = bottomText.isNotEmpty(),
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "复制",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = visuals.innerFieldColor,
                            contentColor = visuals.cardContentColor,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 10.dp, end = 10.dp, bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            var pinched = false
                                            do {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.changes.count { it.pressed } >= 2) {
                                                    val zoom = event.calculateZoom()
                                                    if (zoom != 1f) {
                                                        pinched = true
                                                        val minR = 1.4f / latestOutputFontSize
                                                        val maxR = 56f / latestOutputFontSize
                                                        outputLiveScale = (outputLiveScale * zoom).coerceIn(minR, maxR)
                                                    }
                                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                                }
                                            } while (event.changes.any { it.pressed })
                                            if (pinched) {
                                                fontSizeSp = (latestOutputFontSize * outputLiveScale).coerceIn(1.4f, 56f)
                                                outputLiveScale = 1f
                                            }
                                        }
                                    }
                            ) {
                                OutlinedTextField(
                                    value = bottomText,
                                    onValueChange = { bottomText = it },
                                    placeholder = { Text("点击上方「编码」或「解码」查看结果（可直接编辑）") },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp,
                                        lineHeight = (fontSizeSp * 1.46f).sp
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = visuals.innerFieldColor,
                                        unfocusedContainerColor = visuals.innerFieldColor
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = outputLiveScale
                                            scaleY = outputLiveScale
                                            transformOrigin = TransformOrigin(0f, 0f)
                                        }
                                )
                            }
                        }
                    }

                    ResizeCornerHandle(
                        onDragDeltaY = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            bottomBoxHeightDp = (bottomBoxHeightDp + deltaDp).coerceIn(150f, 560f)
                        },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }

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
                        Text(
                            text = "阅读视图",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(bottomText))
                                scope.launch { snackbarHostState.showSnackbar("已复制") }
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
                            }
                            IconButton(onClick = { isFullScreenReader = false }) {
                                Icon(Icons.Rounded.CloseFullscreen, contentDescription = "退出")
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        PinchZoomTextViewer(
                            text = bottomText,
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

@Composable
private fun ResizeCornerHandle(
    onDragDeltaY: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnDrag by rememberUpdatedState(onDragDeltaY)
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .size(34.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    latestOnDrag(dragAmount.y)
                }
            },
        contentAlignment = Alignment.BottomEnd
    ) {
        Canvas(
            modifier = Modifier
                .padding(end = 9.dp, bottom = 9.dp)
                .size(12.dp)
        ) {
            drawLine(
                color = handleColor,
                start = Offset(size.width * 0.25f, size.height),
                end = Offset(size.width, size.height * 0.25f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = handleColor,
                start = Offset(size.width * 0.65f, size.height),
                end = Offset(size.width, size.height * 0.65f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
