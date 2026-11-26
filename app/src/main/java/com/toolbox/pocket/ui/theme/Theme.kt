package com.toolbox.pocket.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.pocket.data.ThemeMode
import com.toolbox.pocket.data.UiStyle

data class AppThemeVisuals(
    val isDark: Boolean = false,
    val isLiquidGlass: Boolean = false,
    val hasCustomBackground: Boolean = false,
    val cardBorder: BorderStroke? = null,
    val cardContainerColor: Color = Color.Unspecified,
    val innerFieldColor: Color = Color.Unspecified,
    val dockContainerColor: Color = Color.Unspecified,
    val dockIndicatorColor: Color = Color.Unspecified
)

val LocalAppThemeVisuals = staticCompositionLocalOf { AppThemeVisuals() }

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

private val LiquidGlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xB3D0E7FF),
    onPrimaryContainer = Color(0xFF002A5C),
    secondary = Color(0xFF475C75),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0x99FFFFFF),
    onSecondaryContainer = Color(0xFF0A223D),
    surface = Color(0xFFCDE2FF),
    surfaceContainerLowest = Color(0x9EFFFFFF),
    surfaceContainerLow = Color(0x7AFFFFFF),
    surfaceContainer = Color(0x99F4F9FF),
    surfaceContainerHigh = Color(0xB3EAF2FF),
    surfaceContainerHighest = Color(0xCCE0ECFF)
)

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = Color(0xFF5AC8FA),
    onPrimary = Color(0xFF002E44),
    primaryContainer = Color(0x99125A85),
    onPrimaryContainer = Color(0xFFD6F3FF),
    secondary = Color(0xFF9BB4CC),
    onSecondary = Color(0xFF172B3D),
    secondaryContainer = Color(0x40FFFFFF),
    onSecondaryContainer = Color(0xFFFFFFFF),
    surface = Color(0xFF071120),
    surfaceContainerLowest = Color(0x5E070F1C),
    surfaceContainerLow = Color(0x52FFFFFF),
    surfaceContainer = Color(0x4D132238),
    surfaceContainerHigh = Color(0x661C2E47),
    surfaceContainerHighest = Color(0x80263B59)
)

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
    val anim = tween<Color>(durationMillis = 260)
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
    useDynamicColor: Boolean,
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

    val dynamicScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    val rawColorScheme = when {
        isLiquidGlass -> {
            val baseGlass = if (darkTheme) LiquidGlassDarkColorScheme else LiquidGlassLightColorScheme
            if (dynamicScheme != null) {
                baseGlass.copy(
                    primary = dynamicScheme.primary,
                    onPrimary = dynamicScheme.onPrimary,
                    primaryContainer = dynamicScheme.primaryContainer.copy(alpha = 0.72f),
                    onPrimaryContainer = dynamicScheme.onPrimaryContainer,
                    tertiary = dynamicScheme.tertiary
                )
            } else {
                baseGlass
            }
        }
        dynamicScheme != null -> dynamicScheme
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    val colorScheme = animatedColorScheme(rawColorScheme)

    val cardBorder = if (isLiquidGlass) {
        BorderStroke(
            width = 1.4.dp,
            brush = Brush.linearGradient(
                colors = if (darkTheme) {
                    listOf(
                        Color.White.copy(alpha = 0.50f),
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.28f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.98f),
                        Color.White.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.80f)
                    )
                }
            )
        )
    } else {
        null
    }

    // Translucent frosted glass surfaces that contrast sharply against vibrant/photo backdrops
    val cardContainerColor = when {
        isLiquidGlass && darkTheme -> Color(0x47122238)
        isLiquidGlass && !darkTheme -> Color(0x78FFFFFF)
        hasCustomBackground && darkTheme -> colorScheme.surfaceContainerLow.copy(alpha = 0.82f)
        hasCustomBackground && !darkTheme -> colorScheme.surfaceContainerLow.copy(alpha = 0.84f)
        else -> colorScheme.surfaceContainerLow
    }

    val innerFieldColor = when {
        isLiquidGlass && darkTheme -> Color(0x5207101E)
        isLiquidGlass && !darkTheme -> Color(0xA6FFFFFF)
        hasCustomBackground -> colorScheme.surfaceContainerLowest.copy(alpha = 0.86f)
        else -> colorScheme.surfaceContainerLowest
    }

    val dockContainerColor = when {
        isLiquidGlass && darkTheme -> Color(0x66111F33)
        isLiquidGlass && !darkTheme -> Color(0x8FFFFFFF)
        else -> colorScheme.surfaceContainer
    }

    val dockIndicatorColor = when {
        isLiquidGlass && darkTheme -> Color(0x38FFFFFF)
        isLiquidGlass && !darkTheme -> Color(0xD9FFFFFF)
        else -> colorScheme.secondaryContainer
    }

    val visuals = AppThemeVisuals(
        isDark = darkTheme,
        isLiquidGlass = isLiquidGlass,
        hasCustomBackground = hasCustomBackground,
        cardBorder = cardBorder,
        cardContainerColor = cardContainerColor,
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
