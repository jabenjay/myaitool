package com.jaben.tools.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object OutputStorageHelper {
    const val SUB_PICTURES = "pictures"
    const val SUB_VIDEOS = "videos"
    const val SUB_SOUNDS = "sounds"
    const val SUB_FILES = "files"

    fun getDisplayPath(context: Context, subFolder: String): String {
        val settings = ThemePreferences(context).settings.value
        val rootLabel = settings.outputRootLabel.ifBlank { "Download/Jools" }
        return "$rootLabel/$subFolder"
    }

    fun saveFile(
        context: Context,
        subFolder: String,
        fileName: String,
        mimeType: String,
        sourceFile: File
    ): Uri? {
        return FileInputStream(sourceFile).use { fis ->
            saveStream(context, subFolder, fileName, mimeType, fis)
        }
    }

    fun saveStream(
        context: Context,
        subFolder: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): Uri? {
        val prefs = ThemePreferences(context)
        val rootUriStr = prefs.settings.value.outputRootUri

        // 1. Try user-selected custom SAF folder first
        if (!rootUriStr.isNullOrBlank()) {
            val savedCustom = runCatching {
                val rootUri = Uri.parse(rootUriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@runCatching null
                if (!rootDoc.canWrite()) return@runCatching null
                val subDoc = rootDoc.findFile(subFolder)?.takeIf { it.isDirectory }
                    ?: rootDoc.createDirectory(subFolder)
                    ?: return@runCatching null
                // Remove existing file with same name if present
                subDoc.findFile(fileName)?.delete()
                val targetDoc = subDoc.createFile("application/octet-stream", fileName)
                    ?: subDoc.createFile(mimeType, fileName)
                    ?: return@runCatching null
                context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                    inputStream.copyTo(out)
                } ?: return@runCatching null
                targetDoc.uri
            }.onFailure {
                AppLogManager.log("STORAGE", "自定义目录写入失败，回退默认路径: ${it.message}")
            }.getOrNull()

            if (savedCustom != null) return savedCustom
        }

        // 2. Default: Download/Jools/<subFolder>
        return saveToDefaultDownloadJools(context, subFolder, fileName, mimeType, inputStream)
    }

    private fun saveToDefaultDownloadJools(
        context: Context,
        subFolder: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): Uri? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relPath = "${Environment.DIRECTORY_DOWNLOADS}/Jools/$subFolder"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    inputStream.copyTo(out)
                } ?: return null
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(baseDir, "Jools/$subFolder").apply { mkdirs() }
                val outFile = File(targetDir, fileName)
                FileOutputStream(outFile).use { out ->
                    inputStream.copyTo(out)
                }
                Uri.fromFile(outFile)
            }
        }.onFailure {
            AppLogManager.log("STORAGE", "默认目录写入失败: ${it.message}")
        }.getOrNull()
    }
}
