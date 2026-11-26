package com.toolbox.pocket

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.ThemePreferences
import com.toolbox.pocket.ui.screens.Base64Screen
import com.toolbox.pocket.ui.screens.SettingsScreen
import com.toolbox.pocket.ui.screens.ToolboxHomeScreen
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.PocketToolboxTheme
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themePreferences = ThemePreferences(applicationContext)

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
                PocketToolboxApp(themePreferences = themePreferences)
            }
        }
    }
}

@Composable
fun PocketToolboxApp(
    themePreferences: ThemePreferences
) {
    val navController = rememberNavController()
    val settings by themePreferences.settings.collectAsState()
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
        // Layer 1: Custom background image OR Liquid Glass ambient gradient OR solid surface
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
            // Subtle contrast scrim so text stays readable over any photo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(
                            alpha = if (visuals.isDark) 0.42f else 0.30f
                        )
                    )
            )
        } else if (visuals.isLiquidGlass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = if (visuals.isDark) {
                                listOf(
                                    ComposeColor(0xFF0A1424),
                                    ComposeColor(0xFF10283E),
                                    ComposeColor(0xFF0E1B2A)
                                )
                            } else {
                                listOf(
                                    ComposeColor(0xFFE6F1FF),
                                    ComposeColor(0xFFF3F8FF),
                                    ComposeColor(0xFFE1EEFA)
                                )
                            }
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        // Layer 2: App navigation & screens
        Scaffold(
            containerColor = ComposeColor.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = if (visuals.isLiquidGlass || bgBitmap != null) {
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f)
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
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(240)
                    ) + fadeIn(animationSpec = tween(240))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(240)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) {
                composable("home") {
                    ToolboxHomeScreen(
                        onOpenTool = { toolId ->
                            if (toolId == "base64") {
                                navController.navigate("tool/base64")
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
                composable("settings") {
                    SettingsScreen(
                        settings = settings,
                        onThemeModeChange = { themePreferences.setThemeMode(it) },
                        onUiStyleChange = { themePreferences.setUiStyle(it) },
                        onDynamicColorChange = { themePreferences.setDynamicColor(it) },
                        onBackgroundPicked = { uri -> themePreferences.saveBackgroundImage(uri) },
                        onClearBackground = { themePreferences.clearBackgroundImage() },
                        onBackgroundBlurChange = { blur -> themePreferences.setBackgroundBlur(blur) },
                        onDefaultLineNumbersChange = { themePreferences.setDefaultLineNumbers(it) },
                        onDefaultSoftWrapChange = { themePreferences.setDefaultSoftWrap(it) }
                    )
                }
            }
        }
    }
}
