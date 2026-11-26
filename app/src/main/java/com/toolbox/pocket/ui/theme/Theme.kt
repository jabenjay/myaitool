package com.toolbox.pocket.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.pocket.data.M3ColorSource
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.UiStyle

data class AppThemeVisuals(
    val isDark: Boolean = false,
    val isLiquidGlass: Boolean = false,
    val glassOpacity: Float = 0.55f,
    val hasCustomBackground: Boolean = false,
    val cardContainerColor: Color = Color.Unspecified,
    val cardContentColor: Color = Color.Unspecified,
    val innerFieldColor: Color = Color.Unspecified,
    val dockContainerColor: Color = Color.Unspecified,
    val dockIndicatorColor: Color = Color.Unspecified
)

val LocalAppThemeVisuals = staticCompositionLocalOf { AppThemeVisuals() }

fun Modifier.liquidPress(
    interactionSource: MutableInteractionSource? = null,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val visuals = LocalAppThemeVisuals.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = if (visuals.isLiquidGlass) 0.50f else 0.72f,
            stiffness = if (visuals.isLiquidGlass) Spring.StiffnessMediumLow else Spring.StiffnessMedium
        ),
        label = "liquidPressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.liquidGlassSurface(
    cornerRadius: Dp = 26.dp
): Modifier = composed {
    val visuals = LocalAppThemeVisuals.current
    if (!visuals.isLiquidGlass) return@composed this

    val shape = RoundedCornerShape(cornerRadius)
    val rimBrush = if (visuals.isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.14f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.45f)
            )
        )
    }

    this
        .shadow(
            elevation = if (visuals.isDark) 10.dp else 12.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (visuals.isDark) 0.35f else 0.10f),
            spotColor = Color.Black.copy(alpha = if (visuals.isDark) 0.40f else 0.12f)
        )
        .drawWithContent {
            drawContent()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (visuals.isDark) 0.12f else 0.32f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.45f
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            )
            drawRoundRect(
                brush = rimBrush,
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
}

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF395BA9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF001849),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    surface = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E2E9)
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB2C5FF),
    onPrimary = Color(0xFF002B74),
    primaryContainer = Color(0xFF1C438F),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF293042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    surface = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF33343A)
)

private val IosGlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x26007AFF),
    onPrimaryContainer = Color(0xFF0051B3),
    secondary = Color(0xFF5A5A60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0x22000000),
    onSecondaryContainer = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    surfaceContainerLowest = Color(0xB8FFFFFF),
    surfaceContainerLow = Color(0x8CFFFFFF),
    surfaceContainer = Color(0xB3FFFFFF),
    surfaceContainerHigh = Color(0xCCFFFFFF),
    surfaceContainerHighest = Color(0xE0FFFFFF),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF48484E),
    outline = Color(0x33000000),
    outlineVariant = Color(0x22000000)
)

private val IosGlassDarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x380A84FF),
    onPrimaryContainer = Color(0xFF70B8FF),
    secondary = Color(0xFFAEAEB2),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0x2EFFFFFF),
    onSecondaryContainer = Color(0xFFFFFFFF),
    surface = Color(0xFF0B0B0E),
    surfaceContainerLowest = Color(0x80141418),
    surfaceContainerLow = Color(0x8C1C1C22),
    surfaceContainer = Color(0xA61C1C22),
    surfaceContainerHigh = Color(0xBF26262E),
    surfaceContainerHighest = Color(0xD92E2E38),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFFC2C2CA),
    outline = Color(0x38FFFFFF),
    outlineVariant = Color(0x24FFFFFF)
)

private fun buildWallpaperColorScheme(seedColorInt: Int, darkTheme: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seedColorInt, hsv)
    val hue = hsv[0]
    val sat = hsv[1].coerceIn(0.35f, 0.80f)

    fun hsvColor(h: Float, s: Float, v: Float): Color {
        return Color(AndroidColor.HSVToColor(floatArrayOf((h % 360f + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = hsvColor(hue, sat * 0.72f, 0.95f),
            onPrimary = hsvColor(hue, sat, 0.22f),
            primaryContainer = hsvColor(hue, sat * 0.85f, 0.36f),
            onPrimaryContainer = hsvColor(hue, sat * 0.35f, 0.98f),
            secondary = hsvColor(hue + 20f, sat * 0.45f, 0.82f),
            onSecondary = hsvColor(hue + 20f, sat * 0.55f, 0.20f),
            secondaryContainer = hsvColor(hue + 15f, sat * 0.50f, 0.32f),
            onSecondaryContainer = hsvColor(hue + 15f, sat * 0.25f, 0.94f),
            surface = hsvColor(hue, 0.14f, 0.08f),
            surfaceContainerLowest = hsvColor(hue, 0.16f, 0.06f),
            surfaceContainerLow = hsvColor(hue, 0.14f, 0.12f),
            surfaceContainer = hsvColor(hue, 0.14f, 0.15f),
            surfaceContainerHigh = hsvColor(hue, 0.14f, 0.20f),
            surfaceContainerHighest = hsvColor(hue, 0.14f, 0.25f),
            onSurface = Color(0xFFF5F5F7),
            onSurfaceVariant = Color(0xFFC5C7D0)
        )
    } else {
        lightColorScheme(
            primary = hsvColor(hue, sat, 0.65f),
            onPrimary = Color.White,
            primaryContainer = hsvColor(hue, sat * 0.30f, 0.96f),
            onPrimaryContainer = hsvColor(hue, sat, 0.28f),
            secondary = hsvColor(hue + 20f, sat * 0.45f, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = hsvColor(hue + 15f, sat * 0.22f, 0.93f),
            onSecondaryContainer = hsvColor(hue + 15f, sat * 0.55f, 0.20f),
            surface = hsvColor(hue, 0.06f, 0.98f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsvColor(hue, 0.08f, 0.95f),
            surfaceContainer = hsvColor(hue, 0.10f, 0.93f),
            surfaceContainerHigh = hsvColor(hue, 0.10f, 0.90f),
            surfaceContainerHighest = hsvColor(hue, 0.10f, 0.87f),
            onSurface = Color(0xFF1A1B20),
            onSurfaceVariant = Color(0xFF494B54)
        )
    }
}

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val ExpressiveTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val anim = tween<Color>(durationMillis = 280)
    val primary by animateColorAsState(target.primary, anim, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, anim, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, anim, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, anim, label = "onPrimaryContainer")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, anim, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, anim, label = "onSecondaryContainer")
    val surface by animateColorAsState(target.surface, anim, label = "surface")
    val surfaceLow by animateColorAsState(target.surfaceContainerLow, anim, label = "surfaceLow")
    val surfaceLowest by animateColorAsState(target.surfaceContainerLowest, anim, label = "surfaceLowest")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, anim, label = "surfaceContainer")
    val onSurface by animateColorAsState(target.onSurface, anim, label = "onSurface")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, anim, label = "onSurfaceVariant")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        surface = surface,
        surfaceContainerLow = surfaceLow,
        surfaceContainerLowest = surfaceLowest,
        surfaceContainer = surfaceContainer,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant
    )
}

@Composable
fun PocketToolboxTheme(
    themeMode: ThemeMode,
    uiStyle: UiStyle,
    m3ColorSource: M3ColorSource,
    glassOpacity: Float,
    wallpaperSeedColor: Int?,
    hasCustomBackground: Boolean,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val isLiquidGlass = uiStyle == UiStyle.LIQUID_GLASS
    val context = LocalContext.current

    val rawColorScheme = when {
        isLiquidGlass -> if (darkTheme) IosGlassDarkColorScheme else IosGlassLightColorScheme
        m3ColorSource == M3ColorSource.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        m3ColorSource == M3ColorSource.WALLPAPER && wallpaperSeedColor != null -> {
            buildWallpaperColorScheme(wallpaperSeedColor, darkTheme)
        }
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    val colorScheme = animatedColorScheme(rawColorScheme)

    val cardContainerColor = when {
        isLiquidGlass && darkTheme -> Color(0xFF1C1C22).copy(alpha = glassOpacity)
        isLiquidGlass && !darkTheme -> Color.White.copy(alpha = glassOpacity)
        hasCustomBackground -> colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
        else -> colorScheme.surfaceContainerLow
    }

    // Automatically derive high-contrast text color from the card background luminance
    val cardContentColor = if (cardContainerColor.luminance() < 0.45f || darkTheme) {
        if (!darkTheme && cardContainerColor.luminance() > 0.5f) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)
    } else {
        Color(0xFF1C1C1E)
    }

    val innerFieldColor = when {
        isLiquidGlass && darkTheme -> Color(0xFF101014).copy(alpha = (glassOpacity + 0.12f).coerceAtMost(0.92f))
        isLiquidGlass && !darkTheme -> Color.White.copy(alpha = (glassOpacity + 0.18f).coerceAtMost(0.95f))
        hasCustomBackground -> colorScheme.surfaceContainerLowest.copy(alpha = 0.90f)
        else -> colorScheme.surfaceContainerLowest
    }

    val dockContainerColor = when {
        isLiquidGlass && darkTheme -> Color(0xFF1E1E24).copy(alpha = (glassOpacity + 0.14f).coerceAtMost(0.92f))
        isLiquidGlass && !darkTheme -> Color.White.copy(alpha = (glassOpacity + 0.18f).coerceAtMost(0.95f))
        else -> colorScheme.surfaceContainer
    }

    val dockIndicatorColor = when {
        isLiquidGlass && darkTheme -> Color.White.copy(alpha = 0.18f)
        isLiquidGlass && !darkTheme -> Color.Black.copy(alpha = 0.08f)
        else -> colorScheme.secondaryContainer
    }

    val visuals = AppThemeVisuals(
        isDark = darkTheme,
        isLiquidGlass = isLiquidGlass,
        glassOpacity = glassOpacity,
        hasCustomBackground = hasCustomBackground,
        cardContainerColor = cardContainerColor,
        cardContentColor = cardContentColor,
        innerFieldColor = innerFieldColor,
        dockContainerColor = dockContainerColor,
        dockIndicatorColor = dockIndicatorColor
    )

    CompositionLocalProvider(LocalAppThemeVisuals provides visuals) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ExpressiveTypography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}
