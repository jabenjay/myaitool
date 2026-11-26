package com.jaben.tools.tools.format

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.jaben.tools.data.AppLogManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class MediaCategory(val label: String, val pickerMime: String) {
    IMAGE("图片", "image/*"),
    VIDEO("视频", "video/*"),
    AUDIO("音频", "audio/*")
}

enum class TargetFormat(
    val id: String,
    val label: String,
    val ext: String,
    val mime: String,
    val category: MediaCategory,
    val desc: String
) {
    // Image (10 formats)
    IMG_JPG("img_jpg", "JPG", "jpg", "image/jpeg", MediaCategory.IMAGE, "通用压缩"),
    IMG_PNG("img_png", "PNG", "png", "image/png", MediaCategory.IMAGE, "无损透明"),
    IMG_WEBP_LOSSY("img_webp_lossy", "WebP 有损", "webp", "image/webp", MediaCategory.IMAGE, "高压缩比"),
    IMG_WEBP_LOSSLESS("img_webp_lossless", "WebP 无损", "webp", "image/webp", MediaCategory.IMAGE, "无损小体积"),
    IMG_BMP("img_bmp", "BMP", "bmp", "image/bmp", MediaCategory.IMAGE, "位图无压缩"),
    IMG_ICO("img_ico", "ICO", "ico", "image/x-icon", MediaCategory.IMAGE, "网页/桌面图标"),
    IMG_GIF("img_gif", "GIF", "gif", "image/gif", MediaCategory.IMAGE, "256色兼容"),
    IMG_TIFF("img_tiff", "TIFF", "tif", "image/tiff", MediaCategory.IMAGE, "印刷归档"),
    IMG_TGA("img_tga", "TGA", "tga", "image/x-tga", MediaCategory.IMAGE, "游戏贴图"),
    IMG_PPM("img_ppm", "PPM", "ppm", "image/x-portable-pixmap", MediaCategory.IMAGE, "RGB原始图"),

    // Video (6 formats)
    VID_MP4_H264("vid_mp4_h264", "MP4 (H.264)", "mp4", "video/mp4", MediaCategory.VIDEO, "全平台兼容"),
    VID_MP4_HEVC("vid_mp4_hevc", "MP4 (HEVC)", "mp4", "video/mp4", MediaCategory.VIDEO, "H.265高压缩"),
    VID_MOV("vid_mov", "MOV", "mov", "video/quicktime", MediaCategory.VIDEO, "苹果生态封装"),
    VID_GIF("vid_gif", "GIF 动图", "gif", "image/gif", MediaCategory.VIDEO, "表情/动图"),
    VID_TO_M4A("vid_to_m4a", "提取 M4A", "m4a", "audio/mp4", MediaCategory.VIDEO, "提取AAC音轨"),
    VID_TO_WAV("vid_to_wav", "提取 WAV", "wav", "audio/wav", MediaCategory.VIDEO, "提取无损音轨"),

    // Audio (7 formats)
    AUD_M4A("aud_m4a", "M4A (AAC)", "m4a", "audio/mp4", MediaCategory.AUDIO, "主流高效"),
    AUD_AAC("aud_aac", "AAC", "aac", "audio/aac", MediaCategory.AUDIO, "ADTS裸流"),
    AUD_WAV("aud_wav", "WAV", "wav", "audio/wav", MediaCategory.AUDIO, "无损PCM"),
    AUD_AIFF("aud_aiff", "AIFF", "aiff", "audio/aiff", MediaCategory.AUDIO, "苹果无损"),
    AUD_OGG("aud_ogg", "OGG (Opus)", "ogg", "audio/ogg", MediaCategory.AUDIO, "高音质低码率"),
    AUD_3GP("aud_3gp", "3GP (AMR)", "3gp", "audio/3gpp", MediaCategory.AUDIO, "语音极简"),
    AUD_PCM("aud_pcm", "PCM", "pcm", "application/octet-stream", MediaCategory.AUDIO, "原始采样流")
}

data class SourceMediaInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val detailText: String
)

data class ConversionResult(
    val tempFile: File,
    val outputName: String,
    val format: TargetFormat,
    val outputSizeBytes: Long
)

object FormatFactoryEngine {

    fun inspectSource(context: Context, uri: Uri, category: MediaCategory): SourceMediaInfo {
        var name = "未命名文件"
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        val detail = when (category) {
            MediaCategory.IMAGE -> {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth} × ${opts.outHeight}" else "图片文件"
                }.getOrDefault("图片文件")
            }
            MediaCategory.VIDEO, MediaCategory.AUDIO -> {
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, uri)
                    val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    mmr.release()
                    val sec = durMs / 1000
                    val durStr = if (sec >= 60) "${sec / 60}分${sec % 60}秒" else "${sec}秒"
                    if (category == MediaCategory.VIDEO && w > 0 && h > 0) "${w}×${h} · $durStr" else "时长 $durStr"
                }.getOrDefault(if (category == MediaCategory.VIDEO) "视频文件" else "音频文件")
            }
        }
        return SourceMediaInfo(uri = uri, displayName = name, sizeBytes = size, detailText = detail)
    }

    suspend fun convert(
        context: Context,
        source: SourceMediaInfo,
        target: TargetFormat,
        imageQuality: Int = 90,
        imageScalePercent: Int = 100,
        videoMaxHeight: Int = 0, // 0 = original
        audioBitrateKbps: Int = 192,
        onProgress: (Float) -> Unit
    ): ConversionResult {
        val outDir = File(context.cacheDir, "format_factory_out").apply { mkdirs() }
        // Clean previous temp files
        outDir.listFiles()?.forEach { runCatching { it.delete() } }

        val baseName = source.displayName.substringBeforeLast(".").ifBlank { "converted" }
        val outputName = "${baseName}_${System.currentTimeMillis() % 10000}.${target.ext}"
        val outFile = File(outDir, outputName)

        AppLogManager.log("FORMAT", "开始转换: ${source.displayName} -> ${target.label}")

        when (target.category) {
            MediaCategory.IMAGE -> convertImage(
                context, source.uri, target, outFile, imageQuality, imageScalePercent, onProgress
            )
            MediaCategory.VIDEO -> when (target) {
                TargetFormat.VID_GIF -> convertVideoToGif(context, source.uri, outFile, onProgress)
                TargetFormat.VID_TO_M4A -> convertAudio(context, source.uri, TargetFormat.AUD_M4A, outFile, audioBitrateKbps, onProgress)
                TargetFormat.VID_TO_WAV -> convertAudio(context, source.uri, TargetFormat.AUD_WAV, outFile, audioBitrateKbps, onProgress)
                else -> convertVideoWithMedia3(context, source.uri, target, outFile, videoMaxHeight, onProgress)
            }
            MediaCategory.AUDIO -> convertAudio(context, source.uri, target, outFile, audioBitrateKbps, onProgress)
        }

        if (!outFile.exists() || outFile.length() == 0L) {
            throw IllegalStateException("输出文件为空，转换失败")
        }

        AppLogManager.log("FORMAT", "转换完成: $outputName (${outFile.length()} bytes)")
        return ConversionResult(
            tempFile = outFile,
            outputName = outputName,
            format = target,
            outputSizeBytes = outFile.length()
        )
    }

    fun saveToGalleryOrDownloads(context: Context, result: ConversionResult): Uri? {
        val resolver = context.contentResolver
        val isExoticImage = result.format in setOf(
            TargetFormat.IMG_ICO, TargetFormat.IMG_TIFF, TargetFormat.IMG_TGA, TargetFormat.IMG_PPM
        )
        val isExoticAudio = result.format in setOf(
            TargetFormat.AUD_AIFF, TargetFormat.AUD_PCM
        )

        return runCatching {
            val (collection, relPath, mime) = when {
                isExoticImage || isExoticAudio -> {
                    val col = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }
                    Triple(col, Environment.DIRECTORY_DOWNLOADS + "/Jools", "application/octet-stream")
                }
                result.format.mime.startsWith("image/") -> {
                    Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES + "/Jools", result.format.mime)
                }
                result.format.mime.startsWith("video/") -> {
                    Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES + "/Jools", result.format.mime)
                }
                else -> {
                    Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MUSIC + "/Jools", result.format.mime)
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, result.outputName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                result.tempFile.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.onFailure {
            AppLogManager.log("FORMAT", "保存失败: ${it.message}")
        }.getOrNull()
    }

    // ==================== IMAGE ENGINE ====================

    private suspend fun convertImage(
        context: Context,
        uri: Uri,
        target: TargetFormat,
        outFile: File,
        quality: Int,
        scalePercent: Int,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProgress(0.15f)
        var bitmap = decodeSoftwareBitmap(context, uri)
            ?: throw IllegalArgumentException("无法解码该图片格式")

        if (scalePercent in 10..99) {
            val newW = max(1, (bitmap.width * scalePercent / 100f).roundToInt())
            val newH = max(1, (bitmap.height * scalePercent / 100f).roundToInt())
            val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        onProgress(0.45f)

        try {
            FileOutputStream(outFile).use { fos ->
                when (target) {
                    TargetFormat.IMG_JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), fos)
                    TargetFormat.IMG_PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    TargetFormat.IMG_WEBP_LOSSY -> {
                        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                        }
                        bitmap.compress(fmt, quality.coerceIn(10, 100), fos)
                    }
                    TargetFormat.IMG_WEBP_LOSSLESS -> {
                        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSLESS
                        } else {
                            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                        }
                        bitmap.compress(fmt, 100, fos)
                    }
                    TargetFormat.IMG_BMP -> writeBmp(bitmap, fos)
                    TargetFormat.IMG_ICO -> writeIco(bitmap, fos)
                    TargetFormat.IMG_GIF -> writeGif89a(listOf(bitmap), 0, fos)
                    TargetFormat.IMG_TIFF -> writeTiff(bitmap, fos)
                    TargetFormat.IMG_TGA -> writeTga(bitmap, fos)
                    TargetFormat.IMG_PPM -> writePpm(bitmap, fos)
                    else -> throw IllegalArgumentException("不支持的图片格式")
                }
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        onProgress(1f)
    }

    private fun decodeSoftwareBitmap(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)?.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
    }

    private fun writeBmp(bitmap: Bitmap, out: OutputStream) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rowPad = (4 - (w * 3) % 4) % 4
        val rowStride = w * 3 + rowPad
        val imgSize = rowStride * h
        val fileSize = 54 + imgSize

        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte()); put('M'.code.toByte())
            putInt(fileSize)
            putInt(0)
            putInt(54)
            putInt(40)
            putInt(w)
            putInt(h)
            putShort(1)
            putShort(24)
            putInt(0)
            putInt(imgSize)
            putInt(2835)
            putInt(2835)
            putInt(0)
            putInt(0)
        }
        out.write(header.array())
        val rowBuf = ByteArray(rowStride)
        for (y in h - 1 downTo 0) {
            var idx = 0
            val rowOffset = y * w
            for (x in 0 until w) {
                val c = pixels[rowOffset + x]
                rowBuf[idx++] = (c and 0xFF).toByte()        // B
                rowBuf[idx++] = ((c shr 8) and 0xFF).toByte() // G
                rowBuf[idx++] = ((c shr 16) and 0xFF).toByte()// R
            }
            for (p in 0 until rowPad) rowBuf[idx++] = 0
            out.write(rowBuf)
        }
    }

    private fun writeIco(bitmap: Bitmap, out: OutputStream) {
        val maxDim = 256
        val ratio = min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height).coerceAtMost(1f)
        val w = max(1, (bitmap.width * ratio).roundToInt())
        val h = max(1, (bitmap.height * ratio).roundToInt())
        val iconBmp = if (w != bitmap.width || h != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else bitmap

        val pngBytes = ByteArrayOutputStream().use { baos ->
            iconBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
        if (iconBmp != bitmap) iconBmp.recycle()

        val header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0) // Reserved
            putShort(1) // ICO type
            putShort(1) // 1 image
            put(if (w >= 256) 0 else w.toByte())
            put(if (h >= 256) 0 else h.toByte())
            put(0) // palette
            put(0) // reserved
            putShort(1) // planes
            putShort(32) // bpp
            putInt(pngBytes.size)
            putInt(22) // offset
        }
        out.write(header.array())
        out.write(pngBytes)
    }

    private fun writeTga(bitmap: Bitmap, out: OutputStream) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val header = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0); put(0); put(2) // Uncompressed true-color
            putShort(0); putShort(0); put(0)
            putShort(0); putShort(0)
            putShort(w.toShort()); putShort(h.toShort())
            put(32) // BGRA 32-bit
            put(0x28.toByte()) // Top-left origin + 8 alpha bits
        }
        out.write(header.array())
        val rowBuf = ByteArray(w * 4)
        for (y in 0 until h) {
            var idx = 0
            val off = y * w
            for (x in 0 until w) {
                val c = pixels[off + x]
                rowBuf[idx++] = (c and 0xFF).toByte()
                rowBuf[idx++] = ((c shr 8) and 0xFF).toByte()
                rowBuf[idx++] = ((c shr 16) and 0xFF).toByte()
                rowBuf[idx++] = ((c ushr 24) and 0xFF).toByte()
            }
            out.write(rowBuf)
        }
    }

    private fun writePpm(bitmap: Bitmap, out: OutputStream) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        out.write("P6\n$w $h\n255\n".toByteArray(Charsets.US_ASCII))
        val rowBuf = ByteArray(w * 3)
        for (y in 0 until h) {
            var idx = 0
            val off = y * w
            for (x in 0 until w) {
                val c = pixels[off + x]
                rowBuf[idx++] = ((c shr 16) and 0xFF).toByte()
                rowBuf[idx++] = ((c shr 8) and 0xFF).toByte()
                rowBuf[idx++] = (c and 0xFF).toByte()
            }
            out.write(rowBuf)
        }
    }

    private fun writeTiff(bitmap: Bitmap, out: OutputStream) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val stripSize = w * h * 4
        val ifdOffset = 8 + stripSize

        // Little-endian TIFF header
        val hdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('I'.code.toByte()); put('I'.code.toByte())
            putShort(42)
            putInt(ifdOffset)
        }
        out.write(hdr.array())

        val rowBuf = ByteArray(w * 4)
        for (y in 0 until h) {
            var idx = 0
            val off = y * w
            for (x in 0 until w) {
                val c = pixels[off + x]
                rowBuf[idx++] = ((c shr 16) and 0xFF).toByte() // R
                rowBuf[idx++] = ((c shr 8) and 0xFF).toByte()  // G
                rowBuf[idx++] = (c and 0xFF).toByte()          // B
                rowBuf[idx++] = ((c ushr 24) and 0xFF).toByte()// A
            }
            out.write(rowBuf)
        }

        // Extra values area after IFD: BitsPerSample (4 shorts = 8 bytes)
        val extraOffset = ifdOffset + 2 + 10 * 12 + 4
        val ifd = ByteBuffer.allocate(2 + 10 * 12 + 4 + 8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(10) // 10 tags
            fun tag(id: Int, type: Int, count: Int, value: Int) {
                putShort(id.toShort()); putShort(type.toShort()); putInt(count); putInt(value)
            }
            tag(256, 4, 1, w)                 // ImageWidth
            tag(257, 4, 1, h)                 // ImageLength
            tag(258, 3, 4, extraOffset)       // BitsPerSample (8,8,8,8)
            tag(259, 3, 1, 1)                 // Compression = None
            tag(262, 3, 1, 2)                 // Photometric = RGB
            tag(273, 4, 1, 8)                 // StripOffsets
            tag(277, 3, 1, 4)                 // SamplesPerPixel = 4
            tag(278, 4, 1, h)                 // RowsPerStrip
            tag(279, 4, 1, stripSize)         // StripByteCounts
            tag(338, 3, 1, 2)                 // ExtraSamples = Unassociated alpha
            putInt(0)                         // Next IFD
            putShort(8); putShort(8); putShort(8); putShort(8)
        }
        out.write(ifd.array())
    }

    // ==================== GIF89a LZW ENCODER ====================

    private fun writeGif89a(frames: List<Bitmap>, delayCs: Int, out: OutputStream) {
        val first = frames.first()
        val w = first.width
        val h = first.height

        // Build 256-color global palette: 216 web-safe RGB (6x6x6) + 40 grayscale
        val palette = ByteArray(256 * 3)
        var pIdx = 0
        for (r in 0 until 6) {
            for (g in 0 until 6) {
                for (b in 0 until 6) {
                    palette[pIdx++] = (r * 51).toByte()
                    palette[pIdx++] = (g * 51).toByte()
                    palette[pIdx++] = (b * 51).toByte()
                }
            }
        }
        for (i in 0 until 40) {
            val v = (i * 255 / 39).toByte()
            palette[pIdx++] = v; palette[pIdx++] = v; palette[pIdx++] = v
        }

        // Header + Logical Screen Descriptor
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        val lsd = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(w.toShort())
            putShort(h.toShort())
            put(0xF7.toByte()) // Global palette, 8-bit, 256 colors
            put(0)             // Background index
            put(0)             // Aspect ratio
        }
        out.write(lsd.array())
        out.write(palette)

        if (frames.size > 1) {
            // Netscape 2.0 looping block
            out.write(byteArrayOf(
                0x21, 0xFF.toByte(), 0x0B,
                'N'.code.toByte(), 'E'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(),
                'C'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 'E'.code.toByte(),
                '2'.code.toByte(), '.'.code.toByte(), '0'.code.toByte(),
                0x03, 0x01, 0x00, 0x00, 0x00
            ))
        }

        val pixels = IntArray(w * h)
        val indexed = ByteArray(w * h)

        for (bmp in frames) {
            val frameBmp = if (bmp.width != w || bmp.height != h) {
                Bitmap.createScaledBitmap(bmp, w, h, true)
            } else bmp

            frameBmp.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val ri = (r * 5 + 127) / 255
                val gi = (g * 5 + 127) / 255
                val bi = (b * 5 + 127) / 255
                indexed[i] = (ri * 36 + gi * 6 + bi).toByte()
            }
            if (frameBmp != bmp) frameBmp.recycle()

            if (frames.size > 1) {
                val gce = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put(0x21); put(0xF9.toByte()); put(0x04)
                    put(0x04) // Dispose: do not dispose
                    putShort(delayCs.toShort())
                    put(0); put(0)
                }
                out.write(gce.array())
            }

            val imgDesc = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(0x2C)
                putShort(0); putShort(0)
                putShort(w.toShort()); putShort(h.toShort())
                put(0)
            }
            out.write(imgDesc.array())
            writeGifLzw(indexed, out)
        }
        out.write(0x3B) // GIF Trailer
    }

    private fun writeGifLzw(indexed: ByteArray, out: OutputStream) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 256
        val eofCode = 257
        var nextCode = 258
        var codeSize = 9
        val dict = HashMap<Int, Int>(4096)

        val blockBuf = ByteArray(256)
        var blockLen = 0
        var bitAccum = 0
        var bitCount = 0

        fun emitBits(code: Int, size: Int) {
            bitAccum = bitAccum or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                blockBuf[blockLen++] = (bitAccum and 0xFF).toByte()
                bitAccum = bitAccum ushr 8
                bitCount -= 8
                if (blockLen == 255) {
                    out.write(255)
                    out.write(blockBuf, 0, 255)
                    blockLen = 0
                }
            }
        }

        emitBits(clearCode, codeSize)
        var prefix = indexed[0].toInt() and 0xFF

        for (i in 1 until indexed.size) {
            val k = indexed[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
            } else {
                emitBits(prefix, codeSize)
                if (nextCode < 4096) {
                    dict[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    emitBits(clearCode, codeSize)
                    dict.clear()
                    nextCode = 258
                    codeSize = 9
                }
                prefix = k
            }
        }
        emitBits(prefix, codeSize)
        emitBits(eofCode, codeSize)

        if (bitCount > 0) {
            blockBuf[blockLen++] = (bitAccum and 0xFF).toByte()
        }
        if (blockLen > 0) {
            out.write(blockLen)
            out.write(blockBuf, 0, blockLen)
        }
        out.write(0) // Block terminator
    }

    // ==================== VIDEO ENGINE ====================

    @OptIn(UnstableApi::class)
    private suspend fun convertVideoWithMedia3(
        context: Context,
        uri: Uri,
        target: TargetFormat,
        outFile: File,
        maxHeight: Int,
        onProgress: (Float) -> Unit
    ) {
        val videoMime = when (target) {
            TargetFormat.VID_MP4_HEVC -> MimeTypes.VIDEO_H265
            else -> MimeTypes.VIDEO_H264
        }

        val deferred = CompletableDeferred<Unit>()
        val transformer = withContext(Dispatchers.Main) {
            val effects = if (maxHeight > 0) {
                Effects(emptyList(), listOf(Presentation.createForHeight(maxHeight)))
            } else {
                Effects.EMPTY
            }
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(effects)
                .build()

            val t = Transformer.Builder(context)
                .setVideoMimeType(videoMime)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
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
                    delay(120)
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

    private suspend fun convertVideoToGif(
        context: Context,
        uri: Uri,
        outFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            mmr.setDataSource(context, uri)
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
            val maxDurMs = min(durMs, 15_000L) // Cap at 15s for mobile RAM safety
            val fps = 8
            val stepMs = 1000L / fps
            val totalFrames = max(1, (maxDurMs / stepMs).toInt()).coerceAtMost(120)

            for (i in 0 until totalFrames) {
                if (!coroutineContext.isActive) break
                val timeUs = i * stepMs * 1000L
                val raw = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: mmr.getFrameAtTime(timeUs)
                if (raw != null) {
                    val scale = min(360f / raw.width, 360f / raw.height).coerceAtMost(1f)
                    val w = max(1, (raw.width * scale).roundToInt())
                    val h = max(1, (raw.height * scale).roundToInt())
                    val resized = Bitmap.createScaledBitmap(raw, w, h, true)
                    if (resized != raw) raw.recycle()
                    frames.add(resized)
                }
                onProgress((i + 1).toFloat() / totalFrames * 0.75f)
            }
            if (frames.isEmpty()) throw IllegalStateException("无法从视频中提取画面")
            FileOutputStream(outFile).use { fos ->
                writeGif89a(frames, 100 / fps, fos)
            }
            onProgress(1f)
        } finally {
            frames.forEach { if (!it.isRecycled) it.recycle() }
            runCatching { mmr.release() }
        }
    }

    // ==================== AUDIO ENGINE ====================

    private suspend fun convertAudio(
        context: Context,
        uri: Uri,
        target: TargetFormat,
        outFile: File,
        bitrateKbps: Int,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (target == TargetFormat.AUD_OGG && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IllegalStateException("OGG (Opus) 编码需要 Android 10 及以上系统")
        }

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IllegalStateException("未在文件中找到有效音轨")
            }
            extractor.selectTrack(audioTrackIndex)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Decode entire audio to a temp PCM file first (streaming, zero OOM)
            val tempPcm = File(outFile.parentFile, "tmp_${System.nanoTime()}.pcm")
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var decodeDone = false

            FileOutputStream(tempPcm).use { pcmOut ->
                while (!decodeDone && coroutineContext.isActive) {
                    if (!inputDone) {
                        val inIdx = decoder.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = decoder.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                                if (durationUs > 0) {
                                    onProgress((pts.toFloat() / durationUs * 0.45f).coerceIn(0.02f, 0.45f))
                                }
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = decoder.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        outIdx >= 0 -> {
                            if (info.size > 0) {
                                val outBuf = decoder.getOutputBuffer(outIdx)!!
                                val bytes = ByteArray(info.size)
                                outBuf.position(info.offset)
                                outBuf.get(bytes)
                                pcmOut.write(bytes)
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                decodeDone = true
                            }
                        }
                    }
                }
            }

            decoder.stop(); decoder.release(); decoder = null
            extractor.release()

            val pcmSize = tempPcm.length()
            if (pcmSize == 0L) throw IllegalStateException("音频解码结果为空")
            onProgress(0.5f)

            when (target) {
                TargetFormat.AUD_PCM -> {
                    tempPcm.copyTo(outFile, overwrite = true)
                }
                TargetFormat.AUD_WAV -> {
                    FileOutputStream(outFile).use { fos ->
                        fos.write(buildWavHeader(pcmSize, sampleRate, channelCount))
                        tempPcm.inputStream().use { it.copyTo(fos) }
                    }
                }
                TargetFormat.AUD_AIFF -> {
                    FileOutputStream(outFile).use { fos ->
                        fos.write(buildAiffHeader(pcmSize, sampleRate, channelCount))
                        val buf = ByteArray(8192)
                        tempPcm.inputStream().use { fis ->
                            while (true) {
                                val read = fis.read(buf)
                                if (read <= 0) break
                                // Swap 16-bit LE -> BE
                                var i = 0
                                while (i + 1 < read) {
                                    val tmp = buf[i]
                                    buf[i] = buf[i + 1]
                                    buf[i + 1] = tmp
                                    i += 2
                                }
                                fos.write(buf, 0, read)
                            }
                        }
                    }
                }
                TargetFormat.AUD_M4A, TargetFormat.AUD_AAC, TargetFormat.AUD_OGG, TargetFormat.AUD_3GP -> {
                    encodePcmWithMediaCodec(
                        pcmFile = tempPcm,
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                        target = target,
                        outFile = outFile,
                        bitrateKbps = bitrateKbps,
                        onProgress = { encP -> onProgress(0.5f + encP * 0.5f) }
                    )
                }
                else -> throw IllegalArgumentException("不支持的音频目标格式")
            }

            tempPcm.delete()
            onProgress(1f)
        } finally {
            runCatching { decoder?.stop(); decoder?.release() }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { muxer?.stop(); muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private suspend fun encodePcmWithMediaCodec(
        pcmFile: File,
        sampleRate: Int,
        channelCount: Int,
        target: TargetFormat,
        outFile: File,
        bitrateKbps: Int,
        onProgress: (Float) -> Unit
    ) {
        val (encMime, muxerFormat, useRawAdts) = when (target) {
            TargetFormat.AUD_M4A -> Triple(MediaFormat.MIMETYPE_AUDIO_AAC, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, false)
            TargetFormat.AUD_AAC -> Triple(MediaFormat.MIMETYPE_AUDIO_AAC, -1, true)
            TargetFormat.AUD_OGG -> Triple(MediaFormat.MIMETYPE_AUDIO_OPUS, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, false)
            TargetFormat.AUD_3GP -> Triple(MediaFormat.MIMETYPE_AUDIO_AMR_WB, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP, false)
            else -> throw IllegalArgumentException("不支持的编码格式")
        }

        // Normalize sample rate/channels if target codec has strict constraints
        val targetRate = when (target) {
            TargetFormat.AUD_3GP -> 16000
            TargetFormat.AUD_OGG -> 48000
            else -> sampleRate
        }
        val targetChannels = if (target == TargetFormat.AUD_3GP) 1 else channelCount.coerceIn(1, 2)

        val preparedPcm = if (targetRate != sampleRate || targetChannels != channelCount) {
            val resampled = File(pcmFile.parentFile, "resamp_${System.nanoTime()}.pcm")
            resampleLinear16Bit(pcmFile, sampleRate, channelCount, resampled, targetRate, targetChannels)
            resampled
        } else pcmFile

        val encoder = MediaCodec.createEncoderByType(encMime)
        val encFormat = MediaFormat.createAudioFormat(encMime, targetRate, targetChannels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, if (target == TargetFormat.AUD_3GP) 23850 else bitrateKbps * 1000)
            if (encMime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = if (!useRawAdts) MediaMuxer(outFile.absolutePath, muxerFormat) else null
        val adtsOut = if (useRawAdts) FileOutputStream(outFile) else null
        var muxerTrack = -1
        var muxerStarted = false

        val totalBytes = preparedPcm.length().coerceAtLeast(1L)
        var bytesReadTotal = 0L
        val bytesPerSec = targetRate * targetChannels * 2L
        val readBuf = ByteArray(4096)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var encodeDone = false

        try {
            preparedPcm.inputStream().use { fis ->
                while (!encodeDone && coroutineContext.isActive) {
                    if (!inputDone) {
                        val inIdx = encoder.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = encoder.getInputBuffer(inIdx)!!
                            buf.clear()
                            val maxRead = min(readBuf.size, buf.remaining())
                            val read = fis.read(readBuf, 0, maxRead)
                            if (read < 0) {
                                val ptsUs = bytesReadTotal * 1_000_000L / bytesPerSec
                                encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val ptsUs = bytesReadTotal * 1_000_000L / bytesPerSec
                                buf.put(readBuf, 0, read)
                                encoder.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                                bytesReadTotal += read
                                onProgress((bytesReadTotal.toFloat() / totalBytes).coerceIn(0f, 0.98f))
                            }
                        }
                    }

                    val outIdx = encoder.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxer != null && !muxerStarted) {
                                muxerTrack = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(outIdx)!!
                            val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig && info.size > 0) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                if (useRawAdts && adtsOut != null) {
                                    val packet = ByteArray(info.size)
                                    outBuf.get(packet)
                                    adtsOut.write(buildAdtsHeader(info.size, targetRate, targetChannels))
                                    adtsOut.write(packet)
                                } else if (muxer != null && muxerStarted) {
                                    muxer.writeSampleData(muxerTrack, outBuf, info)
                                }
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encodeDone = true
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { adtsOut?.close() }
            runCatching { encoder.stop(); encoder.release() }
            runCatching { if (muxerStarted) muxer?.stop(); muxer?.release() }
            if (preparedPcm != pcmFile) preparedPcm.delete()
        }
    }

    private fun resampleLinear16Bit(
        srcFile: File,
        srcRate: Int,
        srcCh: Int,
        dstFile: File,
        dstRate: Int,
        dstCh: Int
    ) {
        val raw = srcFile.readBytes()
        val srcSamples = raw.size / (2 * srcCh)
        if (srcSamples <= 0) return
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val srcFloats = ShortArray(srcSamples * srcCh)
        for (i in srcFloats.indices) srcFloats[i] = bb.short

        val dstSamples = max(1, (srcSamples.toLong() * dstRate / srcRate).toInt())
        val outBuf = ByteBuffer.allocate(dstSamples * dstCh * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until dstSamples) {
            val srcPos = i.toDouble() * srcRate / dstRate
            val idx0 = srcPos.toInt().coerceIn(0, srcSamples - 1)
            val idx1 = (idx0 + 1).coerceIn(0, srcSamples - 1)
            val frac = srcPos - idx0
            for (c in 0 until dstCh) {
                val sc = if (srcCh == 1) 0 else min(c, srcCh - 1)
                val v0 = if (dstCh == 1 && srcCh == 2) {
                    (srcFloats[idx0 * 2] + srcFloats[idx0 * 2 + 1]) * 0.5
                } else {
                    srcFloats[idx0 * srcCh + sc].toDouble()
                }
                val v1 = if (dstCh == 1 && srcCh == 2) {
                    (srcFloats[idx1 * 2] + srcFloats[idx1 * 2 + 1]) * 0.5
                } else {
                    srcFloats[idx1 * srcCh + sc].toDouble()
                }
                val sample = (v0 + (v1 - v0) * frac).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                outBuf.putShort(sample.toShort())
            }
        }
        FileOutputStream(dstFile).use { it.write(outBuf.array()) }
    }

    private fun buildWavHeader(pcmBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + pcmBytes).toInt())
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.toInt())
        }.array()
    }

    private fun buildAiffHeader(pcmBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val numFrames = (pcmBytes / (channels * 2)).toInt()
        return ByteBuffer.allocate(54).order(ByteOrder.BIG_ENDIAN).apply {
            put("FORM".toByteArray(Charsets.US_ASCII))
            putInt((46 + pcmBytes).toInt())
            put("AIFF".toByteArray(Charsets.US_ASCII))
            put("COMM".toByteArray(Charsets.US_ASCII))
            putInt(18)
            putShort(channels.toShort())
            putInt(numFrames)
            putShort(16)
            // 80-bit IEEE 754 extended float for sampleRate
            put(encodeIeee80(sampleRate))
            put("SSND".toByteArray(Charsets.US_ASCII))
            putInt((8 + pcmBytes).toInt())
            putInt(0)
            putInt(0)
        }.array()
    }

    private fun encodeIeee80(rate: Int): ByteArray {
        val out = ByteArray(10)
        if (rate <= 0) return out
        var exp = 16383 + 31
        var mantissa = rate.toLong()
        while ((mantissa and 0x80000000L) == 0L) {
            mantissa = mantissa shl 1
            exp--
        }
        out[0] = ((exp shr 8) and 0x7F).toByte()
        out[1] = (exp and 0xFF).toByte()
        out[2] = ((mantissa shr 24) and 0xFF).toByte()
        out[3] = ((mantissa shr 16) and 0xFF).toByte()
        out[4] = ((mantissa shr 8) and 0xFF).toByte()
        out[5] = (mantissa and 0xFF).toByte()
        return out
    }

    private fun buildAdtsHeader(packetLen: Int, sampleRate: Int, channels: Int): ByteArray {
        val freqIdx = when (sampleRate) {
            96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3; 44100 -> 4; 32000 -> 5
            24000 -> 6; 22050 -> 7; 16000 -> 8; 12000 -> 9; 11025 -> 10; 8000 -> 11
            else -> 4
        }
        val frameLen = packetLen + 7
        return byteArrayOf(
            0xFF.toByte(),
            0xF9.toByte(),
            (((2 - 1) shl 6) + (freqIdx shl 2) + (channels shr 2)).toByte(),
            (((channels and 3) shl 6) + (frameLen shr 11)).toByte(),
            ((frameLen and 0x7FF) shr 3).toByte(),
            (((frameLen and 7) shl 5) + 0x1F).toByte(),
            0xFC.toByte()
        )
    }
}
