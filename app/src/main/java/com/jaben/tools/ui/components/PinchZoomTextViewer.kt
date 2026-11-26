package com.jaben.tools.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
 * High-performance 120fps pinch-to-zoom viewer for very long text:
 * - Intercepts 2-finger pinch on PointerEventPass.Initial so nested scroll NEVER steals it
 * - Applies livePinchScale directly to GPU graphicsLayer (zero frame lag)
 * - Uses LazyColumn so even 10,000+ lines render and re-wrap instantaneously
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
    minFontSizeSp: Float = 1.4f,
    maxFontSizeSp: Float = 56f,
    baseFontSizeSp: Float = 14f
) {
    var livePinchScale by remember { mutableFloatStateOf(1f) }
    var isPinching by remember { mutableStateOf(false) }
    var showZoomBadge by remember { mutableStateOf(false) }

    val latestFontSize by rememberUpdatedState(fontSizeSp)
    val latestOnFontSizeChange by rememberUpdatedState(onFontSizeChange)

    val effectiveFontSizeSp = (fontSizeSp * livePinchScale).coerceIn(minFontSizeSp, maxFontSizeSp)
    val zoomPercent = ((effectiveFontSizeSp / baseFontSizeSp) * 100f).roundToInt()

    LaunchedEffect(isPinching, effectiveFontSizeSp) {
        if (!isPinching && showZoomBadge) {
            delay(1000)
            showZoomBadge = false
        }
    }

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
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinchDetected = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
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
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
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
            val textSize: TextUnit = fontSizeSp.sp
            val lineHeight: TextUnit = (fontSizeSp * 1.46f).sp
            val maxDigits = lines.size.toString().length.coerceAtLeast(2)

            val listModifier = Modifier
                .fillMaxSize()
                .let { mod ->
                    if (!softWrap) mod.horizontalScroll(horizontalScrollState) else mod
                }
                .graphicsLayer {
                    scaleX = livePinchScale
                    scaleY = livePinchScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }

            LazyColumn(
                modifier = listModifier,
                userScrollEnabled = !isPinching,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(lines) { index, lineText ->
                    if (showLineNumbers) {
                        Row(
                            modifier = if (softWrap) Modifier.fillMaxWidth() else Modifier,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = (index + 1).toString().padStart(maxDigits, ' '),
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSizeSp * 0.84f).sp,
                                lineHeight = lineHeight,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .widthIn(min = (maxDigits * (fontSizeSp * 0.65f)).dp)
                                    .padding(end = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .padding(vertical = 2.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                    } else {
                        Text(
                            text = if (lineText.isEmpty()) " " else lineText,
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
