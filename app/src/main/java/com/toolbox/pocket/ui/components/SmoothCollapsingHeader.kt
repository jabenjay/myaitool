package com.toolbox.pocket.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

@Stable
class SmoothHeaderScrollState(val maxCollapsePx: Float) {
    var offsetPx by mutableFloatStateOf(0f)
        private set

    val progress: Float
        get() = if (maxCollapsePx <= 0f) 0f else (-offsetPx / maxCollapsePx).coerceIn(0f, 1f)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            if (delta < 0f) {
                val prev = offsetPx
                offsetPx = (offsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                return Offset(0f, offsetPx - prev)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            if (delta > 0f) {
                val prev = offsetPx
                offsetPx = (offsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                return Offset(0f, offsetPx - prev)
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberSmoothHeaderScrollState(): SmoothHeaderScrollState {
    val maxCollapsePx = with(LocalDensity.current) { 34.dp.toPx() }
    return remember(maxCollapsePx) { SmoothHeaderScrollState(maxCollapsePx) }
}

/**
 * Single-Text GPU-scaled collapsing header:
 * Zero cross-fade snap, zero clipping, 1:1 finger-tracked silky smooth shrink.
 */
@Composable
fun SmoothCollapsingHeader(
    title: String,
    collapseProgress: Float,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val headerHeight = lerp(82.dp, 48.dp, collapseProgress)
    val scale = 1f - (0.24f * collapseProgress) // 1.0 -> 0.76 smoothly

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(headerHeight)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = lerp(12.dp, 10.dp, collapseProgress))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        )
        if (trailingAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = lerp(4.dp, 2.dp, collapseProgress))
            ) {
                trailingAction()
            }
        }
    }
}
