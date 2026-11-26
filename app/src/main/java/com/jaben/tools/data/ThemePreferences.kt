package com.jaben.tools.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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

enum class M3ColorSource(val label: String) {
    DEFAULT("默认"),
    SYSTEM("系统取色"),
    WALLPAPER("壁纸取色")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val uiStyle: UiStyle = UiStyle.MATERIAL,
    val m3ColorSource: M3ColorSource = M3ColorSource.SYSTEM,
    val glassOpacity: Float = 0.55f,
    val defaultShowLineNumbers: Boolean = true,
    val defaultSoftWrap: Boolean = true,
    val homeColumns: Int = 1,
    val toolOrder: List<String> = listOf("browser", "base64", "qr", "format_factory", "compressor"),
    val backgroundImagePath: String? = null,
    val backgroundBlurDp: Float = 12f,
    val backgroundVersion: Int = 0,
    val wallpaperSeedColor: Int? = null,
    val outputRootUri: String? = null,
    val outputRootLabel: String = "Download/Jools"
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

        val colorSrcName = prefs.getString(KEY_M3_COLOR_SOURCE, M3ColorSource.SYSTEM.name)
        val m3ColorSource = runCatching { M3ColorSource.valueOf(colorSrcName ?: M3ColorSource.SYSTEM.name) }
            .getOrDefault(M3ColorSource.SYSTEM)

        val bgPath = if (bgFile.exists() && prefs.getBoolean(KEY_HAS_BG, false)) {
            bgFile.absolutePath
        } else {
            null
        }

        val savedSeed = if (prefs.contains(KEY_WALLPAPER_SEED)) {
            prefs.getInt(KEY_WALLPAPER_SEED, 0)
        } else if (bgPath != null) {
            extractDominantSeedFromFile(bgPath)?.also {
                prefs.edit().putInt(KEY_WALLPAPER_SEED, it).apply()
            }
        } else {
            null
        }

        return AppSettings(
            themeMode = themeMode,
            uiStyle = uiStyle,
            m3ColorSource = m3ColorSource,
            glassOpacity = prefs.getFloat(KEY_GLASS_OPACITY, 0.55f).coerceIn(0.15f, 0.90f),
            defaultShowLineNumbers = prefs.getBoolean(KEY_LINE_NUMBERS, true),
            defaultSoftWrap = prefs.getBoolean(KEY_SOFT_WRAP, true),
            homeColumns = prefs.getInt(KEY_HOME_COLUMNS, 1).coerceIn(1, 4),
            toolOrder = (prefs.getString(KEY_TOOL_ORDER, "browser,base64,qr,format_factory,compressor") ?: "browser,base64,qr,format_factory,compressor")
                .split(",")
                .filter { it.isNotBlank() }
                .ifEmpty { listOf("browser", "base64", "qr", "format_factory", "compressor") },
            backgroundImagePath = bgPath,
            backgroundBlurDp = prefs.getFloat(KEY_BG_BLUR, 12f),
            backgroundVersion = prefs.getInt(KEY_BG_VERSION, 0),
            wallpaperSeedColor = savedSeed,
            outputRootUri = prefs.getString(KEY_OUTPUT_ROOT_URI, null),
            outputRootLabel = prefs.getString(KEY_OUTPUT_ROOT_LABEL, "Download/Jools") ?: "Download/Jools"
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

    fun setM3ColorSource(source: M3ColorSource) {
        prefs.edit().putString(KEY_M3_COLOR_SOURCE, source.name).apply()
        _settings.value = _settings.value.copy(m3ColorSource = source)
    }

    fun setGlassOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.15f, 0.90f)
        prefs.edit().putFloat(KEY_GLASS_OPACITY, clamped).apply()
        _settings.value = _settings.value.copy(glassOpacity = clamped)
    }

    fun setDefaultLineNumbers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LINE_NUMBERS, enabled).apply()
        _settings.value = _settings.value.copy(defaultShowLineNumbers = enabled)
    }

    fun setDefaultSoftWrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOFT_WRAP, enabled).apply()
        _settings.value = _settings.value.copy(defaultSoftWrap = enabled)
    }

    fun moveTool(fromIndex: Int, toIndex: Int) {
        val current = _settings.value.toolOrder.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        prefs.edit().putString(KEY_TOOL_ORDER, current.joinToString(",")).apply()
        _settings.value = _settings.value.copy(toolOrder = current)
    }

    fun setHomeColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 4)
        prefs.edit().putInt(KEY_HOME_COLUMNS, clamped).apply()
        _settings.value = _settings.value.copy(homeColumns = clamped)
    }

    fun setBackgroundBlur(blurDp: Float) {
        val clamped = blurDp.coerceIn(0f, 30f)
        prefs.edit().putFloat(KEY_BG_BLUR, clamped).apply()
        _settings.value = _settings.value.copy(backgroundBlurDp = clamped)
    }

    fun saveBackgroundImage(uri: Uri): Boolean {
        return runCatching {
            var extractedSeed: Int? = null
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

                extractedSeed = extractDominantSeed(bitmap)

                FileOutputStream(bgFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                bitmap.recycle()
            } ?: return false

            val nextVersion = _settings.value.backgroundVersion + 1
            val editor = prefs.edit()
                .putBoolean(KEY_HAS_BG, true)
                .putInt(KEY_BG_VERSION, nextVersion)
            if (extractedSeed != null) {
                editor.putInt(KEY_WALLPAPER_SEED, extractedSeed!!)
            }
            editor.apply()

            _settings.value = _settings.value.copy(
                backgroundImagePath = bgFile.absolutePath,
                backgroundVersion = nextVersion,
                wallpaperSeedColor = extractedSeed
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
            .remove(KEY_WALLPAPER_SEED)
            .apply()
        _settings.value = _settings.value.copy(
            backgroundImagePath = null,
            backgroundVersion = nextVersion,
            wallpaperSeedColor = null
        )
    }

    private fun extractDominantSeedFromFile(path: String): Int? {
        return runCatching {
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            extractDominantSeed(bmp).also { bmp.recycle() }
        }.getOrNull()
    }

    private fun extractDominantSeed(bitmap: Bitmap): Int {
        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        val hsv = FloatArray(3)
        var bestScore = -1f
        var bestColor = Color.rgb(57, 91, 169)

        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                val pixel = scaled.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                // Prefer vibrant mid-brightness colors
                if (value in 0.20f..0.95f) {
                    val score = sat * 2f + (1f - kotlin.math.abs(value - 0.55f))
                    if (score > bestScore) {
                        bestScore = score
                        bestColor = pixel
                    }
                }
            }
        }
        if (scaled != bitmap) scaled.recycle()
        return bestColor
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_UI_STYLE = "ui_style"
        const val KEY_M3_COLOR_SOURCE = "m3_color_source"
        const val KEY_GLASS_OPACITY = "glass_opacity"
        const val KEY_LINE_NUMBERS = "default_line_numbers"
        const val KEY_SOFT_WRAP = "default_soft_wrap"
        const val KEY_HOME_COLUMNS = "home_columns"
        const val KEY_TOOL_ORDER = "tool_order"
        const val KEY_HAS_BG = "has_custom_bg"
        const val KEY_BG_BLUR = "custom_bg_blur"
        const val KEY_BG_VERSION = "custom_bg_version"
        const val KEY_WALLPAPER_SEED = "wallpaper_seed_color"
    }
}
