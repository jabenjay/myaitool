package com.toolbox.pocket.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Organized long-text viewer supporting:
 * - Smooth two-finger pinch-to-zoom (8sp..32sp) without conflicting with single-finger scrolling
 * - Optional line numbers for structured inspection of long multi-line output
 * - Toggleable soft-wrap vs horizontal scroll
 * - Native text selection
 */
@Composable
fun PinchZoomTextViewer(
    text: String,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    showLineNumbers: Boolean,
    softWrap: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    minFontSizeSp: Float = 8f,
    maxFontSizeSp: Float = 32f,
    baseFontSizeSp: Float = 14f
) {
    var showZoomBadge by remember { mutableStateOf(false) }
    val zoomPercent = ((fontSizeSp / baseFontSizeSp) * 100f).roundToInt()

    LaunchedEffect(fontSizeSp) {
        if (showZoomBadge) {
            delay(1200)
            showZoomBadge = false
        }
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val lines = remember(text) {
        if (text.isEmpty()) emptyList() else text.split('\n')
    }

    Box(
        modifier = modifier
            .pointerInput(minFontSizeSp, maxFontSizeSp, fontSizeSp) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        // Only intercept when 2 or more fingers are pinching
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                val updated = (fontSizeSp * zoomChange)
                                    .coerceIn(minFontSizeSp, maxFontSizeSp)
                                onFontSizeChange(updated)
                                showZoomBadge = true
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        if (text.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val contentModifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .let { mod ->
                    if (!softWrap) mod.horizontalScroll(horizontalScrollState) else mod
                }
                .padding(horizontal = 12.dp, vertical = 12.dp)

            val textSize: TextUnit = fontSizeSp.sp
            val lineHeight: TextUnit = (fontSizeSp * 1.48f).sp

            SelectionContainer {
                Column(
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showLineNumbers) {
                        val maxDigits = lines.size.toString().length.coerceAtLeast(2)
                        lines.forEachIndexed { index, lineText ->
                            Row(
                                modifier = if (softWrap) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = (index + 1).toString().padStart(maxDigits, ' '),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSizeSp * 0.85f).sp,
                                    lineHeight = lineHeight,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .widthIn(min = (maxDigits * (fontSizeSp * 0.65f)).dp)
                                        .padding(end = 10.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .padding(vertical = 2.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (lineText.isEmpty()) " " else lineText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = textSize,
                                    lineHeight = lineHeight,
                                    softWrap = softWrap,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = if (softWrap) Modifier.weight(1f) else Modifier
                                )
                            }
                        }
                    } else {
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = textSize,
                            lineHeight = lineHeight,
                            softWrap = softWrap,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (softWrap) Modifier.fillMaxWidth() else Modifier
                        )
                    }
                }
            }
        }

        // Floating zoom indicator pill
        AnimatedVisibility(
            visible = showZoomBadge,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "$zoomPercent% · ${fontSizeSp.roundToInt()}sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
