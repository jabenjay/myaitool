package com.jaben.tools.tools.compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.jaben.tools.data.AppLogManager
import com.jaben.tools.data.OutputStorageHelper
import com.jaben.tools.tools.format.FormatFactoryEngine
import com.jaben.tools.tools.format.MediaCategory
import com.jaben.tools.tools.format.SourceMediaInfo
import com.jaben.tools.tools.format.TargetFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class CompressFileType(val label: String, val subFolder: String) {
    VIDEO("视频", OutputStorageHelper.SUB_VIDEOS),
    IMAGE("图片", OutputStorageHelper.SUB_PICTURES),
    AUDIO("音频", OutputStorageHelper.SUB_SOUNDS),
    OTHER("通用文件 (ZIP)", OutputStorageHelper.SUB_FILES)
}

data class CompressSourceMeta(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val fileType: CompressFileType,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val videoBitrateBps: Int = 0,
    val fps: Int = 30
)

data class CompressEstimate(
    val estimatedBytes: Long,
    val requestedBytes: Long,
    val isBelowFloor: Boolean
)

data class CompressOutputResult(
    val tempFile: File,
    val outputName: String,
    val mimeType: String,
    val subFolder: String,
    val outputSizeBytes: Long
)

object FileCompressorEngine {

    fun clearCache(context: Context) {
        runCatching {
            File(context.cacheDir, "compressor_out").deleteRecursively()
        }
    }

    fun inspectFile(context: Context, uri: Uri): CompressSourceMeta {
        var name = "未命名文件"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0) size = c.getLong(si)
                }
            }
        }
        if (size <= 0L) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    if (it.length > 0) size = it.length
                }
            }
        }

        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()

        val type = when {
            mime.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "avi", "webm", "3gp", "m4v", "ts") ->
                CompressFileType.VIDEO
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp", "gif") ->
                CompressFileType.IMAGE
            mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr") ->
                CompressFileType.AUDIO
            else -> CompressFileType.OTHER
        }

        var w = 0
        var h = 0
        var durMs = 0L
        var bitrate = 0
        var fps = 30

        when (type) {
            CompressFileType.IMAGE -> {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    w = opts.outWidth.coerceAtLeast(0)
                    h = opts.outHeight.coerceAtLeast(0)
                }
            }
            CompressFileType.VIDEO -> {
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, uri)
                    durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val frameRateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        fps = frameRateStr?.toFloatOrNull()?.roundToInt()?.coerceIn(1, 120) ?: 30
                    }
                    mmr.release()
                }
                if (fps == 30) {
                    runCatching {
                        val extor = MediaExtractor()
                        extor.setDataSource(context, uri, null)
                        for (i in 0 until extor.trackCount) {
                            val fmt = extor.getTrackFormat(i)
                            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                                if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                    fps = fmt.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(1, 120)
                                }
                                break
                            }
                        }
                        extor.release()
                    }
                }
            }
            CompressFileType.AUDIO -> {
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, uri)
                    durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                    mmr.release()
                }
            }
            CompressFileType.OTHER -> Unit
        }

        return CompressSourceMeta(
            uri = uri,
            displayName = name,
            sizeBytes = size,
            mimeType = mime.ifBlank { "application/octet-stream" },
            fileType = type,
            width = w,
            height = h,
            durationMs = durMs,
            videoBitrateBps = bitrate,
            fps = fps
        )
    }

    /**
     * Suggests (videoMaxHeight, videoTargetFps) for a given targetSizePercent (10..95, higher = larger file),
     * prioritizing FPS reduction before Resolution reduction.
     */
    fun suggestVideoParams(meta: CompressSourceMeta, targetSizePercent: Int): Pair<Int, Int> {
        val pct = targetSizePercent.coerceIn(10, 95)
        return when {
            pct >= 72 -> 0 to 0 // Original resolution & FPS
            pct >= 52 -> {
                // Prioritize lowering FPS first
                val targetFps = if (meta.fps > 30) 30 else 24
                0 to targetFps
            }
            pct >= 34 -> {
                val targetFps = 24
                val targetH = when {
                    meta.height > 1080 -> 1080
                    meta.height > 720 -> 720
                    else -> 0
                }
                targetH to targetFps
            }
            pct >= 20 -> {
                val targetFps = 15
                val targetH = if (meta.height > 720) 720 else 480
                targetH to targetFps
            }
            else -> {
                val targetFps = 15
                val targetH = if (meta.height > 480) 480 else 360
                targetH to targetFps
            }
        }
    }

    fun estimateCompressedSize(
        meta: CompressSourceMeta,
        targetSizePercent: Int, // 10 (smallest) .. 95 (closest to original)
        videoMaxHeight: Int,
        videoTargetFps: Int,
        useHevc: Boolean,
        imageScalePercent: Int
    ): CompressEstimate {
        if (meta.sizeBytes <= 0L) return CompressEstimate(0L, 0L, false)
        val targetRatio = targetSizePercent.coerceIn(10, 95) / 100.0
        val requestedBytes = (meta.sizeBytes * targetRatio).toLong().coerceAtLeast(1024L)

        return when (meta.fileType) {
            CompressFileType.VIDEO -> {
                val srcH = meta.height.coerceAtLeast(360)
                val srcW = meta.width.coerceAtLeast(640)
                val effH = if (videoMaxHeight > 0 && srcH > videoMaxHeight) videoMaxHeight else srcH
                val effW = (srcW.toDouble() * effH / srcH).roundToInt().coerceAtLeast(2)
                val effFps = if (videoTargetFps > 0 && meta.fps > videoTargetFps) videoTargetFps else meta.fps

                val resRatio = (effH.toDouble() / srcH).let { it * it }.coerceIn(0.08, 1.0)
                val fpsRatio = (effFps.toDouble() / meta.fps.coerceAtLeast(1)).let { Math.pow(it, 0.75) }.coerceIn(0.25, 1.0)
                val codecFactor = if (useHevc) 0.72 else 1.0

                // Realistic minimum bitrate floor for the chosen resolution + FPS
                val durSec = (meta.durationMs / 1000.0).coerceAtLeast(1.0)
                val bppFloor = if (useHevc) 0.026 else 0.038
                val minVideoBps = effW * effH * effFps * bppFloor
                val minBytesByBpp = ((minVideoBps + 64_000.0) * durSec / 8.0).toLong()
                val minBytesByRatio = (meta.sizeBytes * resRatio * fpsRatio * codecFactor * 0.28).toLong()
                val floorBytes = max(minBytesByBpp, minBytesByRatio).coerceAtMost(meta.sizeBytes)

                // Max reasonable size at this resolution/FPS/codec
                val ceilingBytes = (meta.sizeBytes * resRatio * fpsRatio * codecFactor * 1.02)
                    .toLong()
                    .coerceIn(floorBytes, meta.sizeBytes)

                val isBelowFloor = requestedBytes < floorBytes
                val actualEst = requestedBytes.coerceIn(floorBytes, ceilingBytes)
                CompressEstimate(
                    estimatedBytes = actualEst,
                    requestedBytes = requestedBytes,
                    isBelowFloor = isBelowFloor
                )
            }
            CompressFileType.IMAGE -> {
                val scaleFactor = (imageScalePercent / 100.0).let { it * it }
                val floorBytes = max(2048L, (meta.sizeBytes * scaleFactor * 0.08).toLong())
                val est = (meta.sizeBytes * targetRatio * scaleFactor).toLong().coerceIn(floorBytes, meta.sizeBytes)
                CompressEstimate(est, requestedBytes, requestedBytes < floorBytes)
            }
            CompressFileType.AUDIO -> {
                val floorBytes = max(4096L, (meta.sizeBytes * 0.12).toLong())
                val est = requestedBytes.coerceIn(floorBytes, meta.sizeBytes)
                CompressEstimate(est, requestedBytes, requestedBytes < floorBytes)
            }
            CompressFileType.OTHER -> {
                val compressionStrength = (100 - targetSizePercent) / 100.0
                val est = (meta.sizeBytes * (0.90 - compressionStrength * 0.42)).toLong().coerceIn(512L, meta.sizeBytes)
                CompressEstimate(est, requestedBytes, false)
            }
        }
    }

    suspend fun compress(
        context: Context,
        meta: CompressSourceMeta,
        targetSizePercent: Int,
        videoMaxHeight: Int,
        videoTargetFps: Int,
        useHevc: Boolean,
        imageScalePercent: Int,
        imageOutputExt: String,
        onProgress: (Float) -> Unit
    ): CompressOutputResult {
        val outDir = File(context.cacheDir, "compressor_out").apply { mkdirs() }
        outDir.listFiles()?.forEach { runCatching { it.delete() } }

        val baseName = meta.displayName.substringBeforeLast(".").ifBlank { "compressed" }
        val suffix = System.currentTimeMillis() % 10000

        return when (meta.fileType) {
            CompressFileType.VIDEO -> {
                val estimate = estimateCompressedSize(
                    meta = meta,
                    targetSizePercent = targetSizePercent,
                    videoMaxHeight = videoMaxHeight,
                    videoTargetFps = videoTargetFps,
                    useHevc = useHevc,
                    imageScalePercent = imageScalePercent
                )
                val outName = "${baseName}_cmp_${suffix}.mp4"
                val outFile = File(outDir, outName)
                compressVideoGpu(
                    context = context,
                    meta = meta,
                    targetOutputBytes = estimate.estimatedBytes,
                    maxHeight = videoMaxHeight,
                    targetFps = videoTargetFps,
                    useHevc = useHevc,
                    outFile = outFile,
                    onProgress = onProgress
                )
                CompressOutputResult(
                    tempFile = outFile,
                    outputName = outName,
                    mimeType = "video/mp4",
                    subFolder = OutputStorageHelper.SUB_VIDEOS,
                    outputSizeBytes = outFile.length()
                )
            }
            CompressFileType.IMAGE -> {
                val ext = if (imageOutputExt == "webp") "webp" else "jpg"
                val mime = if (ext == "webp") "image/webp" else "image/jpeg"
                val outName = "${baseName}_cmp_${suffix}.$ext"
                val outFile = File(outDir, outName)
                compressImage(
                    context = context,
                    uri = meta.uri,
                    targetSizePercent = targetSizePercent,
                    scalePercent = imageScalePercent,
                    useWebp = ext == "webp",
                    outFile = outFile,
                    onProgress = onProgress
                )
                CompressOutputResult(
                    tempFile = outFile,
                    outputName = outName,
                    mimeType = mime,
                    subFolder = OutputStorageHelper.SUB_PICTURES,
                    outputSizeBytes = outFile.length()
                )
            }
            CompressFileType.AUDIO -> {
                val qualityRatio = targetSizePercent.coerceIn(10, 95) / 100f
                val targetKbps = (32 + (288 * qualityRatio)).roundToInt().coerceIn(32, 320)
                val res = FormatFactoryEngine.convert(
                    context = context,
                    source = SourceMediaInfo(meta.uri, meta.displayName, meta.sizeBytes, ""),
                    target = TargetFormat.AUD_M4A,
                    audioBitrateKbps = targetKbps,
                    onProgress = onProgress
                )
                // Move into compressor_out and clean format_factory_out
                val outName = "${baseName}_cmp_${suffix}.m4a"
                val outFile = File(outDir, outName)
                res.tempFile.copyTo(outFile, overwrite = true)
                FormatFactoryEngine.clearCache(context)
                CompressOutputResult(
                    tempFile = outFile,
                    outputName = outName,
                    mimeType = "audio/mp4",
                    subFolder = OutputStorageHelper.SUB_SOUNDS,
                    outputSizeBytes = outFile.length()
                )
            }
            CompressFileType.OTHER -> {
                val outName = "${baseName}_cmp_${suffix}.zip"
                val outFile = File(outDir, outName)
                compressToZip(context, meta, targetSizePercent, outFile, onProgress)
                CompressOutputResult(
                    tempFile = outFile,
                    outputName = outName,
                    mimeType = "application/zip",
                    subFolder = OutputStorageHelper.SUB_FILES,
                    outputSizeBytes = outFile.length()
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun compressVideoGpu(
        context: Context,
        meta: CompressSourceMeta,
        targetOutputBytes: Long,
        maxHeight: Int,
        targetFps: Int,
        useHevc: Boolean,
        outFile: File,
        onProgress: (Float) -> Unit
    ) {
        val durSec = (meta.durationMs / 1000.0).coerceAtLeast(1.0)
        val totalTargetBps = (targetOutputBytes * 8.0 / durSec).roundToInt()
        val targetVideoBps = (totalTargetBps - 96_000).coerceIn(250_000, 20_000_000)

        AppLogManager.log(
            "COMPRESS",
            "GPU视频压缩: ${meta.displayName}, videoBitrate=${targetVideoBps / 1000}kbps, h=$maxHeight, fps=$targetFps, hevc=$useHevc"
        )

        val deferred = CompletableDeferred<Unit>()
        val transformer = withContext(Dispatchers.Main) {
            val videoEffects = buildList {
                if (maxHeight > 0 && meta.height > maxHeight) {
                    add(Presentation.createForHeight(maxHeight))
                }
                if (targetFps > 0 && targetFps < meta.fps) {
                    add(FrameDropEffect.createDefaultFrameDropEffect(targetFps.toFloat()))
                }
            }
            val effects = if (videoEffects.isNotEmpty()) Effects(emptyList(), videoEffects) else Effects.EMPTY
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(meta.uri))
                .setEffects(effects)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(targetVideoBps)
                        .build()
                )
                .build()

            val t = Transformer.Builder(context)
                .setVideoMimeType(if (useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        deferred.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        deferred.completeExceptionally(exportException)
                    }
                })
                .build()
            t.start(edited, outFile.absolutePath)
            t
        }

        try {
            withContext(Dispatchers.Main) {
                val holder = ProgressHolder()
                while (!deferred.isCompleted && coroutineContext.isActive) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((holder.progress / 100f).coerceIn(0.02f, 0.99f))
                    }
                    delay(100)
                }
            }
            deferred.await()
            onProgress(1f)
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                runCatching { transformer.cancel() }
            }
            throw t
        }
    }

    private suspend fun compressImage(
        context: Context,
        uri: Uri,
        targetSizePercent: Int,
        scalePercent: Int,
        useWebp: Boolean,
        outFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProgress(0.2f)
        var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } ?: throw IllegalArgumentException("无法读取图片")

        if (scalePercent in 10..99) {
            val nw = max(1, (bitmap.width * scalePercent / 100f).roundToInt())
            val nh = max(1, (bitmap.height * scalePercent / 100f).roundToInt())
            val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        onProgress(0.55f)
        val quality = targetSizePercent.coerceIn(10, 95)
        try {
            FileOutputStream(outFile).use { fos ->
                if (useWebp) {
                    val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    }
                    bitmap.compress(fmt, quality, fos)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                }
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        onProgress(1f)
    }

    private suspend fun compressToZip(
        context: Context,
        meta: CompressSourceMeta,
        targetSizePercent: Int,
        outFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val level = ((100 - targetSizePercent) / 10).coerceIn(1, Deflater.BEST_COMPRESSION)
        val total = meta.sizeBytes.coerceAtLeast(1L)
        var readBytes = 0L
        val buf = ByteArray(8192)

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            zos.setLevel(level)
            zos.putNextEntry(ZipEntry(meta.displayName))
            context.contentResolver.openInputStream(meta.uri)?.use { input ->
                while (coroutineContext.isActive) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    zos.write(buf, 0, n)
                    readBytes += n
                    onProgress((readBytes.toFloat() / total).coerceIn(0.05f, 0.98f))
                }
            } ?: throw IllegalStateException("无法读取源文件")
            zos.closeEntry()
        }
        onProgress(1f)
    }
}
