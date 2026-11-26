package com.toolbox.pocket.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.zIndex
import com.toolbox.pocket.data.AppLogManager
import com.toolbox.pocket.data.BookmarkItem
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidPress

private const val DEFAULT_HOME_URL = "https://cn.pornhub.com/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String = DEFAULT_HOME_URL,
    homeUrl: String = DEFAULT_HOME_URL,
    bookmarks: List<BookmarkItem>,
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
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var editingBookmark by remember { mutableStateOf<BookmarkItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            }
        }
    }

    BackHandler(enabled = showBookmarks || canGoBack) {
        if (showBookmarks) {
            showBookmarks = false
            focusManager.clearFocus()
        } else {
            runCatching { webViewRef?.goBack() }
        }
    }

    Column(
        modifier = modifier
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
            modifier = Modifier.fillMaxWidth()
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
                        settings.mediaPlaybackRequiresUserGesture = true
                    setBackgroundColor(barBg.toArgb())

                        webViewClient = object : WebViewClient() {
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
                                    imageVector = if (isCurrentHome) Icons.Rounded.Home else OutlinedHome,
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
