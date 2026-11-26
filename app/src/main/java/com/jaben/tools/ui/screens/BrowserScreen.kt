package com.jaben.tools.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.jaben.tools.data.AdBlockManager
import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.content.Intent
import android.net.Uri
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jaben.tools.data.AppLogManager
import com.jaben.tools.data.BookmarkItem
import com.jaben.tools.data.ProxyLatency
import com.jaben.tools.data.DownloadStatus
import com.jaben.tools.data.DownloadTaskItem
import com.jaben.tools.data.OutputStorageHelper
import com.jaben.tools.data.ProxyState
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidPress

private const val DEFAULT_HOME_URL = "https://cn.pornhub.com/"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String = DEFAULT_HOME_URL,
    homeUrl: String = DEFAULT_HOME_URL,
    bookmarks: List<BookmarkItem>,
    proxyState: ProxyState,
    onToggleProxy: (Boolean) -> Unit,
    onSelectProxy: (String) -> Unit,
    onTestProxyNode: (String) -> Unit,
    onTestAllProxies: () -> Unit,
    downloadItems: List<DownloadTaskItem>,
    onStartDownload: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onClearFinishedDownloads: () -> Unit,
    onSetHomeUrl: (String) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onUpdateBookmark: (String, String, String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onMoveBookmark: (Int, Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    val barBg = if (visuals.isDark) Color(0xFF14161C) else Color(0xFFFFFFFF)
    val pillBg = if (visuals.isDark) Color(0xFF232732) else Color(0xFFEEF1F6)
    val barFg = if (visuals.isDark) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)
    val menuBg = if (visuals.isDark) Color(0xFF1C1F28) else Color(0xFFFFFFFF)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val startUrl = remember(initialUrl) {
        val t = initialUrl.trim()
        when {
            t.isEmpty() -> homeUrl
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://www.bing.com/search?q=${android.net.Uri.encode(t)}"
        }
    }
    var urlInput by remember { mutableStateOf(startUrl) }
    var showBookmarks by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showLoadingOverlay by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(6000); showLoadingOverlay = false }
    LaunchedEffect(proxyState.enabled, proxyState.selectedId) {
        val sid = proxyState.selectedId
        if (proxyState.enabled && sid != null && proxyState.latencies[sid] == null) {
            onTestProxyNode(sid)
        }
    }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var editingBookmark by remember { mutableStateOf<BookmarkItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showAdBlockRulesDialog by remember { mutableStateOf(false) }
    var newRuleInput by remember { mutableStateOf("") }

    val adBlockManager = remember(context) { AdBlockManager(context) }
    var adBlockEnabled by remember { mutableStateOf(adBlockManager.isEnabled()) }
    var adBlockRules by remember { mutableStateOf(adBlockManager.getRules()) }
    val currentAdBlockEnabled by rememberUpdatedState(adBlockEnabled)
    val currentAdBlockRules by rememberUpdatedState(adBlockRules)

    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isLandscapeFullscreen by remember { mutableStateOf(true) }

    val hostActivity = remember(context) { context.findActivity() }

    fun setSystemBarsVisible(visible: Boolean) {
        val act = hostActivity ?: return
        val window = act.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun exitVideoFullscreen() {
        runCatching { customViewCallback?.onCustomViewHidden() }
        customVideoView = null
        customViewCallback = null
        isLandscapeFullscreen = true
        setSystemBarsVisible(true)
        hostActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun navigateTo(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val target = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.bing.com/search?q=${android.net.Uri.encode(trimmed)}"
        }
        urlInput = target
        showBookmarks = false
        runCatching {
            webViewRef?.stopLoading()
            AppLogManager.log("BROWSER", "访问: $target")
            webViewRef?.loadUrl(target)
        }
        runCatching { focusManager.clearFocus() }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (customVideoView != null) {
                exitVideoFullscreen()
            }
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            }
        }
    }

    BackHandler(enabled = customVideoView != null || showMenuSheet || showBookmarks || canGoBack) {
        when {
            customVideoView != null -> exitVideoFullscreen()
            showMenuSheet -> showMenuSheet = false
            showBookmarks -> {
                showBookmarks = false
                focusManager.clearFocus()
            }
            else -> runCatching { webViewRef?.goBack() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(barBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // High-contrast Via-style address bar
        Surface(
            color = barBg,
            contentColor = barFg,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pillBg,
                        contentColor = barFg,
                        border = BorderStroke(1.dp, barFg.copy(alpha = 0.14f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (urlInput.isEmpty()) {
                                    Text(
                                        text = "输入网址或搜索...",
                                        fontSize = 13.5.sp,
                                        color = barFg.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = barFg,
                                        fontSize = 13.5.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = { navigateTo(urlInput) }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                showBookmarks = true
                                            }
                                        }
                                )
                            }
                            if (urlInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlInput = "" },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "清空",
                                        tint = barFg.copy(alpha = 0.6f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    val activeLatency = proxyState.selectedId?.let { proxyState.latencies[it] }
                    val proxyIconTint = when {
                        !proxyState.enabled -> barFg.copy(alpha = 0.72f)
                        activeLatency is ProxyLatency.Success && activeLatency.ms < 600L -> Color(0xFF22C55E)
                        activeLatency is ProxyLatency.Success -> Color(0xFFF59E0B)
                        activeLatency is ProxyLatency.Failed -> Color(0xFFEF4444)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .liquidPress()
                            .combinedClickable(
                                onClick = {
                                    val next = !proxyState.enabled
                                    onToggleProxy(next)
                                    if (next) {
                                        proxyState.selectedId?.let { onTestProxyNode(it) }
                                        Toast.makeText(context, "代理已开启", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "代理已关闭", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showProxyDialog = true
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (proxyState.enabled) Icons.Rounded.Public else Icons.Outlined.Public,
                            contentDescription = "代理开关（长按配置）",
                            tint = proxyIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { runCatching { webViewRef?.reload() } },
                        modifier = Modifier
                            .size(36.dp)
                            .liquidPress()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = barFg,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    val normalizedCurrent = urlInput.trim().removeSuffix("/")
                    val existingBookmark = bookmarks.firstOrNull {
                        it.url.trim().removeSuffix("/").equals(normalizedCurrent, ignoreCase = true)
                    }
                    val isBookmarked = existingBookmark != null

                    IconButton(
                        onClick = {
                            if (existingBookmark != null) {
                                onDeleteBookmark(existingBookmark.id)
                                Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
                            } else if (urlInput.isNotBlank()) {
                                val title = pageTitle.ifBlank {
                                    urlInput.removePrefix("https://").removePrefix("http://").substringBefore("/")
                                }
                                onAddBookmark(title, urlInput)
                                Toast.makeText(context, "已收藏到书签", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .liquidPress()
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "收藏为书签",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else barFg,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                if (isLoading && progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }

        // Main content area: WebView + overlaid bookmark dropdown menu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .zIndex(0f)
                .clipToBounds()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        clipToOutline = true
                        setBackgroundColor(barBg.toArgb())
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                            if (!url.isNullOrBlank()) {
                                onStartDownload(url, userAgent, contentDisposition, mimetype, contentLength)
                                Toast.makeText(context, "已添加到下载队列", Toast.LENGTH_SHORT).show()
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                if (currentAdBlockEnabled && request != null && !request.isForMainFrame) {
                                    val reqUrl = request.url?.toString().orEmpty()
                                    if (adBlockManager.shouldBlock(reqUrl, currentAdBlockRules)) {
                                        return AdBlockManager.emptyResponse()
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase() ?: return true
                                return scheme != "http" && scheme != "https"
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                if (!url.isNullOrEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    urlInput = url
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                if (currentAdBlockEnabled) {
                                    view?.evaluateJavascript(AdBlockManager.COSMETIC_JS, null)
                                }
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                showLoadingOverlay = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                showLoadingOverlay = false
                                if (!url.isNullOrEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    urlInput = url
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    AppLogManager.log("BROWSER", "加载失败: ${error?.description} (${error?.errorCode})")
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                AppLogManager.log("BROWSER", "WebView渲染进程异常退出，已拦截防闪退")
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 75) showLoadingOverlay = false
                                if (newProgress >= 100) isLoading = false
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (view == null) return
                                if (customVideoView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customVideoView = view
                                customViewCallback = callback
                                isLandscapeFullscreen = true
                                hostActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                setSystemBarsVisible(false)
                            }

                            override fun onHideCustomView() {
                                exitVideoFullscreen()
                            }
                        }

                        AppLogManager.log("BROWSER", "初始化WebView并加载主页")
                    loadUrl(startUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Themed loading animation page while WebView prepares first paint
            androidx.compose.animation.AnimatedVisibility(
                visible = showLoadingOverlay,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(260))
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "browserSplash")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Text(
                            text = "正在加载页面...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = barFg
                        )
                    }
                }
            }

            // Scrim to dismiss bookmark menu when tapping outside
            androidx.compose.animation.AnimatedVisibility(
                visible = showBookmarks,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showBookmarks = false
                            focusManager.clearFocus()
                        }
                )
            }

            // Smooth Bookmark Dropdown Menu right below the address bar
            androidx.compose.animation.AnimatedVisibility(
                visible = showBookmarks,
                enter = fadeIn(tween(180)) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(tween(140)) + shrinkVertically(
                    animationSpec = tween(160),
                    shrinkTowards = Alignment.Top
                )
            ) {
                BookmarkDropdownPanel(
                    bookmarks = bookmarks,
                    homeUrl = homeUrl,
                    panelBg = menuBg,
                    panelFg = barFg,
                    onSelectBookmark = { navigateTo(it.url) },
                    onSetHome = { item ->
                        onSetHomeUrl(item.url)
                        Toast.makeText(context, "已设为浏览器主页", Toast.LENGTH_SHORT).show()
                    },
                    onEditBookmark = { editingBookmark = it },
                    onMoveBookmark = onMoveBookmark,
                    onAddClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Minimal Via-style bottom toolbar
        Surface(
            color = barBg,
            contentColor = barFg,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .zIndex(2f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { runCatching { webViewRef?.goBack() } },
                    enabled = canGoBack,
                    modifier = Modifier.liquidPress()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "后退",
                        tint = if (canGoBack) barFg else barFg.copy(alpha = 0.3f),
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(
                    onClick = { runCatching { webViewRef?.goForward() } },
                    enabled = canGoForward,
                    modifier = Modifier.liquidPress()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "前进",
                        tint = if (canGoForward) barFg else barFg.copy(alpha = 0.3f),
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(
                    onClick = { navigateTo(homeUrl) },
                    modifier = Modifier.liquidPress()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "主页",
                        tint = barFg,
                        modifier = Modifier.size(21.dp)
                    )
                }
                val activeDownloadCount = downloadItems.count { it.status == DownloadStatus.DOWNLOADING }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { showMenuSheet = true },
                        modifier = Modifier.liquidPress()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "菜单",
                            tint = if (activeDownloadCount > 0) MaterialTheme.colorScheme.primary else barFg,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    if (activeDownloadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                IconButton(
                    onClick = onExit,
                    modifier = Modifier.liquidPress()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "退出浏览器",
                        tint = barFg,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }

    if (showDownloadsDialog) {
        val activeRootLabel = OutputStorageHelper.getDisplayPath(context, "").trimEnd('/')
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            title = {
                Column {
                    Text("下载管理", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "保存目录: $activeRootLabel/<分类>",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                if (downloadItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无下载记录",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        downloadItems.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (item.subFolder) {
                                                OutputStorageHelper.SUB_PICTURES -> Icons.Rounded.Image
                                                OutputStorageHelper.SUB_VIDEOS -> Icons.Rounded.VideoFile
                                                OutputStorageHelper.SUB_SOUNDS -> Icons.Rounded.AudioFile
                                                else -> Icons.Rounded.InsertDriveFile
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.fileName,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val statusText = when (item.status) {
                                                DownloadStatus.DOWNLOADING -> {
                                                    if (item.totalBytes > 0L) {
                                                        "${formatDlBytes(item.downloadedBytes)} / ${formatDlBytes(item.totalBytes)}"
                                                    } else {
                                                        "已下载 ${formatDlBytes(item.downloadedBytes)}"
                                                    }
                                                }
                                                DownloadStatus.COMPLETED -> "${item.subFolder} · ${formatDlBytes(item.downloadedBytes)}"
                                                DownloadStatus.FAILED -> "失败: ${item.errorMessage ?: "未知错误"}"
                                                DownloadStatus.CANCELLED -> "已取消"
                                            }
                                            Text(
                                                text = statusText,
                                                fontSize = 11.5.sp,
                                                color = if (item.status == DownloadStatus.FAILED) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (item.status == DownloadStatus.DOWNLOADING) {
                                            IconButton(
                                                onClick = { onCancelDownload(item.id) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "取消",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            if (item.status == DownloadStatus.COMPLETED && item.savedUri != null) {
                                                IconButton(
                                                    onClick = {
                                                        val uri = Uri.parse(item.savedUri)
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = item.mimeType
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "分享文件"))
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Share,
                                                        contentDescription = "分享",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onRemoveDownload(item.id) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "删除记录",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (item.status == DownloadStatus.DOWNLOADING) {
                                        if (item.totalBytes > 0L) {
                                            LinearProgressIndicator(
                                                progress = { (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                            )
                                        }
                                    } else if (item.status == DownloadStatus.COMPLETED && item.savedUri != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    runCatching {
                                                        val uri = Uri.parse(item.savedUri)
                                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(uri, item.mimeType)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(viewIntent)
                                                    }.onFailure {
                                                        Toast.makeText(context, "未找到可打开该文件的应用", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("打开文件", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) {
                    Text("关闭")
                }
            },
            dismissButton = {
                if (downloadItems.any { it.status != DownloadStatus.DOWNLOADING }) {
                    TextButton(onClick = onClearFinishedDownloads) {
                        Text("清空已完成")
                    }
                }
            }
        )
    }

    if (showProxyDialog) {
        AlertDialog(
            onDismissRequest = { showProxyDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("网络代理", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = proxyState.statusMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = proxyState.enabled,
                        onCheckedChange = { next ->
                            onToggleProxy(next)
                            if (next) {
                                proxyState.selectedId?.let { onTestProxyNode(it) }
                            }
                        }
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    proxyState.nodes.forEach { node ->
                        val isSelected = node.id == proxyState.selectedId
                        val lat = proxyState.latencies[node.id]
                        val (latText, latColor) = when (lat) {
                            is ProxyLatency.Testing -> "测速中..." to MaterialTheme.colorScheme.primary
                            is ProxyLatency.Success -> "${lat.ms} ms" to (if (lat.ms < 600L) Color(0xFF22C55E) else Color(0xFFF59E0B))
                            is ProxyLatency.Failed -> "不可用" to Color(0xFFEF4444)
                            null -> "未测速" to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectProxy(node.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectProxy(node.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = node.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = node.summary()
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = latText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = latColor
                                )
                                IconButton(
                                    onClick = { onTestProxyNode(node.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = "测速",
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProxyDialog = false }) {
                    Text("完成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onTestAllProxies,
                    enabled = !proxyState.isTestingAll
                ) {
                    Text(if (proxyState.isTestingAll) "测速中..." else "全部测速")
                }
            }
        )
    }

    if (showAddDialog) {
        BookmarkEditDialog(
            title = "添加书签",
            initialName = "",
            initialUrl = "",
            onDismiss = { showAddDialog = false },
            onDelete = null,
            onSave = { name, url ->
                onAddBookmark(name, url)
                showAddDialog = false
            }
        )
    }

    editingBookmark?.let { target ->
        BookmarkEditDialog(
            title = "编辑书签",
            initialName = target.name,
            initialUrl = target.url,
            onDismiss = { editingBookmark = null },
            onDelete = {
                onDeleteBookmark(target.id)
                editingBookmark = null
            },
            onSave = { name, url ->
                onUpdateBookmark(target.id, name, url)
                editingBookmark = null
            }
        )
    }

    if (showMenuSheet) {
        val activeDownloadCount = downloadItems.count { it.status == DownloadStatus.DOWNLOADING }
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "浏览器菜单",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // 1. 下载管理
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showMenuSheet = false
                            showDownloadsDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("下载管理", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (activeDownloadCount > 0) "正在下载 $activeDownloadCount 个任务" else "查看与管理下载文件",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. 屏蔽广告（点击开关，长按编辑规则）
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = {
                                val next = !adBlockEnabled
                                adBlockEnabled = next
                                adBlockManager.setEnabled(next)
                                Toast.makeText(
                                    context,
                                    if (next) "已开启广告屏蔽" else "已关闭广告屏蔽",
                                    Toast.LENGTH_SHORT
                                ).show()
                                runCatching { webViewRef?.reload() }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenuSheet = false
                                showAdBlockRulesDialog = true
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            tint = if (adBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("屏蔽广告", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "点击开关 · 长按编辑规则 (${adBlockRules.size} 条)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = adBlockEnabled,
                            onCheckedChange = { next ->
                                adBlockEnabled = next
                                adBlockManager.setEnabled(next)
                                Toast.makeText(
                                    context,
                                    if (next) "已开启广告屏蔽" else "已关闭广告屏蔽",
                                    Toast.LENGTH_SHORT
                                ).show()
                                runCatching { webViewRef?.reload() }
                            }
                        )
                    }
                }

                // 3. 网页视频全屏辅助
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showMenuSheet = false
                            webViewRef?.evaluateJavascript(AdBlockManager.FORCE_VIDEO_FULLSCREEN_JS) { res ->
                                val cleaned = res?.trim('"') ?: ""
                                if (cleaned == "NO_VIDEO") {
                                    Toast.makeText(context, "当前页面未检测到视频", Toast.LENGTH_SHORT).show()
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
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("视频全屏播放", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "将当前网页视频切换为横屏全屏",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAdBlockRulesDialog) {
        AlertDialog(
            onDismissRequest = { showAdBlockRulesDialog = false },
            title = { Text("编辑广告屏蔽规则", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newRuleInput,
                            onValueChange = { newRuleInput = it },
                            placeholder = { Text("输入域名或关键词，如 doubleclick.net", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val trimmed = newRuleInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    adBlockRules = adBlockManager.addRule(trimmed)
                                    newRuleInput = ""
                                }
                            }
                        ) {
                            Text("添加")
                        }
                    }

                    Text(
                        text = "当前规则 (${adBlockRules.size} 条)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(adBlockRules, key = { idx, item -> "${idx}_$item" }) { _, rule ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rule,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            adBlockRules = adBlockManager.removeRule(rule)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "删除规则",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdBlockRulesDialog = false
                        if (adBlockEnabled) {
                            runCatching { webViewRef?.reload() }
                        }
                    }
                ) {
                    Text("完成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        adBlockRules = adBlockManager.resetToDefault()
                        Toast.makeText(context, "已恢复默认规则", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("恢复默认")
                }
            }
        )
    }

    if (customVideoView != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .zIndex(20f)
        ) {
            AndroidView(
                factory = { customVideoView!! },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val nextLandscape = !isLandscapeFullscreen
                        isLandscapeFullscreen = nextLandscape
                        hostActivity?.requestedOrientation = if (nextLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = "旋转屏幕",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { exitVideoFullscreen() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FullscreenExit,
                        contentDescription = "退出全屏",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}



@Composable
private fun BookmarkDropdownPanel(
    bookmarks: List<BookmarkItem>,
    homeUrl: String,
    panelBg: Color,
    panelFg: Color,
    onSelectBookmark: (BookmarkItem) -> Unit,
    onSetHome: (BookmarkItem) -> Unit,
    onEditBookmark: (BookmarkItem) -> Unit,
    onMoveBookmark: (Int, Int) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(140f) }

    val latestBookmarks by rememberUpdatedState(bookmarks)
    val latestOnMove by rememberUpdatedState(onMoveBookmark)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = panelBg,
        contentColor = panelFg,
        shadowElevation = 10.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clipToBounds()
            ) {
                itemsIndexed(bookmarks, key = { _, item -> item.id }) { index, item ->
                    val isDragging = draggedId == item.id
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.015f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.76f,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "bookmarkFloatScale"
                    )
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 12.dp else 0.dp,
                        label = "bookmarkFloatElevation"
                    )
                    val isDarkPanel = panelBg.red < 0.5f
                    val rowBg = if (isDragging) {
                        if (isDarkPanel) Color(0xFF2B3140) else Color(0xFFF0F4FA)
                    } else {
                        panelBg
                    }

                    Surface(
                        color = rowBg,
                        contentColor = panelFg,
                        shadowElevation = elevation,
                        shape = RoundedCornerShape(if (isDragging) 14.dp else 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 10f else 0f)
                            .then(if (!isDragging) Modifier.animateItem() else Modifier)
                            .onSizeChanged { size ->
                                if (size.height > 0) rowHeightPx = size.height.toFloat()
                            }
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetY
                                }
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBookmark(item) }
                                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = panelFg,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.url.removePrefix("https://").removePrefix("http://"),
                                    fontSize = 11.5.sp,
                                    color = panelFg.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Pencil edit button
                            IconButton(
                                onClick = { onEditBookmark(item) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "编辑书签",
                                    tint = panelFg.copy(alpha = 0.72f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Home button between edit and sort buttons (filled if current homepage)
                            val isCurrentHome = item.url.trim().removeSuffix("/")
                                .equals(homeUrl.trim().removeSuffix("/"), ignoreCase = true)
                            IconButton(
                                onClick = { onSetHome(item) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentHome) Icons.Rounded.Home else Icons.Outlined.Home,
                                    contentDescription = "设为主页",
                                    tint = if (isCurrentHome) MaterialTheme.colorScheme.primary else panelFg.copy(alpha = 0.65f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Drag handle button to the right of home button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .pointerInput(item.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedId = item.id
                                                dragOffsetY = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = {
                                                draggedId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedId = null
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val currentList = latestBookmarks
                                                val currentIdx = currentList.indexOfFirst { it.id == item.id }
                                                if (currentIdx != -1) {
                                                    val threshold = rowHeightPx * 0.55f
                                                    if (dragOffsetY > threshold && currentIdx < currentList.lastIndex) {
                                                        latestOnMove(currentIdx, currentIdx + 1)
                                                        dragOffsetY -= rowHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } else if (dragOffsetY < -threshold && currentIdx > 0) {
                                                        latestOnMove(currentIdx, currentIdx - 1)
                                                        dragOffsetY += rowHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "拖动排序",
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else panelFg.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = panelFg.copy(alpha = 0.1f))

            // Bottom row: Add Bookmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "添加书签",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun BookmarkEditDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, softWrap = false) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("书签名称", maxLines = 1, softWrap = false) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("网址（例如 www.baidu.com）", maxLines = 1, softWrap = false) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                    }
                }
                FilledTonalButton(
                    enabled = url.isNotBlank(),
                    onClick = { onSave(name, url) }
                ) {
                    Text("保存", maxLines = 1, softWrap = false)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", maxLines = 1, softWrap = false)
            }
        }
    )

}
private fun formatDlBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
