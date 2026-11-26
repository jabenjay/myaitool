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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toolbox.pocket.data.ProxyManager
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.ThemePreferences
import com.toolbox.pocket.ui.screens.Base64Screen
import com.toolbox.pocket.ui.screens.BrowserScreen
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
        val proxyManager = ProxyManager(applicationContext)

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
                useDynamicColor = settings.useDynamicColor,
                hasCustomBackground = settings.backgroundImagePath != null
            ) {
                PocketToolboxApp(
                    themePreferences = themePreferences,
                    proxyManager = proxyManager
                )
            }
        }
    }
}

@Composable
fun PocketToolboxApp(
    themePreferences: ThemePreferences,
    proxyManager: ProxyManager
) {
    val navController = rememberNavController()
    val settings by themePreferences.settings.collectAsState()
    val proxyState by proxyManager.state.collectAsState()
    val visuals = LocalAppThemeVisuals.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == "home" || currentRoute == "settings"

    val bgBitmap = remember(settings.backgroundImagePath, settings.backgroundVersion) {
        settings.backgroundImagePath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = Modifier.fillMaxSize()) {
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
                            alpha = if (visuals.isDark) 0.36f else 0.22f
                        )
                    )
            )
        } else if (visuals.isLiquidGlass) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = if (visuals.isDark) {
                            listOf(
                                ComposeColor(0xFF040B18),
                                ComposeColor(0xFF091B36),
                                ComposeColor(0xFF050F22)
                            )
                        } else {
                            listOf(
                                ComposeColor(0xFF9EC9FF),
                                ComposeColor(0xFFCDE2FF),
                                ComposeColor(0xFFB5D6FF)
                            )
                        }
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (visuals.isDark) 0.55f else 0.45f),
                            ComposeColor.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.18f),
                        radius = size.width * 0.78f
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.18f),
                    radius = size.width * 0.78f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = if (visuals.isDark) 0.45f else 0.38f),
                            ComposeColor.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.55f),
                        radius = size.width * 0.82f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.55f),
                    radius = size.width * 0.82f
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Scaffold(
            containerColor = ComposeColor.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    if (visuals.isLiquidGlass) {
                        LiquidGlassFloatingDock(
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
                            }
                        )
                    } else {
                        NavigationBar(
                            containerColor = if (bgBitmap != null) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding()
                ),
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
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(180)
                    ) + fadeOut(animationSpec = tween(180))
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
                    ) + scaleOut(
                        targetScale = 0.94f,
                        animationSpec = tween(200)
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                composable("home") {
                    ToolboxHomeScreen(
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
                composable("tool/browser") {
                    BrowserScreen(
                        onExit = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        settings = settings,
                        proxyState = proxyState,
                        onThemeModeChange = { themePreferences.setThemeMode(it) },
                        onUiStyleChange = { themePreferences.setUiStyle(it) },
                        onDynamicColorChange = { themePreferences.setDynamicColor(it) },
                        onBackgroundPicked = { uri -> themePreferences.saveBackgroundImage(uri) },
                        onClearBackground = { themePreferences.clearBackgroundImage() },
                        onBackgroundBlurChange = { blur -> themePreferences.setBackgroundBlur(blur) },
                        onDefaultLineNumbersChange = { themePreferences.setDefaultLineNumbers(it) },
                        onDefaultSoftWrapChange = { themePreferences.setDefaultSoftWrap(it) },
                        onProxyToggle = { proxyManager.setEnabled(it) },
                        onSelectProxy = { proxyManager.selectNode(it) },
                        onAddProxy = { proxyManager.addNode(it) },
                        onDeleteProxy = { proxyManager.deleteNode(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassFloatingDock(
    currentRoute: String?,
    onSelectRoute: (String) -> Unit
) {
    val visuals = LocalAppThemeVisuals.current
    val selectedIndex = if (currentRoute == "settings") 1 else 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 36.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = visuals.dockContainerColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    spotColor = ComposeColor.Black.copy(alpha = 0.22f)
                )
                .clip(RoundedCornerShape(36.dp))
                .liquidGlassSurface(36.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                val pillWidth = maxWidth / 2
                // Viscous liquid-spring sliding pill indicator
                val indicatorOffset by animateDpAsState(
                    targetValue = if (selectedIndex == 0) 0.dp else pillWidth,
                    animationSpec = spring(
                        dampingRatio = 0.58f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "liquidDockOffset"
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(pillWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(30.dp))
                        .background(visuals.dockIndicatorColor)
                        .liquidGlassSurface(30.dp)
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
            dampingRatio = 0.52f,
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
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = tint
            )
        }
    }
}
