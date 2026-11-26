package com.toolbox.pocket.tools.base64

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

enum class Base64Mode(val label: String) {
    ENCODE("编码 (Encode)"),
    DECODE("解码 (Decode)")
}

enum class Base64Variant(val label: String) {
    STANDARD("标准 Base64"),
    URL_SAFE("URL 安全")
}

enum class LineWrapWidth(val label: String, val width: Int?) {
    NONE("不强制分段", null),
    RFC_76("76 字符/行 (MIME)", 76),
    COL_64("64 字符/行 (PEM)", 64)
}

data class Base64Result(
    val output: String,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val lineCount: Int = 0,
    val charCount: Int = 0,
    val byteCount: Int = 0,
    val isPrettyJson: Boolean = false
)

object Base64Processor {

    fun process(
        input: String,
        mode: Base64Mode,
        variant: Base64Variant,
        wrapWidth: LineWrapWidth,
        autoFormatJson: Boolean,
        preserveParagraphs: Boolean
    ): Base64Result {
        if (input.isEmpty()) {
            return Base64Result(output = "")
        }

        return try {
            when (mode) {
                Base64Mode.ENCODE -> encode(input, variant, wrapWidth, preserveParagraphs)
                Base64Mode.DECODE -> decode(input, variant, autoFormatJson)
            }
        } catch (e: IllegalArgumentException) {
            Base64Result(
                output = "",
                isError = true,
                errorMessage = "解码失败：输入内容不是有效的 Base64 格式（请检查字符或是否选对 URL 安全模式）。"
            )
        } catch (e: Exception) {
            Base64Result(
                output = "",
                isError = true,
                errorMessage = "处理出错：${e.localizedMessage ?: "未知错误"}"
            )
        }
    }

    private fun encode(
        input: String,
        variant: Base64Variant,
        wrapWidth: LineWrapWidth,
        preserveParagraphs: Boolean
    ): Base64Result {
        val flags = when (variant) {
            Base64Variant.STANDARD -> Base64.NO_WRAP
            Base64Variant.URL_SAFE -> Base64.URL_SAFE or Base64.NO_WRAP
        }

        val rawOutput = if (preserveParagraphs && input.contains('\n')) {
            // Preserve user's original paragraph/line structure cleanly when requested
            input.split("\r?\n".toRegex()).joinToString("\n") { line ->
                if (line.isEmpty()) "" else {
                    val encodedLine = Base64.encodeToString(
                        line.toByteArray(StandardCharsets.UTF_8),
                        flags
                    )
                    wrapText(encodedLine, wrapWidth.width)
                }
            }
        } else {
            val encoded = Base64.encodeToString(
                input.toByteArray(StandardCharsets.UTF_8),
                flags
            )
            wrapText(encoded, wrapWidth.width)
        }

        val normalized = normalizeLineEndings(rawOutput)
        return buildStatsResult(normalized)
    }

    private fun decode(
        input: String,
        variant: Base64Variant,
        autoFormatJson: Boolean
    ): Base64Result {
        // Strip whitespace/line breaks inside Base64 blocks so multi-line Base64 decodes cleanly
        val cleaned = input.replace("\s+".toRegex(), "")
        val flags = when (variant) {
            Base64Variant.STANDARD -> Base64.DEFAULT
            Base64Variant.URL_SAFE -> Base64.URL_SAFE
        }

        val decodedBytes = Base64.decode(cleaned, flags)
        var decodedText = normalizeLineEndings(String(decodedBytes, StandardCharsets.UTF_8))

        var formattedJson = false
        if (autoFormatJson) {
            val pretty = tryPrettyJson(decodedText)
            if (pretty != null) {
                decodedText = pretty
                formattedJson = true
            }
        }

        return buildStatsResult(decodedText, isPrettyJson = formattedJson)
    }

    private fun wrapText(text: String, width: Int?): String {
        if (width == null || text.length <= width) return text
        return text.chunked(width).joinToString("\n")
    }

    private fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    private fun tryPrettyJson(text: String): String? {
        val trimmed = text.trim()
        if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]")))
        ) {
            return null
        }
        return runCatching {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(2)
            } else {
                JSONArray(trimmed).toString(2)
            }
        }.getOrNull()
    }

    private fun buildStatsResult(text: String, isPrettyJson: Boolean = false): Base64Result {
        val lines = if (text.isEmpty()) 0 else text.split('\n').size
        val bytes = text.toByteArray(StandardCharsets.UTF_8).size
        return Base64Result(
            output = text,
            lineCount = lines,
            charCount = text.length,
            byteCount = bytes,
            isPrettyJson = isPrettyJson
        )
    }
}
