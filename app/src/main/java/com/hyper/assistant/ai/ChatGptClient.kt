package com.hyper.assistant.ai

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
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
 * OpenAI ChatGPT (GPT-4o / o3-mini) 流式通信客户端
 * 优化 endpoint 路径拼接，完美兼容官方与各类反代镜像站
 */
object ChatGptClient {

    private const val TAG = "ChatGptClient"

    suspend fun streamChat(
        context: Context,
        apiKey: String,
        modelName: String,
        prompt: String,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        val baseUrl = SmartProxyManager.getOpenAiBaseUrl(context).trimEnd('/')
        val model = if (modelName.isNotBlank()) modelName else "gpt-4o"
        
        // 智能兼容反代自带 /v1 后缀
        val requestUrl = if (baseUrl.endsWith("/v1")) {
            "$baseUrl/chat/completions"
        } else {
            "$baseUrl/v1/chat/completions"
        }

        val effectivePrompt = if (prompt.isNotBlank()) prompt else "你好"
        Log.d(TAG, "发起 ChatGPT 请求: $requestUrl, Model: $model")

        val requestJson = JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", true)

            val messages = JsonArray()
            val msg = JsonObject()
            msg.addProperty("role", "user")
            msg.addProperty("content", effectivePrompt)
            messages.add(msg)

            add("messages", messages)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .build()

        val fullResponse = StringBuilder()

        try {
            val response = SmartProxyManager.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val errMsg = "OpenAI 请求失败 HTTP ${response.code}: $errBody"
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
                    if (dataJson == "[DONE]") {
                        break
                    }
                    if (dataJson.isNotEmpty()) {
                        try {
                            val jsonObj = JsonParser.parseString(dataJson).asJsonObject
                            val choices = jsonObj.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                val text = delta?.get("content")?.asString.orEmpty()
                                if (text.isNotEmpty()) {
                                    fullResponse.append(text)
                                    withContext(Dispatchers.Main) {
                                        onChunk(text)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "解析 ChatGPT SSE 异常: ${e.message}")
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(fullResponse.toString())
            }

        } catch (e: Throwable) {
            Log.e(TAG, "ChatGPT 连接异常: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError(e)
            }
        }
    }
}
