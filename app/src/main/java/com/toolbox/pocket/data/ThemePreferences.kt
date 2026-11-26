package com.toolbox.pocket.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

enum class UiStyle(val label: String) {
    MATERIAL("Material 3"),
    LIQUID_GLASS("液态玻璃")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val uiStyle: UiStyle = UiStyle.MATERIAL,
    val useDynamicColor: Boolean = true,
    val defaultShowLineNumbers: Boolean = true,
    val defaultSoftWrap: Boolean = true,
    val backgroundImagePath: String? = null,
    val backgroundBlurDp: Float = 12f,
    val backgroundVersion: Int = 0
)

class ThemePreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
    private val bgFile = File(context.filesDir, "custom_theme_bg.jpg")

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val themeMode = runCatching { ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)

        val styleName = prefs.getString(KEY_UI_STYLE, UiStyle.MATERIAL.name)
        val uiStyle = runCatching { UiStyle.valueOf(styleName ?: UiStyle.MATERIAL.name) }
            .getOrDefault(UiStyle.MATERIAL)

        val bgPath = if (bgFile.exists() && prefs.getBoolean(KEY_HAS_BG, false)) {
            bgFile.absolutePath
        } else {
            null
        }

        return AppSettings(
            themeMode = themeMode,
            uiStyle = uiStyle,
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            defaultShowLineNumbers = prefs.getBoolean(KEY_LINE_NUMBERS, true),
            defaultSoftWrap = prefs.getBoolean(KEY_SOFT_WRAP, true),
            backgroundImagePath = bgPath,
            backgroundBlurDp = prefs.getFloat(KEY_BG_BLUR, 12f),
            backgroundVersion = prefs.getInt(KEY_BG_VERSION, 0)
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setUiStyle(style: UiStyle) {
        prefs.edit().putString(KEY_UI_STYLE, style.name).apply()
        _settings.value = _settings.value.copy(uiStyle = style)
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

    fun setBackgroundBlur(blurDp: Float) {
        val clamped = blurDp.coerceIn(0f, 30f)
        prefs.edit().putFloat(KEY_BG_BLUR, clamped).apply()
        _settings.value = _settings.value.copy(backgroundBlurDp = clamped)
    }

    fun saveBackgroundImage(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val bytes = input.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

                var sampleSize = 1
                val maxDim = 1600
                while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    ?: return false

                FileOutputStream(bgFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                bitmap.recycle()
            } ?: return false

            val nextVersion = _settings.value.backgroundVersion + 1
            prefs.edit()
                .putBoolean(KEY_HAS_BG, true)
                .putInt(KEY_BG_VERSION, nextVersion)
                .apply()

            _settings.value = _settings.value.copy(
                backgroundImagePath = bgFile.absolutePath,
                backgroundVersion = nextVersion
            )
            true
        }.getOrDefault(false)
    }

    fun clearBackgroundImage() {
        if (bgFile.exists()) {
            bgFile.delete()
        }
        val nextVersion = _settings.value.backgroundVersion + 1
        prefs.edit()
            .putBoolean(KEY_HAS_BG, false)
            .putInt(KEY_BG_VERSION, nextVersion)
            .apply()
        _settings.value = _settings.value.copy(
            backgroundImagePath = null,
            backgroundVersion = nextVersion
        )
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_UI_STYLE = "ui_style"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_LINE_NUMBERS = "default_line_numbers"
        const val KEY_SOFT_WRAP = "default_soft_wrap"
        const val KEY_HAS_BG = "has_custom_bg"
        const val KEY_BG_BLUR = "custom_bg_blur"
        const val KEY_BG_VERSION = "custom_bg_version"
    }
}
