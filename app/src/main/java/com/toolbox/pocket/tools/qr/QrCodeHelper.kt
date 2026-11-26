package com.toolbox.pocket.tools.qr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.OutputStream

enum class QrDotStyle(val label: String) {
    SQUARE("经典方块"),
    ROUNDED("圆角方块"),
    CIRCLE("圆点")
}

enum class QrEccLevel(val label: String, val level: ErrorCorrectionLevel) {
    L("低 (7%)", ErrorCorrectionLevel.L),
    M("中 (15%)", ErrorCorrectionLevel.M),
    Q("较高 (25%)", ErrorCorrectionLevel.Q),
    H("高 (30%)", ErrorCorrectionLevel.H)
}

object QrCodeHelper {

    fun generateStyledQrBitmap(
        content: String,
        sizePx: Int = 900,
        fgColor: Int = android.graphics.Color.BLACK,
        bgColor: Int = android.graphics.Color.WHITE,
        dotStyle: QrDotStyle = QrDotStyle.SQUARE,
        eccLevel: QrEccLevel = QrEccLevel.M,
        caption: String = ""
    ): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to eccLevel.level,
                EncodeHintType.MARGIN to 2
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
            val mw = matrix.width
            val mh = matrix.height

            val captionHeight = if (caption.isNotBlank()) (sizePx * 0.11f).toInt() else 0
            val bmp = Bitmap.createBitmap(sizePx, sizePx + captionHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(bgColor)

            val padding = sizePx * 0.06f
            val drawArea = sizePx - padding * 2f
            val cellSize = drawArea / mw.toFloat()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fgColor
                style = Paint.Style.FILL
            }

            for (y in 0 until mh) {
                for (x in 0 until mw) {
                    if (matrix.get(x, y)) {
                        val left = padding + x * cellSize
                        val top = padding + y * cellSize
                        val right = left + cellSize
                        val bottom = top + cellSize
                        when (dotStyle) {
                            QrDotStyle.SQUARE -> canvas.drawRect(left, top, right + 0.4f, bottom + 0.4f, paint)
                            QrDotStyle.ROUNDED -> {
                                val inset = cellSize * 0.07f
                                val r = cellSize * 0.28f
                                canvas.drawRoundRect(
                                    RectF(left + inset, top + inset, right - inset, bottom - inset),
                                    r,
                                    r,
                                    paint
                                )
                            }
                            QrDotStyle.CIRCLE -> {
                                val cx = (left + right) / 2f
                                val cy = (top + bottom) / 2f
                                canvas.drawCircle(cx, cy, cellSize * 0.43f, paint)
                            }
                        }
                    }
                }
            }

            if (caption.isNotBlank()) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fgColor
                    textSize = sizePx * 0.044f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val textY = sizePx + captionHeight * 0.55f
                canvas.drawText(caption.take(28), sizePx / 2f, textY, textPaint)
            }

            bmp
        }.getOrNull()
    }

    fun decodeFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            val result = decodeFromBitmap(bmp)
            bmp.recycle()
            result
        }.getOrNull()
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return runCatching {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val source = RGBLuminanceSource(w, h, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            MultiFormatReader().decode(binary, hints).text
        }.getOrNull()
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        return runCatching {
            val filename = "QR_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PocketToolbox")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val out: OutputStream = resolver.openOutputStream(uri) ?: return false
            out.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }
}
