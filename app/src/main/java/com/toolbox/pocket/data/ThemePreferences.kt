package com.toolbox.pocket.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("跟随系统", "自动匹配手机当前的浅色或深色外观"),
    LIGHT("浅色模式", "始终使用明亮通透的浅色界面"),
    DARK("深色模式", "始终使用柔和护眼的深色界面")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val defaultShowLineNumbers: Boolean = true,
    val defaultSoftWrap: Boolean = true
)

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val themeMode = runCatching { ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        return AppSettings(
            themeMode = themeMode,
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            defaultShowLineNumbers = prefs.getBoolean(KEY_LINE_NUMBERS, true),
            defaultSoftWrap = prefs.getBoolean(KEY_SOFT_WRAP, true)
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = _settings.value.copy(useDynamicColor = enabled)
    }

    fun setDefaultLineNumbers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LINE_NUMBERS, enabled).apply()
        _settings.value = _settings.value.copy(defaultShowLineNumbers = enabled)
    }

    fun setDefaultSoftWrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOFT_WRAP, enabled).apply()
        _settings.value = _settings.value.copy(defaultSoftWrap = enabled)
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_LINE_NUMBERS = "default_line_numbers"
        const val KEY_SOFT_WRAP = "default_soft_wrap"
    }
}
