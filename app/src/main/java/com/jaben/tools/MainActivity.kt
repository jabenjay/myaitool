package com.jaben.tools

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import com.jaben.tools.service.VideoPipState
import com.jaben.tools.data.AppShortcutHelper
import com.jaben.tools.data.AppUpdateChecker
import com.jaben.tools.data.PendingNoticeItem
import com.jaben.tools.data.RemoteNoticeManager
import com.jaben.tools.ui.components.NoticeConfirmDialog
import com.jaben.tools.data.UpdateCheckResult
import com.jaben.tools.ui.components.UpdateBottomSheet
import com.jaben.tools.data.BookmarkManager
import com.jaben.tools.data.BrowserDownloadManager
import com.jaben.tools.tools.qr.QrRouter
import com.jaben.tools.data.GfwRuleManager
import com.jaben.tools.data.ProxyManager
import com.jaben.tools.data.ThemeMode
import com.jaben.tools.data.ThemePreferences
import com.jaben.tools.ui.screens.AboutScreen
import com.jaben.tools.ui.screens.Base64Screen
import com.jaben.tools.ui.screens.BrowserScreen
import com.jaben.tools.ui.screens.FileCompressorScreen
import com.jaben.tools.ui.screens.PushGitScreen
import com.jaben.tools.ui.screens.FormatFactoryScreen
import com.jaben.tools.ui.screens.StorageSettingsScreen
import com.jaben.tools.ui.screens.LayoutSettingsScreen
import com.jaben.tools.ui.screens.LogViewerScreen
import com.jaben.tools.ui.screens.ProxySettingsScreen
import com.jaben.tools.ui.screens.QrScannerScreen
import com.jaben.tools.ui.screens.QrToolScreen
import com.jaben.tools.ui.screens.SettingsScreen
import com.jaben.tools.ui.screens.ThemeSettingsScreen
import com.jaben.tools.ui.screens.ToolboxHomeScreen
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.PocketToolboxTheme
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jaben.tools.ui.theme.liquidPress
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {
    private val pendingShortcutState = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppShortcutHelper.extractRoute(intent)?.let { route ->
            pendingShortcutState.value = route
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themePreferences = ThemePreferences(applicationContext)
        val gfwRuleManager = GfwRuleManager(applicationContext)
        val proxyManager = ProxyManager(applicationContext, gfwRuleManager)
        val bookmarkManager = BookmarkManager(applicationContext)
        val downloadManager = BrowserDownloadManager(applicationContext, proxyManager)
        pendingShortcutState.value = AppShortcutHelper.extractRoute(intent)

        setContent {
            var pendingShortcut by pendingShortcutState
            val settings by themePreferences.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LaunchedEffect(VideoPipState.isFloatingPlayerActive, VideoPipState.isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        setPictureInPictureParams(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .setAutoEnterEnabled(VideoPipState.isFloatingPlayerActive && VideoPipState.isPlaying)
                                .build()
                        )
                    }
                }
            }

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            PocketToolboxTheme(
                themeMode = settings.themeMode,
                uiStyle = settings.uiStyle,
                m3ColorSource = settings.m3ColorSource,
                glassOpacity = settings.glassOpacity,
                wallpaperSeedColor = settings.wallpaperSeedColor,
                hasCustomBackground = settings.backgroundImagePath != null
            ) {
                PocketToolboxApp(
                    themePreferences = themePreferences,
                    proxyManager = proxyManager,
                    gfwRuleManager = gfwRuleManager,
                    bookmarkManager = bookmarkManager,
                    downloadManager = downloadManager,
                    pendingShortcutRoute = pendingShortcut,
                    onShortcutHandled = { pendingShortcut = null }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (VideoPipState.isFloatingPlayerActive && VideoPipState.isPlaying &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        VideoPipState.isInPipMode = isInPictureInPictureMode
    }
}

@Composable
fun PocketToolboxApp(
    themePreferences: ThemePreferences,
    proxyManager: ProxyManager,
    gfwRuleManager: GfwRuleManager,
    bookmarkManager: BookmarkManager,
    downloadManager: BrowserDownloadManager,
    pendingShortcutRoute: String? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val settings by themePreferences.settings.collectAsState()
    val proxyState by proxyManager.state.collectAsState()
    val gfwRuleState by gfwRuleManager.state.collectAsState()
    val bookmarks by bookmarkManager.bookmarks.collectAsState()
    val downloadItems by downloadManager.items.collectAsState()
    val browserHomeUrl by bookmarkManager.homeUrl.collectAsState()
    val visuals = LocalAppThemeVisuals.current
    val appScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var isHomeDragging by remember { mutableStateOf(false) }
    var startupUpdateInfo by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }
    var pendingNotices by remember { mutableStateOf<List<PendingNoticeItem>>(emptyList()) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == "main"

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        gfwRuleManager.updateRulesOnStartup()
    }

    LaunchedEffect(Unit) {
        pendingNotices = RemoteNoticeManager.checkPendingNotices(context)
        val res = AppUpdateChecker.checkLatestRelease(context, proxyManager.activeJavaProxy())
        if (res is UpdateCheckResult.UpdateAvailable) {
            startupUpdateInfo = res
        }
    }

    LaunchedEffect(settings.toolOrder) {
        AppShortcutHelper.updateDynamicShortcuts(context, settings.toolOrder)
    }

    LaunchedEffect(pendingShortcutRoute) {
        if (!pendingShortcutRoute.isNullOrBlank()) {
            navController.navigate(pendingShortcutRoute) {
                popUpTo("main") { inclusive = false }
                launchSingleTop = true
            }
            onShortcutHandled()
        }
    }

    val bgBitmap = remember(settings.backgroundImagePath, settings.backgroundVersion) {
        settings.backgroundImagePath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Background
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { mod ->
                        if (settings.backgroundBlurDp > 0f) mod.blur(settings.backgroundBlurDp.dp) else mod
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(
                            alpha = if (visuals.isDark) 0.30f else 0.18f
                        )
                    )
            )
        } else if (visuals.isLiquidGlass) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = if (visuals.isDark) {
                            listOf(ComposeColor(0xFF0A0D14), ComposeColor(0xFF101828), ComposeColor(0xFF090B10))
                        } else {
                            listOf(ComposeColor(0xFFD6E6FA), ComposeColor(0xFFE7EFFA), ComposeColor(0xFFDCE6F4))
                        }
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (visuals.isDark) ComposeColor(0x552E5CFF) else ComposeColor(0x557BB2FF),
                            ComposeColor.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.22f),
                        radius = size.width * 0.72f
                    ),
                    center = Offset(size.width * 0.2f, size.height * 0.22f),
                    radius = size.width * 0.72f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (visuals.isDark) ComposeColor(0x448B5CF6) else ComposeColor(0x44A5B4FC),
                            ComposeColor.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.75f),
                        radius = size.width * 0.75f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.75f),
                    radius = size.width * 0.75f
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        // Layer 2: Navigation & Screens
        val contentHost: @Composable (Modifier) -> Unit = { hostModifier ->
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = hostModifier,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.95f,
                        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    scaleOut(targetScale = 0.96f, animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = tween(220))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                    ) + scaleOut(targetScale = 0.95f, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
                }
            ) {
                composable("main") {
                    val dockExtraPadding = if (visuals.isLiquidGlass) 92.dp else 0.dp
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isHomeDragging,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> ToolboxHomeScreen(
                                homeColumns = settings.homeColumns,
                                toolOrder = settings.toolOrder,
                                bottomExtraPadding = dockExtraPadding,
                                onMoveTool = { from, to -> themePreferences.moveTool(from, to) },
                                onDragActiveChange = { active -> isHomeDragging = active },
                                onOpenScanner = { navController.navigate("tool/qr_scanner") },
                                onOpenTool = { toolId ->
                                    when (toolId) {
                                        "base64" -> navController.navigate("tool/base64")
                                        "browser" -> navController.navigate("tool/browser")
                                        "qr" -> navController.navigate("tool/qr")
                                        "format_factory" -> navController.navigate("tool/format_factory")
                                        "compressor" -> navController.navigate("tool/compressor")
                                        "pushgit" -> navController.navigate("tool/pushgit")
                                    }
                                }
                            )
                            else -> SettingsScreen(
                                settings = settings,
                                proxyState = proxyState,
                                bottomExtraPadding = dockExtraPadding,
                                onOpenThemeSettings = { navController.navigate("settings/theme") },
                                onOpenLayoutSettings = { navController.navigate("settings/layout") },
                                onOpenProxySettings = { navController.navigate("settings/proxy") },
                                onOpenStorageSettings = { navController.navigate("settings/storage") },
                                onOpenLogs = { navController.navigate("settings/logs") },
                                onOpenAbout = { navController.navigate("settings/about") },
                                onOpenUpdateUrl = { url -> navController.navigate("tool/browser?url=${Uri.encode(url)}") },
                                activeJavaProxy = { proxyManager.activeJavaProxy() }
                            )
                        }
                    }
                }
                composable("tool/base64") {
                    Base64Screen(
                        appSettings = settings,
                        onDefaultLineNumbersChange = { themePreferences.setDefaultLineNumbers(it) },
                        onDefaultSoftWrapChange = { themePreferences.setDefaultSoftWrap(it) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "tool/browser?url={url}",
                    arguments = listOf(
                        navArgument("url") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    ),
                    enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) { entry ->
                    val argUrl = entry.arguments?.getString("url").orEmpty()
                    val decodedUrl = if (argUrl.isNotEmpty()) Uri.decode(argUrl) else ""
                    BrowserScreen(
                        initialUrl = decodedUrl,
                        homeUrl = browserHomeUrl,
                        bookmarks = bookmarks,
                        proxyState = proxyState,
                        onToggleProxy = { proxyManager.setEnabled(it) },
                        onSelectProxy = { proxyManager.selectNode(it) },
                        onTestProxyNode = { proxyManager.testNode(it) },
                        onTestAllProxies = { proxyManager.testAllNodes() },
                        downloadItems = downloadItems,
                        onStartDownload = { url, ua, cd, mime, len -> downloadManager.startDownload(url, ua, cd, mime, len) },
                        onCancelDownload = { downloadManager.cancelDownload(it) },
                        onRemoveDownload = { downloadManager.removeRecord(it) },
                        onClearFinishedDownloads = { downloadManager.clearFinished() },
                        onSetHomeUrl = { bookmarkManager.setHomeUrl(it) },
                        onAddBookmark = { n, u -> bookmarkManager.addBookmark(n, u) },
                        onUpdateBookmark = { id, n, u -> bookmarkManager.updateBookmark(id, n, u) },
                        onDeleteBookmark = { id -> bookmarkManager.deleteBookmark(id) },
                        onMoveBookmark = { from, to -> bookmarkManager.moveBookmark(from, to) },
                        onExit = { navController.popBackStack() }
                    )
                }
                composable(
                    "tool/qr_scanner",
                    enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    QrScannerScreen(
                        onResult = { scanned ->
                            QrRouter.openScannedResult(
                                context = context,
                                scanned = scanned,
                                openInBuiltInBrowser = { target ->
                                    val encoded = Uri.encode(target)
                                    navController.navigate("tool/browser?url=$encoded") {
                                        popUpTo("tool/qr_scanner") { inclusive = true }
                                    }
                                }
                            )
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("tool/compressor") {
                    FileCompressorScreen(onBack = { navController.popBackStack() })
                }
                composable("tool/pushgit") {
                    PushGitScreen(onBack = { navController.popBackStack() })
                }
                composable("tool/format_factory") {
                    FormatFactoryScreen(onBack = { navController.popBackStack() })
                }
                composable("tool/qr") {
                    QrToolScreen(
                        onOpenCameraScan = { navController.navigate("tool/qr_scanner") },
                        onOpenInBrowser = { content ->
                            val encoded = Uri.encode(content)
                            navController.navigate("tool/browser?url=$encoded")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings/theme") {
                    ThemeSettingsScreen(
                        settings = settings,
                        onBack = { navController.popBackStack() },
                        onThemeModeChange = { themePreferences.setThemeMode(it) },
                        onUiStyleChange = { themePreferences.setUiStyle(it) },
                        onM3ColorSourceChange = { themePreferences.setM3ColorSource(it) },
                        onGlassOpacityChange = { themePreferences.setGlassOpacity(it) },
                        onBackgroundPicked = { uri -> themePreferences.saveBackgroundImage(uri) },
                        onClearBackground = { themePreferences.clearBackgroundImage() },
                        onBackgroundBlurChange = { blur -> themePreferences.setBackgroundBlur(blur) }
                    )
                }
                composable("settings/layout") {
                    LayoutSettingsScreen(
                        settings = settings,
                        onBack = { navController.popBackStack() },
                        onHomeColumnsChange = { themePreferences.setHomeColumns(it) }
                    )
                }
                composable("settings/proxy") {
                    ProxySettingsScreen(
                        proxyState = proxyState,
                        gfwRuleState = gfwRuleState,
                        onBack = { navController.popBackStack() },
                        onProxyToggle = { proxyManager.setEnabled(it) },
                        onSelectProxy = { proxyManager.selectNode(it) },
                        onAddProxy = { proxyManager.addNode(it) },
                        onDeleteProxy = { proxyManager.deleteNode(it) },
                        onTestNode = { proxyManager.testNode(it) },
                        onTestAllNodes = { proxyManager.testAllNodes() },
                        onRefreshGfwRules = { appScope.launch { gfwRuleManager.updateRulesOnStartup() } }
                    )
                }
                composable("settings/storage") {
                    StorageSettingsScreen(
                        settings = settings,
                        onBack = { navController.popBackStack() },
                        onSelectOutputRoot = { uri, label -> themePreferences.setOutputRoot(uri, label) },
                        onResetOutputRoot = { themePreferences.resetOutputRoot() }
                    )
                }
                composable("settings/about") {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onOpenRepoInBrowser = { url -> navController.navigate("tool/browser?url=${Uri.encode(url)}") }
                    )
                }
                composable("settings/logs") {
                    LogViewerScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        if (visuals.isLiquidGlass) {
            Box(modifier = Modifier.fillMaxSize()) {
                contentHost(Modifier.fillMaxSize())
                if (showBottomBar) {
                    LiquidGlassFloatingOvalDock(
                        selectedIndex = pagerState.currentPage,
                        pageOffsetFraction = pagerState.currentPageOffsetFraction,
                        onSelectPage = { page ->
                            appScope.launch {
                                pagerState.animateScrollToPage(
                                    page = page,
                                    animationSpec = spring(
                                        dampingRatio = 0.78f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = ComposeColor.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(
                            containerColor = if (bgBitmap != null) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ) {
                            NavigationBarItem(
                                selected = pagerState.currentPage == 0,
                                onClick = {
                                    appScope.launch { pagerState.animateScrollToPage(0) }
                                },
                                icon = { Icon(Icons.Rounded.Home, contentDescription = "主页") },
                                label = { Text("主页") }
                            )
                            NavigationBarItem(
                                selected = pagerState.currentPage == 1,
                                onClick = {
                                    appScope.launch { pagerState.animateScrollToPage(1) }
                                },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = "设置") },
                                label = { Text("设置") }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                contentHost(Modifier.padding(bottom = innerPadding.calculateBottomPadding()))
            }
        }

        val activeNotice = pendingNotices.firstOrNull()
        if (activeNotice != null) {
            NoticeConfirmDialog(
                item = activeNotice,
                onAcknowledge = {
                    RemoteNoticeManager.markAcknowledged(context, activeNotice)
                    pendingNotices = pendingNotices.drop(1)
                }
            )
        } else {
            startupUpdateInfo?.let { info ->
                UpdateBottomSheet(
                    updateInfo = info,
                    onDismiss = { startupUpdateInfo = null }
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassFloatingOvalDock(
    selectedIndex: Int,
    pageOffsetFraction: Float,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = visuals.dockContainerColor,
            contentColor = visuals.cardContentColor,
            modifier = Modifier
                .width(236.dp)
                .height(58.dp)
                .clip(RoundedCornerShape(50))
                .liquidGlassSurface(30.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val pillWidth = maxWidth / 2
                // Track live HorizontalPager swipe offset 1:1
                val fraction = (selectedIndex + pageOffsetFraction).coerceIn(0f, 1f)
                val indicatorOffset = pillWidth * fraction

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(pillWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(visuals.dockIndicatorColor)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassDockItem(
                        label = "主页",
                        icon = Icons.Rounded.Home,
                        selected = selectedIndex == 0,
                        onClick = { onSelectPage(0) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    LiquidGlassDockItem(
                        label = "设置",
                        icon = Icons.Rounded.Settings,
                        selected = selectedIndex == 1,
                        onClick = { onSelectPage(1) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassDockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 0.95f,
        animationSpec = spring(
            dampingRatio = 0.50f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dockItemScale"
    )
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .liquidPress(interactionSource, pressedScale = 0.92f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = tint
            )
        }
    }
}
