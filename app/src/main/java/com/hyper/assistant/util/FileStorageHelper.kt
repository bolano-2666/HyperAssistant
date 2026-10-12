package com.hyper.assistant.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 文件与图片持久化及读取工具库
 * 遵循 Android Scoped Storage 原生规范，无感存取图片至相册、文档至下载目录
 */
object FileStorageHelper {

    private const val TAG = "FileStorageHelper"

    data class SelectedFileInfo(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val isImage: Boolean,
        val isPdf: Boolean,
        val base64Data: String?,
        val textContent: String?
    )

    /**
     * 将字节数组保存为图片到系统相册 (Pictures/HyperAssistant)
     */
    suspend fun saveImageToGallery(context: Context, imageBytes: ByteArray, title: String = "AI_Generated"): Uri? = withContext(Dispatchers.IO) {
        try {
            val filename = "Hyper_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HyperAssistant")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null

            resolver.openOutputStream(uri)?.use { output ->
                output.write(imageBytes)
                output.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Log.i(TAG, "图片已成功保存到系统相册: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "保存图片到相册失败: ${e.message}", e)
            null
        }
    }

    /**
     * 将文本内容保存为文档至手机系统下载目录 (Download/HyperAssistant)
     */
    suspend fun saveTextToDownloads(context: Context, content: String, defaultName: String = "AI_Report"): Uri? = withContext(Dispatchers.IO) {
        try {
            val extension = if (content.contains("# ") || content.contains("```")) "md" else "txt"
            val filename = "${defaultName}_${System.currentTimeMillis()}.$extension"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, if (extension == "md") "text/markdown" else "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HyperAssistant")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, values) ?: return@withContext null

            resolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Log.i(TAG, "文档已成功导出至下载目录: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "导出文档失败: ${e.message}", e)
            null
        }
    }

    /**
     * 解析用户通过 SAF 选中的 Uri 文件流
     */
    suspend fun parseSelectedUri(context: Context, uri: Uri): SelectedFileInfo? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            var fileName = "unknown_file"
            var fileSize = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val mimeType = resolver.getType(uri) ?: when {
                fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                fileName.endsWith(".png", true) -> "image/png"
                fileName.endsWith(".webp", true) -> "image/webp"
                fileName.endsWith(".pdf", true) -> "application/pdf"
                fileName.endsWith(".txt", true) -> "text/plain"
                fileName.endsWith(".md", true) -> "text/markdown"
                fileName.endsWith(".json", true) -> "application/json"
                fileName.endsWith(".csv", true) -> "text/csv"
                else -> "application/octet-stream"
            }

            val isImage = mimeType.startsWith("image/")
            val isPdf = mimeType == "application/pdf"

            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val textContent = if (!isImage && !isPdf) {
                    try {
                        String(bytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                SelectedFileInfo(
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                    isImage = isImage,
                    isPdf = isPdf,
                    base64Data = base64,
                    textContent = textContent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析选中文件失败: ${e.message}", e)
            null
        }
    }
}
