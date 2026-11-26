package com.toolbox.pocket.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.toolbox.pocket.tools.qr.QrCodeHelper
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var handled by remember { mutableStateOf(false) }
    var statusTip by remember { mutableStateOf("将二维码放入取景框内自动识别") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && !handled) {
            val decoded = QrCodeHelper.decodeFromUri(context, uri)
            if (!decoded.isNullOrBlank()) {
                handled = true
                onResult(decoded)
            } else {
                statusTip = "未在所选图片中识别到二维码"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { analyzerExecutor.shutdownNow() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val reader = MultiFormatReader().apply {
                            setHints(
                                mapOf(
                                    DecodeHintType.TRY_HARDER to true,
                                    DecodeHintType.CHARACTER_SET to "UTF-8"
                                )
                            )
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .apply {
                                setAnalyzer(analyzerExecutor) { imageProxy ->
                                    if (handled) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    try {
                                        val plane = imageProxy.planes[0]
                                        val buf = plane.buffer
                                        val data = ByteArray(buf.remaining())
                                        buf.get(data)
                                        val source = PlanarYUVLuminanceSource(
                                            data,
                                            plane.rowStride,
                                            imageProxy.height,
                                            0,
                                            0,
                                            imageProxy.width,
                                            imageProxy.height,
                                            false
                                        )
                                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                                        val text = runCatching { reader.decodeWithState(bitmap).text }.getOrNull()
                                        if (!text.isNullOrBlank() && !handled) {
                                            handled = true
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onResult(text)
                                            }
                                        }
                                    } catch (_: Exception) {
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }

                        runCatching {
                            provider.unbindAll()
                            cameraRef = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanner overlay with translucent mask, corners & animated laser line
            val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
            val scanFraction by infiniteTransition.animateFloat(
                initialValue = 0.08f,
                targetValue = 0.92f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scanFraction"
            )
            val accentColor = MaterialTheme.colorScheme.primary

            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxSize = size.width * 0.68f
                val left = (size.width - boxSize) / 2f
                val top = (size.height - boxSize) / 2f - 40.dp.toPx()
                val cornerLen = 26.dp.toPx()
                val strokeW = 3.5.dp.toPx()

                drawRect(Color.Black.copy(alpha = 0.52f))
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Four corner accents
                // Top-left
                drawLine(accentColor, Offset(left, top + cornerLen), Offset(left, top), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
                // Top-right
                drawLine(accentColor, Offset(left + boxSize - cornerLen, top), Offset(left + boxSize, top), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLen), strokeW, StrokeCap.Round)
                // Bottom-left
                drawLine(accentColor, Offset(left, top + boxSize - cornerLen), Offset(left, top + boxSize), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(left, top + boxSize), Offset(left + cornerLen, top + boxSize), strokeW, StrokeCap.Round)
                // Bottom-right
                drawLine(accentColor, Offset(left + boxSize - cornerLen, top + boxSize), Offset(left + boxSize, top + boxSize), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(left + boxSize, top + boxSize - cornerLen), Offset(left + boxSize, top + boxSize), strokeW, StrokeCap.Round)

                // Animated scan line
                val lineY = top + boxSize * scanFraction
                drawLine(
                    color = accentColor.copy(alpha = 0.85f),
                    start = Offset(left + 16.dp.toPx(), lineY),
                    end = Offset(left + boxSize - 16.dp.toPx(), lineY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "需要相机权限才能实时扫描二维码",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予相机权限")
                }
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "扫一扫",
                color = Color.White,
                fontSize = 17.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Bottom tip + Torch + Gallery buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = statusTip,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f)
                    ) {
                        IconButton(
                            onClick = {
                                val next = !torchEnabled
                                torchEnabled = next
                                runCatching { cameraRef?.cameraControl?.enableTorch(next) }
                            }
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = "手电筒",
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("手电筒", color = Color.White, fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        IconButton(
                            onClick = {
                                galleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = "相册选图",
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("相册", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
