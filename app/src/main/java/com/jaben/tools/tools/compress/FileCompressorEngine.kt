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

data class CompressOutputResult(
    val tempFile: File,
    val outputName: String,
    val mimeType: String,
    val subFolder: String,
    val outputSizeBytes: Long
)

object FileCompressorEngine {

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

    fun estimateCompressedSize(
        meta: CompressSourceMeta,
        compressionPercent: Int, // 10 (lightest) .. 95 (strongest)
        videoMaxHeight: Int,
        videoTargetFps: Int,
        useHevc: Boolean,
        imageScalePercent: Int
    ): Long {
        if (meta.sizeBytes <= 0L) return 0L
        val keepRatio = (100 - compressionPercent).coerceIn(5, 95) / 100.0

        return when (meta.fileType) {
            CompressFileType.VIDEO -> {
                val resFactor = if (videoMaxHeight > 0 && meta.height > videoMaxHeight) {
                    (videoMaxHeight.toDouble() / meta.height).let { it * it }.coerceIn(0.15, 1.0)
                } else 1.0
                val fpsFactor = if (videoTargetFps > 0 && meta.fps > videoTargetFps) {
                    (videoTargetFps.toDouble() / meta.fps).coerceIn(0.35, 1.0)
                } else 1.0
                val codecFactor = if (useHevc) 0.72 else 1.0
                val est = meta.sizeBytes * keepRatio * (0.4 + 0.6 * resFactor) * (0.6 + 0.4 * fpsFactor) * codecFactor
                est.toLong().coerceIn((meta.sizeBytes * 0.04).toLong().coerceAtLeast(1024L), meta.sizeBytes)
            }
            CompressFileType.IMAGE -> {
                val scaleFactor = (imageScalePercent / 100.0).let { it * it }
                val est = meta.sizeBytes * keepRatio * scaleFactor
                est.toLong().coerceIn(1024L, meta.sizeBytes)
            }
            CompressFileType.AUDIO -> {
                val est = meta.sizeBytes * keepRatio
                est.toLong().coerceIn(2048L, meta.sizeBytes)
            }
            CompressFileType.OTHER -> {
                val levelFactor = 0.88 - (compressionPercent / 100.0) * 0.45
                (meta.sizeBytes * levelFactor).toLong().coerceIn(256L, meta.sizeBytes)
            }
        }
    }

    suspend fun compress(
        context: Context,
        meta: CompressSourceMeta,
        compressionPercent: Int,
        videoMaxHeight: Int,
        videoTargetFps: Int,
        useHevc: Boolean,
        imageScalePercent: Int,
        imageOutputExt: String, // "jpg" or "webp"
        onProgress: (Float) -> Unit
    ): CompressOutputResult {
        val outDir = File(context.cacheDir, "compressor_out").apply { mkdirs() }
        outDir.listFiles()?.forEach { runCatching { it.delete() } }

        val baseName = meta.displayName.substringBeforeLast(".").ifBlank { "compressed" }
        val suffix = System.currentTimeMillis() % 10000

        return when (meta.fileType) {
            CompressFileType.VIDEO -> {
                val outName = "${baseName}_cmp_${suffix}.mp4"
                val outFile = File(outDir, outName)
                compressVideoGpu(
                    context = context,
                    meta = meta,
                    compressionPercent = compressionPercent,
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
                    compressionPercent = compressionPercent,
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
                val qualityRatio = (100 - compressionPercent).coerceIn(10, 95) / 100f
                val targetKbps = (32 + (288 * qualityRatio)).roundToInt().coerceIn(32, 320)
                val res = FormatFactoryEngine.convert(
                    context = context,
                    source = SourceMediaInfo(meta.uri, meta.displayName, meta.sizeBytes, ""),
                    target = TargetFormat.AUD_M4A,
                    audioBitrateKbps = targetKbps,
                    onProgress = onProgress
                )
                CompressOutputResult(
                    tempFile = res.tempFile,
                    outputName = "${baseName}_cmp_${suffix}.m4a",
                    mimeType = "audio/mp4",
                    subFolder = OutputStorageHelper.SUB_SOUNDS,
                    outputSizeBytes = res.outputSizeBytes
                )
            }
            CompressFileType.OTHER -> {
                val outName = "${baseName}_cmp_${suffix}.zip"
                val outFile = File(outDir, outName)
                compressToZip(context, meta, compressionPercent, outFile, onProgress)
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
        compressionPercent: Int,
        maxHeight: Int,
        targetFps: Int,
        useHevc: Boolean,
        outFile: File,
        onProgress: (Float) -> Unit
    ) {
        val keepRatio = (100 - compressionPercent).coerceIn(8, 95) / 100f
        val baseBitrate = if (meta.videoBitrateBps > 100_000) meta.videoBitrateBps else 4_000_000
        val hevcFactor = if (useHevc) 0.72f else 1.0f
        val targetBitrate = (baseBitrate * keepRatio * hevcFactor).roundToInt().coerceIn(250_000, 20_000_000)

        AppLogManager.log(
            "COMPRESS",
            "GPU视频压缩: ${meta.displayName}, bitrate=${targetBitrate / 1000}kbps, h=$maxHeight, fps=$targetFps, hevc=$useHevc"
        )

        val deferred = CompletableDeferred<Unit>()
        val transformer = withContext(Dispatchers.Main) {
            val videoEffects = buildList {
                if (maxHeight > 0) add(Presentation.createForHeight(maxHeight))
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
                        .setBitrate(targetBitrate)
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
        compressionPercent: Int,
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
        val quality = (105 - compressionPercent).coerceIn(10, 95)
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
        compressionPercent: Int,
        outFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val level = (compressionPercent / 11).coerceIn(1, Deflater.BEST_COMPRESSION)
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
