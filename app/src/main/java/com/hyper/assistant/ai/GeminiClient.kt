package com.hyper.assistant.ai

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hyper.assistant.network.SmartProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Google Gemini 3 系列流式通信客户端
 * 默认支持 gemini-3.1-pro / gemini-3-flash / gemini-2.5-flash
 * 自动适配免科学上网反向代理与流式 SSE 逐字返回
 */
object GeminiClient {

    private const val TAG = "GeminiClient"

    suspend fun streamChat(
        context: Context,
        apiKey: String,
        modelName: String,
        prompt: String,
        imageBase64: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        val baseUrl = SmartProxyManager.getGeminiBaseUrl(context).trimEnd('/')
        val model = if (modelName.isNotBlank()) modelName else "gemini-3.1-pro"
        val requestUrl = "$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"

        Log.d(TAG, "发起 Gemini 请求: $requestUrl, Model: $model")

        // 构建请求体
        val requestJson = JsonObject().apply {
            val contentsArray = com.google.gson.JsonArray()
            val contentObj = JsonObject()
            contentObj.addProperty("role", "user")

            val partsArray = com.google.gson.JsonArray()

            // 文本问题
            val textPart = JsonObject()
            textPart.addProperty("text", prompt)
            partsArray.add(textPart)

            // 多模态截图支持
            if (!imageBase64.isNullOrEmpty()) {
                val imagePart = JsonObject()
                val inlineData = JsonObject()
                inlineData.addProperty("mime_type", "image/jpeg")
                inlineData.addProperty("data", imageBase64)
                imagePart.add("inline_data", inlineData)
                partsArray.add(imagePart)
            }

            contentObj.add("parts", partsArray)
            contentsArray.add(contentObj)
            add("contents", contentsArray)

            // 生成参数
            val config = JsonObject()
            config.addProperty("temperature", 0.7)
            config.addProperty("maxOutputTokens", 1024)
            add("generationConfig", config)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .build()

        val fullResponse = StringBuilder()

        try {
            val response = SmartProxyManager.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val errMsg = "Gemini 请求失败 HTTP ${response.code}: $errBody"
                Log.e(TAG, errMsg)
                withContext(Dispatchers.Main) {
                    onError(RuntimeException(errMsg))
                }
                return@withContext
            }

            val inputStream = response.body?.byteStream() ?: throw IllegalStateException("空响应流")
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim().orEmpty()
                if (currentLine.startsWith("data:")) {
                    val dataJson = currentLine.removePrefix("data:").trim()
                    if (dataJson.isNotEmpty()) {
                        try {
                            val jsonObj = JsonParser.parseString(dataJson).asJsonObject
                            val candidates = jsonObj.getAsJsonArray("candidates")
                            if (candidates != null && candidates.size() > 0) {
                                val content = candidates[0].asJsonObject.getAsJsonObject("content")
                                val parts = content?.getAsJsonArray("parts")
                                if (parts != null && parts.size() > 0) {
                                    val text = parts[0].asJsonObject.get("text")?.asString.orEmpty()
                                    if (text.isNotEmpty()) {
                                        fullResponse.append(text)
                                        withContext(Dispatchers.Main) {
                                            onChunk(text)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "解析 SSE 分块失败: ${e.message}")
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(fullResponse.toString())
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Gemini 连接异常: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError(e)
            }
        }
    }
}
