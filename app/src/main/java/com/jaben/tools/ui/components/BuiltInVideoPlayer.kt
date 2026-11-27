package com.jaben.tools.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun BuiltInVideoPlayerOverlay(
    videoUrl: String,
    pageUrl: String,
    pageTitle: String,
    userAgent: String,
    initialPositionMs: Long = 0L,
    onDownloadVideo: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findHostActivity() }

    var isFullscreen by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }
    var minimizedOnLeft by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val speeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.28f, 1.5f, 2.0f) }

    val exoPlayer = remember(videoUrl, pageUrl) {
        val cookie = runCatching { CookieManager.getInstance().getCookie(pageUrl) }.getOrNull().orEmpty()
        val headers = buildMap {
            if (pageUrl.isNotBlank()) put("Referer", pageUrl)
            if (cookie.isNotBlank()) put("Cookie", cookie)
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent.ifBlank { "Mozilla/5.0" })
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                if (initialPositionMs > 0L) seekTo(initialPositionMs)
                playWhenReady = true
                prepare()
            }
    }

    fun applyFullscreenSystemUi(fullscreen: Boolean) {
        activity?.let { act ->
            val window = act.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (fullscreen) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            act.requestedOrientation = if (fullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    fun closePlayer() {
        applyFullscreenSystemUi(false)
        onClose()
    }

    BackHandler(enabled = true) {
        when {
            isFullscreen -> {
                isFullscreen = false
                applyFullscreenSystemUi(false)
            }
            isMinimized -> {
                isMinimized = false
            }
            else -> closePlayer()
        }
    }

    LaunchedEffect(isFullscreen) {
        applyFullscreenSystemUi(isFullscreen)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                errorMessage = "无法播放该视频（${error.errorCodeName}）"
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            applyFullscreenSystemUi(false)
        }
    }

    LaunchedEffect(exoPlayer, isSeeking) {
        while (true) {
            if (!isSeeking) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                currentPositionMs = pos
                durationMs = dur
                sliderPosition = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            }
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isFullscreen) Modifier.background(Color.Black) else Modifier)
            .zIndex(25f)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val baseWidthPx = screenWidthPx * 0.86f
        val maxRestingScale = (screenWidthPx / baseWidthPx).coerceAtLeast(1f)
        val minScale = 0.25f

        val scaleAnim = remember { Animatable(0.88f) }
        val offsetXAnim = remember { Animatable((screenWidthPx - baseWidthPx) / 2f) }
        val offsetYAnim = remember { Animatable(with(density) { 80.dp.toPx() }) }

        // Entrance animation when floating player opens
        LaunchedEffect(Unit) {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
            )
        }

        val currentWidthPx = baseWidthPx * scaleAnim.value
        val safeTopPx = with(density) { 28.dp.toPx() }
        val safeBottomPx = with(density) { 56.dp.toPx() }

        // Narrow & tall iPhone-style edge handle
        val minimizedWidthDp = 22.dp
        val minimizedHeightDp = 68.dp
        val minimizedWidthPx = with(density) { minimizedWidthDp.toPx() }
        val minimizedHeightPx = with(density) { minimizedHeightDp.toPx() }
        var minimizedY by remember { mutableFloatStateOf(screenHeightPx * 0.42f) }

        if (isMinimized && !isFullscreen) {
            // Keep ExoPlayer alive in background at 1px invisible size so audio keeps playing
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(1, 1)
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
            )

            // Narrow, tall pill tab snapped to left or right edge with an iPhone-style vertical bar
            val snapX = if (minimizedOnLeft) 0 else (screenWidthPx - minimizedWidthPx).roundToInt()
            val tabShape = if (minimizedOnLeft) {
                RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
            } else {
                RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
            }
            Surface(
                shape = tabShape,
                color = Color(0xFF3A3A3E).copy(alpha = 0.88f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .offset { IntOffset(snapX, minimizedY.roundToInt()) }
                    .size(width = minimizedWidthDp, height = minimizedHeightDp)
                    .clip(tabShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            minimizedY = (minimizedY + dragAmount.y).coerceIn(
                                safeTopPx,
                                (screenHeightPx - minimizedHeightPx - safeBottomPx).coerceAtLeast(safeTopPx)
                            )
                        }
                    }
                    .clickable {
                        isMinimized = false
                        val restScale = scaleAnim.value.coerceIn(minScale, maxRestingScale)
                        val restW = baseWidthPx * restScale
                        val restH = restW * (9f / 16f)
                        val targetX = ((screenWidthPx - restW) / 2f).coerceAtLeast(0f)
                        val targetY = minimizedY.coerceIn(
                            safeTopPx,
                            (screenHeightPx - restH - safeBottomPx).coerceAtLeast(safeTopPx)
                        )
                        scope.launch {
                            launch {
                                scaleAnim.animateTo(
                                    restScale,
                                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            launch {
                                offsetXAnim.animateTo(
                                    targetX,
                                    spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            launch {
                                offsetYAnim.animateTo(
                                    targetY,
                                    spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    // iPhone-style slim vertical indicator bar
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.90f))
                    )
                }
            }
        } else {
            val playerBoxModifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { controlsVisible = !controlsVisible }
            } else {
                val baseWidthDp = with(density) { baseWidthPx.toDp() }
                Modifier
                    .offset { IntOffset(offsetXAnim.value.roundToInt(), offsetYAnim.value.roundToInt()) }
                    .width(baseWidthDp)
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .shadow(12.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .pointerInput(screenWidthPx, screenHeightPx) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var moved = false
                            var wasMultiTouch = false
                            var totalPanDist = 0f
                            val downTime = System.currentTimeMillis()
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) wasMultiTouch = true
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (zoomChange != 1f || panChange.x != 0f || panChange.y != 0f) {
                                    totalPanDist += kotlin.math.hypot(panChange.x, panChange.y)
                                    if (totalPanDist > 10f || zoomChange != 1f) {
                                        moved = true
                                    }
                                    scope.launch {
                                        if (zoomChange != 1f) {
                                            // Allow pinching well beyond screen width (up to 2.2x maxRestingScale)
                                            val nextScale = (scaleAnim.value * zoomChange)
                                                .coerceIn(minScale, maxRestingScale * 2.2f)
                                            val oldW = baseWidthPx * scaleAnim.value
                                            val newW = baseWidthPx * nextScale
                                            val oldH = oldW * (9f / 16f)
                                            val newH = newW * (9f / 16f)
                                            scaleAnim.snapTo(nextScale)
                                            offsetXAnim.snapTo(offsetXAnim.value - (newW - oldW) / 2f)
                                            offsetYAnim.snapTo(offsetYAnim.value - (newH - oldH) / 2f)
                                        }
                                        if (moved) {
                                            offsetXAnim.snapTo(offsetXAnim.value + panChange.x)
                                            offsetYAnim.snapTo(offsetYAnim.value + panChange.y)
                                        }
                                    }
                                    if (moved) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // Tap -> toggle controls visibility
                            if (!moved && !wasMultiTouch && (System.currentTimeMillis() - downTime) < 350L) {
                                controlsVisible = !controlsVisible
                                return@awaitEachGesture
                            }

                            val curScale = scaleAnim.value
                            val clampedScale = curScale.coerceIn(minScale, maxRestingScale)
                            val finalW = baseWidthPx * clampedScale
                            val finalH = finalW * (9f / 16f)

                            var targetX = offsetXAnim.value
                            var targetY = offsetYAnim.value
                            if (clampedScale != curScale) {
                                val oldW = baseWidthPx * curScale
                                val oldH = oldW * (9f / 16f)
                                targetX -= (finalW - oldW) / 2f
                                targetY -= (finalH - oldH) / 2f
                            }

                            // Minimize ONLY when > 2/3 of the floating window is obscured beyond left/right edge
                            val hiddenLeft = -targetX
                            val hiddenRight = (targetX + finalW) - screenWidthPx
                            val twoThirdsW = finalW * (2f / 3f)

                            if (moved && (hiddenLeft > twoThirdsW || hiddenRight > twoThirdsW)) {
                                minimizedOnLeft = hiddenLeft > twoThirdsW
                                minimizedY = targetY.coerceIn(
                                    safeTopPx,
                                    (screenHeightPx - minimizedHeightPx - safeBottomPx).coerceAtLeast(safeTopPx)
                                )
                                isMinimized = true
                            } else {
                                val maxX = (screenWidthPx - finalW).coerceAtLeast(0f)
                                val maxY = (screenHeightPx - finalH - safeBottomPx).coerceAtLeast(safeTopPx)
                                val clampedX = targetX.coerceIn(0f, maxX)
                                val clampedY = targetY.coerceIn(safeTopPx, maxY)

                                val bounceSpec = spring<Float>(
                                    dampingRatio = 0.68f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                                scope.launch {
                                    launch { scaleAnim.animateTo(clampedScale, bounceSpec) }
                                    launch { offsetXAnim.animateTo(clampedX, bounceSpec) }
                                    launch { offsetYAnim.animateTo(clampedY, bounceSpec) }
                                }
                            }
                        }
                    }
            }

            Box(modifier = playerBoxModifier) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isBuffering && errorMessage == null) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                    )
                }

                errorMessage?.let { msg ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(msg, color = Color.White, fontSize = 12.sp)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.clickable { closePlayer() }
                        ) {
                            Text(
                                text = "关闭",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top bar: Top-left X + Video Name, Top-right Speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                                    )
                                )
                                .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { closePlayer() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭播放器",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pageTitle.ifBlank { "视频播放" },
                                color = Color.White,
                                fontSize = if (isFullscreen) 14.sp else 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                                        val nextSpeed = speeds[nextIdx]
                                        playbackSpeed = nextSpeed
                                        exoPlayer.setPlaybackSpeed(nextSpeed)
                                    }
                            ) {
                                val speedLabel = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x"
                                Text(
                                    text = speedLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Center Play/Pause
                        if (errorMessage == null) {
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(if (isFullscreen) 56.dp else 40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isFullscreen) 32.dp else 24.dp)
                                )
                            }
                        }

                        // Middle-Right: Download current video (Icon only)
                        if (errorMessage == null) {
                            IconButton(
                                onClick = { onDownloadVideo(videoUrl) },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = if (isFullscreen) 16.dp else 6.dp)
                                    .size(if (isFullscreen) 42.dp else 32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "下载视频",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isFullscreen) 22.dp else 17.dp)
                                )
                            }
                        }

                        // Bottom bar: Left time/total, Center slim expressive progress line, Right fullscreen toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                    )
                                )
                                .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatVideoTime(currentPositionMs)}/${formatVideoTime(durationMs)}",
                                color = Color.White,
                                fontSize = if (isFullscreen) 12.sp else 10.sp
                            )
                            ExpressiveSlimSeekBar(
                                value = sliderPosition,
                                onValueChange = { ratio ->
                                    isSeeking = true
                                    sliderPosition = ratio
                                    if (durationMs > 0L) {
                                        currentPositionMs = (ratio * durationMs).toLong()
                                    }
                                },
                                onValueChangeFinished = {
                                    if (durationMs > 0L) {
                                        exoPlayer.seekTo((sliderPosition * durationMs).toLong())
                                    }
                                    isSeeking = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { isFullscreen = !isFullscreen },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = if (isFullscreen) "退出全屏" else "全屏播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveSlimSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(22.dp)
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                    onValueChange(ratio)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onValueChange((offset.x / barWidthPx).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / barWidthPx).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.30f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(if (isDragging) 3.5.dp else 2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
        val thumbSize = if (isDragging) 11.dp else 8.dp
        val density = LocalDensity.current
        val thumbOffsetPx = ((barWidthPx - with(density) { thumbSize.toPx() }) * value.coerceIn(0f, 1f))
            .coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

private fun formatVideoTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
