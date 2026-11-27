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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Checkbox
import com.jaben.tools.data.BrowserHistoryItem
import com.jaben.tools.data.BrowserHistoryManager
import android.webkit.JavascriptInterface
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.rememberCoroutineScope
import com.jaben.tools.data.AdBlockSubscription
import com.jaben.tools.data.CompiledAdBlockRules
import com.jaben.tools.ui.components.BuiltInVideoPlayerOverlay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    var showHistoryDialog by remember { mutableStateOf(false) }
    var isHistoryMultiSelect by remember { mutableStateOf(false) }
    var selectedHistoryIds by remember { mutableStateOf(setOf<String>()) }
    var hasTypedInAddress by remember { mutableStateOf(false) }
    var isAddressFocused by remember { mutableStateOf(false) }
    val historyManager = remember(context) { BrowserHistoryManager(context) }
    var historyItems by remember { mutableStateOf(historyManager.getHistory()) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showAdBlockRulesDialog by remember { mutableStateOf(false) }
    var adBlockDialogTab by remember { mutableIntStateOf(0) }
    var newSubNameInput by remember { mutableStateOf("") }
    var newSubUrlInput by remember { mutableStateOf("") }
    var newDomainInput by remember { mutableStateOf("") }
    var isRefreshingSubs by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val adBlockManager = remember(context) { AdBlockManager(context) }
    var adBlockEnabled by remember { mutableStateOf(adBlockManager.isEnabled()) }
    var adBlockSubscriptions by remember { mutableStateOf(adBlockManager.getSubscriptions()) }
    var customDomains by remember { mutableStateOf(adBlockManager.getCustomDomains()) }
    var compiledAdRules by remember { mutableStateOf(adBlockManager.compileActiveRules()) }
    val currentAdBlockEnabled by rememberUpdatedState(adBlockEnabled)
    val currentCompiledAdRules by rememberUpdatedState(compiledAdRules)

    var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
    var detectedVideoPosMs by remember { mutableStateOf(0L) }
    var detectedVideoTitle by remember { mutableStateOf("") }
    var detectedVideoPosterUrl by remember { mutableStateOf("") }
    var activeBuiltInVideoUrl by remember { mutableStateOf<String?>(null) }
    var userDismissedFloatingForPage by remember { mutableStateOf(false) }
    var lastVideoPageUrl by remember { mutableStateOf("") }
    var webViewUserAgent by remember { mutableStateOf("Mozilla/5.0") }

    LaunchedEffect(Unit) {
        val subs = adBlockManager.getSubscriptions()
        val needsInitialFetch = subs.any { it.enabled && it.lastUpdatedMs == 0L }
        if (needsInitialFetch) {
            isRefreshingSubs = true
            adBlockManager.refreshAllEnabled()
            adBlockSubscriptions = adBlockManager.getSubscriptions()
            compiledAdRules = adBlockManager.compileActiveRules()
            isRefreshingSubs = false
        }
    }

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
        hasTypedInAddress = false
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
                                    onValueChange = {
                                        urlInput = it
                                        hasTypedInAddress = true
                                        if (it.isNotBlank()) showBookmarks = false
                                    },
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
                                        .onFocusChanged { focusState -> isAddressFocused = focusState.isFocused; if (!focusState.isFocused) hasTypedInAddress = false; focusState ->
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
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.loadsImagesAutomatically = true
                        settings.textZoom = 100
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        val cleanUa = (settings.userAgentString ?: "Mozilla/5.0")
                            .replace("; wv)", ")")
                            .replace("Version/4.0 ", "")
                        settings.userAgentString = cleanUa
                        webViewUserAgent = cleanUa
                        val currentWebView = this
                        android.webkit.CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(currentWebView, true)
                        }
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onVideoFound(src: String?, posSec: Double, title: String?, poster: String?) {
                                val clean = src?.trim().orEmpty()
                                if (clean.startsWith("http://") || clean.startsWith("https://")) {
                                    post {
                                        detectedVideoUrl = clean
                                        detectedVideoPosMs = (posSec * 1000.0).toLong().coerceAtLeast(0L)
                                        if (!title.isNullOrBlank()) detectedVideoTitle = title.trim()
                                        if (!poster.isNullOrBlank()) detectedVideoPosterUrl = poster.trim()
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onVideoPlayRequested(src: String?, posSec: Double, title: String?, poster: String?) {
                                val raw = src?.trim().orEmpty()
                                post {
                                    val resolved = when {
                                        raw.startsWith("http://") || raw.startsWith("https://") -> raw
                                        raw.startsWith("blob:") -> detectedVideoUrl.orEmpty()
                                        else -> ""
                                    }
                                    if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                                        detectedVideoUrl = resolved
                                        val posMs = (posSec * 1000.0).toLong().coerceAtLeast(0L)
                                        detectedVideoPosMs = posMs
                                        if (!title.isNullOrBlank()) detectedVideoTitle = title.trim()
                                        if (!poster.isNullOrBlank()) detectedVideoPosterUrl = poster.trim()
                                        if (!userDismissedFloatingForPage && activeBuiltInVideoUrl == null) {
                                            evaluateJavascript(
                                                "window.__joolsLockPause=true;document.querySelectorAll('video').forEach(function(v){try{v.pause();}catch(e){}});",
                                                null
                                            )
                                            activeBuiltInVideoUrl = resolved
                                        }
                                    }
                                }
                            }
                        }, "JoolsVideoBridge")
                        clipToOutline = true
                        setBackgroundColor(barBg.toArgb())
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                            if (!url.isNullOrBlank()) {
                                onStartDownload(url, userAgent, contentDisposition, mimetype, contentLength)
                                Toast.makeText(context, "开始下载", Toast.LENGTH_SHORT).show()
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val reqUrl = request?.url?.toString().orEmpty()
                                if (currentAdBlockEnabled && request != null && !request.isForMainFrame) {
                                    if (adBlockManager.shouldBlock(reqUrl, currentCompiledAdRules)) {
                                        return AdBlockManager.emptyResponse()
                                    }
                                }
                                if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                    val pathLower = reqUrl.substringBefore('?').lowercase()
                                    if (pathLower.endsWith(".m3u8") || pathLower.endsWith(".mp4") || pathLower.endsWith(".webm")) {
                                        view?.post {
                                            if (detectedVideoUrl == null) {
                                                detectedVideoUrl = reqUrl
                                                detectedVideoPosMs = 0L
                                            }
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                val cleanUrl = url.orEmpty()
                                if (cleanUrl.isNotEmpty() && cleanUrl != lastVideoPageUrl) {
                                    lastVideoPageUrl = cleanUrl
                                    detectedVideoUrl = null
                                    detectedVideoPosMs = 0L
                                    detectedVideoTitle = ""
                                    detectedVideoPosterUrl = ""
                                    userDismissedFloatingForPage = false
                                    activeBuiltInVideoUrl = null
                                    if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                                        urlInput = cleanUrl
                                    }
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    view?.postDelayed({
                                        view.evaluateJavascript(VIDEO_DETECTOR_JS, null)
                                    }, 600)
                                }
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
                                if (!url.isNullOrEmpty()) lastVideoPageUrl = url
                                detectedVideoUrl = null
                                detectedVideoPosMs = 0L
                                detectedVideoTitle = ""
                                detectedVideoPosterUrl = ""
                                userDismissedFloatingForPage = false
                                activeBuiltInVideoUrl = null
                                if (!url.isNullOrEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    urlInput = url
                                    historyItems = historyManager.recordVisit(view?.title.orEmpty(), url)
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                if (currentAdBlockEnabled) {
                                    view?.evaluateJavascript(AdBlockManager.COSMETIC_JS, null)
                                }
                                view?.evaluateJavascript(VIDEO_DETECTOR_JS, null)
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                showLoadingOverlay = false
                                view?.evaluateJavascript(VIDEO_DETECTOR_JS, null)
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

            // Floating built-in video player trigger when a playable video is detected on page
            androidx.compose.animation.AnimatedVisibility(
                visible = detectedVideoUrl != null && !showBookmarks,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 14.dp)
                    .zIndex(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            val vUrl = detectedVideoUrl ?: return@clickable
                            runCatching {
                                webViewRef?.evaluateJavascript(
                                    "window.__joolsLockPause=true;document.querySelectorAll('video').forEach(function(v){try{v.pause();}catch(e){}});",
                                    null
                                )
                            }
                            activeBuiltInVideoUrl = vUrl
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = "内置播放器",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Live history keyword suggestions when typing in address bar
            val matchedHistory = remember(urlInput, hasTypedInAddress, isAddressFocused, historyItems) {
                if (isAddressFocused && hasTypedInAddress && urlInput.isNotBlank()) {
                    historyManager.search(urlInput)
                } else {
                    emptyList()
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = matchedHistory.isNotEmpty(),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(3f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = menuBg,
                    contentColor = barFg,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                    ) {
                        itemsIndexed(matchedHistory, key = { _, h -> h.id }) { _, hItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hasTypedInAddress = false
                                        navigateTo(hItem.url)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = barFg.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hItem.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = hItem.url,
                                        fontSize = 11.sp,
                                        color = barFg.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
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
                        Toast.makeText(context, "已设为主页", Toast.LENGTH_SHORT).show()
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
                                                        Toast.makeText(context, "无法打开该文件", Toast.LENGTH_SHORT).show()
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1 (4 columns: 下载管理, 屏蔽广告, 预留空位 x2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 下载管理
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showMenuSheet = false
                                showDownloadsDialog = true
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "下载管理",
                                        tint = if (activeDownloadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            if (activeDownloadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "下载管理",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 2. 屏蔽广告（点击开关，长按管理订阅与域名）
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
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
                                    runCatching {
                                        webViewRef?.clearCache(true)
                                        if (!next) {
                                            webViewRef?.evaluateJavascript(
                                                "document.getElementById('jools-adblock-style')?.remove();",
                                                null
                                            )
                                        }
                                        webViewRef?.reload()
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMenuSheet = false
                                    showAdBlockRulesDialog = true
                                }
                            )
                            .padding(vertical = 10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (adBlockEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "屏蔽广告",
                                    tint = if (adBlockEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (adBlockEnabled) "屏蔽广告·开" else "屏蔽广告·关",
                            fontSize = 12.sp,
                            color = if (adBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 3. 历史记录
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showMenuSheet = false
                                isHistoryMultiSelect = false
                                selectedHistoryIds = emptySet()
                                historyItems = historyManager.getHistory()
                                showHistoryDialog = true
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = "历史记录",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "历史记录",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                // Row 2 (4 columns reserved)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showHistoryDialog = false
                isHistoryMultiSelect = false
                selectedHistoryIds = emptySet()
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHistoryMultiSelect) "已选 ${selectedHistoryIds.size} 条" else "历史记录 (14天)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (isHistoryMultiSelect) {
                        TextButton(
                            enabled = selectedHistoryIds.isNotEmpty(),
                            onClick = {
                                historyItems = historyManager.deleteItems(selectedHistoryIds)
                                isHistoryMultiSelect = false
                                selectedHistoryIds = emptySet()
                            }
                        ) {
                            Text("删除所选", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (historyItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                historyItems = historyManager.clearAll()
                                Toast.makeText(context, "已清空历史记录", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("全部删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            text = {
                if (historyItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(historyItems, key = { _, item -> item.id }) { _, hItem ->
                            val isSelected = hItem.id in selectedHistoryIds
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isHistoryMultiSelect) {
                                                selectedHistoryIds = if (isSelected) {
                                                    selectedHistoryIds - hItem.id
                                                } else {
                                                    selectedHistoryIds + hItem.id
                                                }
                                            } else {
                                                showHistoryDialog = false
                                                navigateTo(hItem.url)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isHistoryMultiSelect = true
                                            selectedHistoryIds = selectedHistoryIds + hItem.id
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isHistoryMultiSelect) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedHistoryIds = if (checked) {
                                                    selectedHistoryIds + hItem.id
                                                } else {
                                                    selectedHistoryIds - hItem.id
                                                }
                                            }
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hItem.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                            .format(Date(hItem.visitedAtMs))
                                        Text(
                                            text = "$timeStr · ${hItem.url}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!isHistoryMultiSelect) {
                                        IconButton(
                                            onClick = {
                                                historyItems = historyManager.deleteItem(hItem.id)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "删除记录",
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
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
                        if (isHistoryMultiSelect) {
                            isHistoryMultiSelect = false
                            selectedHistoryIds = emptySet()
                        } else {
                            showHistoryDialog = false
                        }
                    }
                ) {
                    Text(if (isHistoryMultiSelect) "取消多选" else "关闭")
                }
            }
        )
    }

    if (showAdBlockRulesDialog) {
        AlertDialog(
            onDismissRequest = { showAdBlockRulesDialog = false },
            title = { Text("广告屏蔽订阅与规则", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TabRow(selectedTabIndex = adBlockDialogTab) {
                        Tab(
                            selected = adBlockDialogTab == 0,
                            onClick = { adBlockDialogTab = 0 },
                            text = { Text("规则订阅 (${adBlockSubscriptions.size})", fontSize = 13.sp) }
                        )
                        Tab(
                            selected = adBlockDialogTab == 1,
                            onClick = { adBlockDialogTab = 1 },
                            text = { Text("自定义域名 (${customDomains.size})", fontSize = 13.sp) }
                        )
                    }

                    if (adBlockDialogTab == 0) {
                        OutlinedTextField(
                            value = newSubNameInput,
                            onValueChange = { newSubNameInput = it },
                            placeholder = { Text("订阅名称（可选）", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newSubUrlInput,
                                onValueChange = { newSubUrlInput = it },
                                placeholder = { Text("订阅链接 https://...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    val urlStr = newSubUrlInput.trim()
                                    if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                        val updated = adBlockManager.addSubscription(newSubNameInput, urlStr)
                                        adBlockSubscriptions = updated
                                        newSubNameInput = ""
                                        newSubUrlInput = ""
                                        val added = updated.lastOrNull { it.url.equals(urlStr, ignoreCase = true) }
                                        if (added != null) {
                                            coroutineScope.launch {
                                                isRefreshingSubs = true
                                                adBlockManager.refreshSubscription(added.id)
                                                adBlockSubscriptions = adBlockManager.getSubscriptions()
                                                compiledAdRules = adBlockManager.compileActiveRules()
                                                isRefreshingSubs = false
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "请输入有效订阅链接", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("添加")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(adBlockSubscriptions, key = { _, s -> s.id }) { _, sub ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sub.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val timeText = if (sub.lastUpdatedMs > 0L) {
                                                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(sub.lastUpdatedMs))
                                            } else {
                                                "未更新(内置基础)"
                                            }
                                            Text(
                                                text = "${sub.ruleCount} 条 · $timeText",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isRefreshingSubs = true
                                                    val res = adBlockManager.refreshSubscription(sub.id)
                                                    adBlockSubscriptions = adBlockManager.getSubscriptions()
                                                    compiledAdRules = adBlockManager.compileActiveRules()
                                                    isRefreshingSubs = false
                                                    Toast.makeText(
                                                        context,
                                                        if (res.isSuccess) "更新成功" else "更新失败",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "更新订阅",
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        if (!sub.isBuiltIn) {
                                            IconButton(
                                                onClick = {
                                                    adBlockSubscriptions = adBlockManager.removeSubscription(sub.id)
                                                    compiledAdRules = adBlockManager.compileActiveRules()
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "删除订阅",
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = sub.enabled,
                                            onCheckedChange = { checked ->
                                                adBlockSubscriptions = adBlockManager.toggleSubscription(sub.id, checked)
                                                compiledAdRules = adBlockManager.compileActiveRules()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newDomainInput,
                                onValueChange = { newDomainInput = it },
                                placeholder = { Text("输入屏蔽域名，如 ads.example.com", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    val trimmed = newDomainInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        customDomains = adBlockManager.addCustomDomain(trimmed)
                                        compiledAdRules = adBlockManager.compileActiveRules()
                                        newDomainInput = ""
                                    }
                                }
                            ) {
                                Text("添加")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(customDomains, key = { idx, item -> "${idx}_$item" }) { _, domain ->
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
                                            text = domain,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                customDomains = adBlockManager.removeCustomDomain(domain)
                                                compiledAdRules = adBlockManager.compileActiveRules()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "删除域名",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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
                    enabled = !isRefreshingSubs,
                    onClick = {
                        coroutineScope.launch {
                            isRefreshingSubs = true
                            val ok = adBlockManager.refreshAllEnabled()
                            adBlockSubscriptions = adBlockManager.getSubscriptions()
                            compiledAdRules = adBlockManager.compileActiveRules()
                            isRefreshingSubs = false
                            Toast.makeText(
                                context,
                                if (ok > 0) "已更新 $ok 个订阅" else "更新失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text(if (isRefreshingSubs) "更新中..." else "全部更新")
                }
            }
        )
    }

    activeBuiltInVideoUrl?.let { vUrl ->
        BuiltInVideoPlayerOverlay(
            videoUrl = vUrl,
            pageUrl = urlInput,
            pageTitle = detectedVideoTitle.ifBlank { pageTitle },
            posterUrl = detectedVideoPosterUrl,
            userAgent = webViewUserAgent,
            initialPositionMs = detectedVideoPosMs,
            onDownloadVideo = { downloadUrl ->
                onStartDownload(downloadUrl, webViewUserAgent, null, "video/mp4", -1L)
                Toast.makeText(context, "开始下载", Toast.LENGTH_SHORT).show()
            },
            onClose = {
                activeBuiltInVideoUrl = null
                userDismissedFloatingForPage = true
                runCatching {
                    webViewRef?.evaluateJavascript("window.__joolsLockPause = false; window.__joolsAutoHijack = false;", null)
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


private const val VIDEO_DETECTOR_JS = """
(function() {
  function getVideoSrc(v) {
    var src = v.currentSrc || v.src || '';
    if (!src) {
      var s = v.querySelector('source');
      if (s) src = s.src || '';
    }
    return src;
  }
  function resolveUrl(raw) {
    if (!raw) return '';
    try { return new URL(raw, location.href).href; } catch (e) { return raw; }
  }
  function getVideoTitle(v) {
    var og = document.querySelector('meta[property="og:title"]');
    if (og && og.content) return og.content.trim();
    var tw = document.querySelector('meta[name="twitter:title"]');
    if (tw && tw.content) return tw.content.trim();
    if (v && v.title) return v.title.trim();
    return (document.title || '').trim();
  }
  function getVideoPoster(v) {
    if (v && v.poster) return resolveUrl(v.poster);
    var og = document.querySelector('meta[property="og:image"]');
    if (og && og.content) return resolveUrl(og.content.trim());
    var tw = document.querySelector('meta[name="twitter:image"]');
    if (tw && tw.content) return resolveUrl(tw.content.trim());
    var link = document.querySelector('link[rel="image_src"]');
    if (link && link.href) return resolveUrl(link.href);
    return '';
  }
  function pauseAllVideos() {
    try {
      document.querySelectorAll('video').forEach(function(v) { try { v.pause(); } catch(e) {} });
    } catch(e) {}
  }
  function scanVideos() {
    try {
      var vids = document.querySelectorAll('video');
      for (var i = 0; i < vids.length; i++) {
        var v = vids[i];
        var src = getVideoSrc(v);
        if (src && (src.indexOf('http://') === 0 || src.indexOf('https://') === 0)) {
          window.JoolsVideoBridge && window.JoolsVideoBridge.onVideoFound(src, v.currentTime || 0, getVideoTitle(v), getVideoPoster(v));
          return;
        }
      }
      var iframes = document.querySelectorAll('iframe');
      for (var j = 0; j < iframes.length; j++) {
        try {
          var ivs = iframes[j].contentDocument && iframes[j].contentDocument.querySelectorAll('video');
          if (ivs) {
            for (var k = 0; k < ivs.length; k++) {
              var iv = ivs[k];
              var isrc = getVideoSrc(iv);
              if (isrc && (isrc.indexOf('http://') === 0 || isrc.indexOf('https://') === 0)) {
                window.JoolsVideoBridge && window.JoolsVideoBridge.onVideoFound(isrc, iv.currentTime || 0, getVideoTitle(iv), getVideoPoster(iv));
                return;
              }
            }
          }
        } catch (e) {}
      }
    } catch (e) {}
  }
  function handlePlayEvent(e) {
    var v = e.target;
    if (!v || v.tagName !== 'VIDEO') return;
    if (window.__joolsLockPause) {
      try { v.pause(); } catch(err) {}
      return;
    }
    var src = getVideoSrc(v);
    if (src && (src.indexOf('http://') === 0 || src.indexOf('https://') === 0 || src.indexOf('blob:') === 0)) {
      if (window.__joolsAutoHijack !== false && window.JoolsVideoBridge) {
        try { v.pause(); } catch(err) {}
        window.JoolsVideoBridge.onVideoPlayRequested(src, v.currentTime || 0, getVideoTitle(v), getVideoPoster(v));
      } else if (window.JoolsVideoBridge && src.indexOf('http') === 0) {
        window.JoolsVideoBridge.onVideoFound(src, v.currentTime || 0, getVideoTitle(v), getVideoPoster(v));
      }
    }
  }
  if (!window.__joolsVideoHooked) {
    window.__joolsVideoHooked = true;
    window.__joolsAutoHijack = true;
    window.__joolsLockPause = false;
    var origPlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function() {
      try {
        if (this.tagName === 'VIDEO') {
          if (window.__joolsLockPause) {
            try { this.pause(); } catch(e) {}
            return Promise.resolve();
          }
          var src = getVideoSrc(this);
          if (src && (src.indexOf('http://') === 0 || src.indexOf('https://') === 0 || src.indexOf('blob:') === 0)) {
            if (window.__joolsAutoHijack !== false && window.JoolsVideoBridge) {
              try { this.pause(); } catch(e) {}
              window.JoolsVideoBridge.onVideoPlayRequested(src, this.currentTime || 0, getVideoTitle(this), getVideoPoster(this));
              return Promise.resolve();
            }
          }
        }
      } catch(e) {}
      return origPlay.apply(this, arguments);
    };
    document.addEventListener('play', handlePlayEvent, true);
    document.addEventListener('playing', handlePlayEvent, true);
    document.addEventListener('timeupdate', function(e) {
      if (window.__joolsLockPause && e.target && e.target.tagName === 'VIDEO') {
        try { e.target.pause(); } catch(err) {}
      }
    }, true);
    try {
      var obs = new MutationObserver(function() {
        if (window.__joolsLockPause) pauseAllVideos();
        scanVideos();
      });
      if (document.body) obs.observe(document.body, { childList: true, subtree: true });
    } catch (e) {}
  }
  scanVideos();
  setTimeout(scanVideos, 800);
  setTimeout(scanVideos, 2500);
})();
"""