package com.toolbox.pocket.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
 * Silky-smooth 60/120fps pinch-to-zoom long-text viewer:
 * - Uses pointerInput(Unit) + rememberUpdatedState so the gesture stream NEVER restarts mid-pinch
 * - Applies GPU graphicsLayer scaling during active pinch (zero text relayout lag),
 *   then commits the final crisp fontSizeSp smoothly upon finger release
 * - Double-tap resets zoom to 100%
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
    var livePinchScale by remember { mutableFloatStateOf(1f) }
    var isPinching by remember { mutableStateOf(false) }
    var showZoomBadge by remember { mutableStateOf(false) }

    val latestFontSize by rememberUpdatedState(fontSizeSp)
    val latestOnFontSizeChange by rememberUpdatedState(onFontSizeChange)

    val effectiveFontSizeSp = (fontSizeSp * livePinchScale).coerceIn(minFontSizeSp, maxFontSizeSp)
    val zoomPercent = ((effectiveFontSizeSp / baseFontSizeSp) * 100f).roundToInt()

    val animatedLayerScale by animateFloatAsState(
        targetValue = if (isPinching) livePinchScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pinchScale"
    )

    LaunchedEffect(isPinching, effectiveFontSizeSp) {
        if (!isPinching && showZoomBadge) {
            delay(1100)
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        latestOnFontSizeChange(baseFontSizeSp)
                        showZoomBadge = true
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pinchDetected = false
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                pinchDetected = true
                                isPinching = true
                                showZoomBadge = true
                                val minRatio = minFontSizeSp / latestFontSize
                                val maxRatio = maxFontSizeSp / latestFontSize
                                livePinchScale = (livePinchScale * zoomChange).coerceIn(minRatio, maxRatio)
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (pinchDetected) {
                        val committed = (latestFontSize * livePinchScale)
                            .coerceIn(minFontSizeSp, maxFontSizeSp)
                        livePinchScale = 1f
                        isPinching = false
                        latestOnFontSizeChange(committed)
                    }
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
                .graphicsLayer {
                    scaleX = animatedLayerScale
                    scaleY = animatedLayerScale
                    transformOrigin = TransformOrigin(0f, 0f)
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
                    text = "$zoomPercent% · ${effectiveFontSizeSp.roundToInt()}sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
