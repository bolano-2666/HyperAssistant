package com.hyper.assistant.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hyper.assistant.network.SmartProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * AI 高清图像生成引擎
 * 采用最新高质量 FLUX / Imagen 神经网络，支持根据中文/英文描述秒级生成 1024x1024 艺术级图像
 */
object ImageGenerator {

    private const val TAG = "ImageGenerator"

    data class GenerationResult(
        val bitmap: Bitmap,
        val imageBytes: ByteArray,
        val prompt: String
    )

    /**
     * 根据文本提示词生成高清图像
     */
    suspend fun generateImage(prompt: String): GenerationResult? = withContext(Dispatchers.IO) {
        try {
            val cleanPrompt = prompt.trim()
            val encodedPrompt = URLEncoder.encode(cleanPrompt, "UTF-8")
            
            // 采用顶级开源多模态 FLUX 生图引擎，免额外计费，秒级渲染 1024x1024 高清大图
            val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&model=flux"
            Log.d(TAG, "发起 AI 绘图请求: $imageUrl")

            val request = Request.Builder()
                .url(imageUrl)
                .get()
                .build()

            val response = SmartProxyManager.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "生图请求失败 HTTP ${response.code}")
                return@withContext null
            }

            val bytes = response.body?.bytes() ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null

            Log.i(TAG, "AI 图像生成成功，大小: ${bytes.size} 字节, 分辨率: ${bitmap.width}x${bitmap.height}")
            GenerationResult(
                bitmap = bitmap,
                imageBytes = bytes,
                prompt = cleanPrompt
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI 绘图发生异常: ${e.message}", e)
            null
        }
    }
}
