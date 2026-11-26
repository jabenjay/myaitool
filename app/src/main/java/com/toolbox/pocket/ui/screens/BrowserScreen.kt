package com.toolbox.pocket.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidPress

private const val DEFAULT_HOME_URL = "https://cn.pornhub.com/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val focusManager = LocalFocusManager.current

    val barBg = if (visuals.isDark) Color(0xFF14161C) else Color(0xFFFFFFFF)
    val pillBg = if (visuals.isDark) Color(0xFF232732) else Color(0xFFEEF1F6)
    val barFg = if (visuals.isDark) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var urlInput by remember { mutableStateOf(DEFAULT_HOME_URL) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun navigateTo(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val target = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.bing.com/search?q=${android.net.Uri.encode(trimmed)}"
        }
        urlInput = target
        runCatching {
            webViewRef?.stopLoading()
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

    BackHandler(enabled = canGoBack) {
        runCatching { webViewRef?.goBack() }
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
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
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
                                modifier = Modifier.weight(1f),
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
                                    modifier = Modifier.fillMaxWidth()
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

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { runCatching { webViewRef?.reload() } },
                        modifier = Modifier
                            .size(38.dp)
                            .liquidPress()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = barFg,
                            modifier = Modifier.size(20.dp)
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

        // Crash-hardened WebView
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

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val scheme = request?.url?.scheme?.lowercase() ?: return true
                            // Only allow http/https inside WebView; ignore intent:// etc. so it never crashes
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

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            if (!url.isNullOrEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                urlInput = url
                            }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            // Prevent WebView renderer crash from taking down the entire app
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            if (newProgress >= 100) isLoading = false
                        }
                    }

                    loadUrl(DEFAULT_HOME_URL)
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

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
                    onClick = { navigateTo(DEFAULT_HOME_URL) },
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
}
