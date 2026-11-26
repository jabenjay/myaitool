package com.toolbox.pocket

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.ThemePreferences
import com.toolbox.pocket.ui.screens.Base64Screen
import com.toolbox.pocket.ui.screens.SettingsScreen
import com.toolbox.pocket.ui.screens.ToolboxHomeScreen
import com.toolbox.pocket.ui.theme.PocketToolboxTheme

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
                useDynamicColor = settings.useDynamicColor
            ) {
                PocketToolboxApp(
                    themePreferences = themePreferences
                )
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == "home" || currentRoute == "settings"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
            modifier = Modifier.padding(innerPadding)
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
                    onDynamicColorChange = { themePreferences.setDynamicColor(it) },
                    onDefaultLineNumbersChange = { themePreferences.setDefaultLineNumbers(it) },
                    onDefaultSoftWrapChange = { themePreferences.setDefaultSoftWrap(it) }
                )
            }
        }
    }
}
