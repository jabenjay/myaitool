package com.toolbox.pocket

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toolbox.pocket.data.BookmarkManager
import com.toolbox.pocket.data.GfwRuleManager
import com.toolbox.pocket.data.ProxyManager
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.ThemePreferences
import com.toolbox.pocket.ui.screens.Base64Screen
import com.toolbox.pocket.ui.screens.BrowserScreen
import com.toolbox.pocket.ui.screens.LogViewerScreen
import com.toolbox.pocket.ui.screens.ProxySettingsScreen
import com.toolbox.pocket.ui.screens.SettingsScreen
import com.toolbox.pocket.ui.screens.ToolboxHomeScreen
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.PocketToolboxTheme
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themePreferences = ThemePreferences(applicationContext)
        val gfwRuleManager = GfwRuleManager(applicationContext)
        val proxyManager = ProxyManager(applicationContext, gfwRuleManager)
        val bookmarkManager = BookmarkManager(applicationContext)

        setContent {
            val settings by themePreferences.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
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
                    bookmarkManager = bookmarkManager
                )
            }
        }
    }
}

@Composable
fun PocketToolboxApp(
    themePreferences: ThemePreferences,
    proxyManager: ProxyManager,
    gfwRuleManager: GfwRuleManager,
    bookmarkManager: BookmarkManager
) {
    val navController = rememberNavController()
    val settings by themePreferences.settings.collectAsState()
    val proxyState by proxyManager.state.collectAsState()
    val gfwRuleState by gfwRuleManager.state.collectAsState()
    val bookmarks by bookmarkManager.bookmarks.collectAsState()

    LaunchedEffect(Unit) {
        gfwRuleManager.updateRulesOnStartup()
    }
    val visuals = LocalAppThemeVisuals.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == "home" || currentRoute == "settings"

    val bgBitmap = remember(settings.backgroundImagePath, settings.backgroundVersion) {
        settings.backgroundImagePath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Custom wallpaper OR clean iOS-style ambient backdrop OR solid M3 surface
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { mod ->
                        if (settings.backgroundBlurDp > 0f) {
                            mod.blur(settings.backgroundBlurDp.dp)
                        } else {
                            mod
                        }
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
            // Crisp iOS-like neutral sky/aurora backdrop so crystal white/dark glass stands out
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = if (visuals.isDark) {
                            listOf(
                                ComposeColor(0xFF0A0D14),
                                ComposeColor(0xFF101828),
                                ComposeColor(0xFF090B10)
                            )
                        } else {
                            listOf(
                                ComposeColor(0xFFD6E6FA),
                                ComposeColor(0xFFE7EFFA),
                                ComposeColor(0xFFDCE6F4)
                            )
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

        // Layer 2: Full-bleed NavHost + overlaid floating oval dock when in Liquid Glass mode
        if (visuals.isLiquidGlass) {
            Box(modifier = Modifier.fillMaxSize()) {
                val floatingDockPadding = if (showBottomBar) 92.dp else 0.dp
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
                        ) + scaleIn(
                            initialScale = 0.94f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    exitTransition = {
                        scaleOut(targetScale = 0.96f, animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                    },
                    popEnterTransition = {
                        scaleIn(
                            initialScale = 0.96f,
                            animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
                        ) + scaleOut(targetScale = 0.94f, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
                    }
                ) {
                    composable("home") {
                        ToolboxHomeScreen(
                            homeColumns = settings.homeColumns,
                            bottomExtraPadding = floatingDockPadding,
                            onOpenTool = { toolId ->
                                when (toolId) {
                                    "base64" -> navController.navigate("tool/base64")
                                    "browser" -> navController.navigate("tool/browser")
                                }
                            }
                        )
                    }
                    composable("tool/base64") {
                        Base64Screen(
                            appSettings = settings,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "tool/browser",
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(150)) }
                    ) {
                        BrowserScreen(
                            bookmarks = bookmarks,
                            onAddBookmark = { n, u -> bookmarkManager.addBookmark(n, u) },
                            onUpdateBookmark = { id, n, u -> bookmarkManager.updateBookmark(id, n, u) },
                            onDeleteBookmark = { id -> bookmarkManager.deleteBookmark(id) },
                            onMoveBookmark = { from, to -> bookmarkManager.moveBookmark(from, to) },
                            onExit = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            proxyState = proxyState,
                            bottomExtraPadding = floatingDockPadding,
                            onOpenProxySettings = { navController.navigate("settings/proxy") },
                            onOpenLogs = { navController.navigate("settings/logs") },
                            onThemeModeChange = { themePreferences.setThemeMode(it) },
                            onUiStyleChange = { themePreferences.setUiStyle(it) },
                            onM3ColorSourceChange = { themePreferences.setM3ColorSource(it) },
                            onGlassOpacityChange = { themePreferences.setGlassOpacity(it) },
                            onHomeColumnsChange = { themePreferences.setHomeColumns(it) },
                            onBackgroundPicked = { uri -> themePreferences.saveBackgroundImage(uri) },
                            onClearBackground = { themePreferences.clearBackgroundImage() },
                            onBackgroundBlurChange = { blur -> themePreferences.setBackgroundBlur(blur) },
                            onDefaultLineNumbersChange = { themePreferences.setDefaultLineNumbers(it) },
                            onDefaultSoftWrapChange = { themePreferences.setDefaultSoftWrap(it) }
                        )
                    }
                    composable("settings/logs") {
                        LogViewerScreen(onBack = { navController.popBackStack() })
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
                            onTestAllNodes = { proxyManager.testAllNodes() }
                        )
                    }
                }

                if (showBottomBar) {
                    LiquidGlassFloatingOvalDock(
                        currentRoute = currentRoute,
                        onSelectRoute = { route ->
                            if (route == "home") {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate("settings") {
                                    popUpTo("home")
                                    launchSingleTop = true
                                }
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
                                selected = currentRoute == "home",
                                onClick = {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(Icons.Rounded.Build, contentDescription = "工具箱") },
                                label = { Text("工具箱") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "settings",
                                onClick = {
                                    navController.navigate("settings") {
                                        popUpTo("home")
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = "设置") },
                                label = { Text("设置") }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(220)
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(160))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(220)
                        ) + fadeOut(animationSpec = tween(200))
                    }
                ) {
                    composable("home") {
                        ToolboxHomeScreen(
                            homeColumns = settings.homeColumns,
                            onOpenTool = { toolId ->
                                when (toolId) {
                                    "base64" -> navController.navigate("tool/base64")
                                    "browser" -> navController.navigate("tool/browser")
                                }
                            }
                        )
                    }
                    composable("tool/base64") {
                        Base64Screen(
                            appSettings = settings,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "tool/browser",
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(150)) }
                    ) {
                        BrowserScreen(
                            bookmarks = bookmarks,
                            onAddBookmark = { n, u -> bookmarkManager.addBookmark(n, u) },
                            onUpdateBookmark = { id, n, u -> bookmarkManager.updateBookmark(id, n, u) },
                            onDeleteBookmark = { id -> bookmarkManager.deleteBookmark(id) },
                            onMoveBookmark = { from, to -> bookmarkManager.moveBookmark(from, to) },
                            onExit = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            proxyState = proxyState,
                            onOpenProxySettings = { navController.navigate("settings/proxy") },
                            onOpenLogs = { navController.navigate("settings/logs") },
                            onThemeModeChange = { themePreferences.setThemeMode(it) },
                            onUiStyleChange = { themePreferences.setUiStyle(it) },
                            onM3ColorSourceChange = { themePreferences.setM3ColorSource(it) },
                            onGlassOpacityChange = { themePreferences.setGlassOpacity(it) },
                            onHomeColumnsChange = { themePreferences.setHomeColumns(it) },
                            onBackgroundPicked = { uri -> themePreferences.saveBackgroundImage(uri) },
                            onClearBackground = { themePreferences.clearBackgroundImage() },
                            onBackgroundBlurChange = { blur -> themePreferences.setBackgroundBlur(blur) },
                            onDefaultLineNumbersChange = { themePreferences.setDefaultLineNumbers(it) },
                            onDefaultSoftWrapChange = { themePreferences.setDefaultSoftWrap(it) }
                        )
                    }
                    composable("settings/logs") {
                        LogViewerScreen(onBack = { navController.popBackStack() })
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
                            onTestAllNodes = { proxyManager.testAllNodes() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassFloatingOvalDock(
    currentRoute: String?,
    onSelectRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val selectedIndex = if (currentRoute == "settings") 1 else 0

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
                val indicatorOffset by animateDpAsState(
                    targetValue = if (selectedIndex == 0) 0.dp else pillWidth,
                    animationSpec = spring(
                        dampingRatio = 0.56f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "liquidDockOffset"
                )

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
                        label = "工具箱",
                        icon = Icons.Rounded.Build,
                        selected = selectedIndex == 0,
                        onClick = { onSelectRoute("home") },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    LiquidGlassDockItem(
                        label = "设置",
                        icon = Icons.Rounded.Settings,
                        selected = selectedIndex == 1,
                        onClick = { onSelectRoute("settings") },
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
