package com.hyper.assistant.ai

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hyper.assistant.network.SmartProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Google Gemini 原生多模态（语音+视觉+文档+文本）流式通信客户端
 * 原生支持音频输入 (audio/wav)、文档 (application/pdf)、图像及文本文件解析
 * 默认搭载官方最新的 gemini-3.8-flash，支持 503/429 自动避峰重试
 */
object GeminiClient {

    private const val TAG = "GeminiClient"

    suspend fun streamChat(
        context: Context,
        apiKey: String,
        modelName: String,
        prompt: String,
        imageBase64: String? = null,
        audioBase64: String? = null,
        fileBase64: String? = null,
        fileMimeType: String? = null,
        documentText: String? = null,
        onStatusUpdate: (String) -> Unit = {},
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        val baseUrl = SmartProxyManager.getGeminiBaseUrl(context).trimEnd('/')
        var activeModel = if (modelName.isNotBlank()) modelName else "gemini-3.8-flash"
        val fullResponse = StringBuilder()

        // 最多重试 2 次 (若遇到 503 High Demand 峰值拥堵，自动降级至备用稳定模型)
        val maxAttempts = 2
        var attempt = 0

        while (attempt < maxAttempts) {
            attempt++
            val requestUrl = "$baseUrl/v1beta/models/$activeModel:streamGenerateContent?alt=sse&key=$apiKey"
            Log.d(TAG, "发起 Gemini 请求 (第 $attempt 次): $requestUrl, Model: $activeModel")

            withContext(Dispatchers.Main) {
                if (attempt == 1) {
                    onStatusUpdate("正在连通 Google Gemini 节点...")
                } else {
                    onStatusUpdate("原模型繁忙，已切换至备用引擎 ($activeModel)...")
                }
            }

            // 构建请求体
            val requestJson = JsonObject().apply {
                val contentsArray = JsonArray()
                val contentObj = JsonObject()
                contentObj.addProperty("role", "user")

                val partsArray = JsonArray()

                // 1. 原生音频直接输入（多模态耳朵）
                if (!audioBase64.isNullOrEmpty()) {
                    val audioPart = JsonObject()
                    val inlineData = JsonObject()
                    inlineData.addProperty("mime_type", "audio/wav")
                    inlineData.addProperty("data", audioBase64)
                    audioPart.add("inline_data", inlineData)
                    partsArray.add(audioPart)
                }

                // 2. 原生 PDF 或图像多模态文件输入
                if (!fileBase64.isNullOrEmpty() && !fileMimeType.isNullOrEmpty()) {
                    val filePart = JsonObject()
                    val inlineData = JsonObject()
                    inlineData.addProperty("mime_type", fileMimeType)
                    inlineData.addProperty("data", fileBase64)
                    filePart.add("inline_data", inlineData)
                    partsArray.add(filePart)
                }

                // 3. 屏幕截图等图像支持
                if (!imageBase64.isNullOrEmpty()) {
                    val imagePart = JsonObject()
                    val inlineData = JsonObject()
                    inlineData.addProperty("mime_type", "image/jpeg")
                    inlineData.addProperty("data", imageBase64)
                    imagePart.add("inline_data", inlineData)
                    partsArray.add(imagePart)
                }

                // 4. 文本文档、代码或表格内容嵌入
                if (!documentText.isNullOrEmpty()) {
                    val docPart = JsonObject()
                    docPart.addProperty("text", "【用户上传的文件内容如下】:\n```\n$documentText\n```\n请结合上述文件内容为用户进行深度解析、总结或答疑。")
                    partsArray.add(docPart)
                }

                // 5. 文本提问或系统提示词
                val effectivePrompt = if (prompt.isNotBlank()) {
                    prompt
                } else if (!audioBase64.isNullOrEmpty()) {
                    "请直接听懂这段语音内容并回答我的问题，用口语化、亲切自然的短句直接回答，适合语音朗读。"
                } else if (!fileBase64.isNullOrEmpty() || !documentText.isNullOrEmpty()) {
                    "请全面阅读并分析这份文件，总结其核心要点、关键数据与结论。"
                } else {
                    "你好"
                }
                val textPart = JsonObject()
                textPart.addProperty("text", effectivePrompt)
                partsArray.add(textPart)

                contentObj.add("parts", partsArray)
                contentsArray.add(contentObj)
                add("contents", contentsArray)

                // 生成与思考参数配置 (降低推理延迟，实现毫秒级快速回复)
                val config = JsonObject().apply {
                    addProperty("temperature", 0.7)
                    addProperty("maxOutputTokens", 2048)
                    val thinkingConfig = JsonObject()
                    if (activeModel.contains("gemini-3")) {
                        thinkingConfig.addProperty("thinkingLevel", "LOW")
                    } else {
                        thinkingConfig.addProperty("thinkingBudget", 0)
                    }
                    add("thinkingConfig", thinkingConfig)
                }
                add("generationConfig", config)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .header("Accept", "text/event-stream")
                .build()

            try {
                val response = SmartProxyManager.okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    val errBody = response.body?.string().orEmpty()

                    if ((code == 503 || code == 429) && attempt < maxAttempts) {
                        // 遇到 503 官方负载高峰，降级至 gemini-2.5-flash
                        val fallback = if (activeModel == "gemini-3.8-flash") "gemini-2.5-flash" else "gemini-2.0-flash"
                        Log.w(TAG, "检测到 Google 503 繁忙，自动降级至备用模型 $fallback 并在 1 秒后重试...")
                        activeModel = fallback
                        delay(1000)
                        continue
                    }

                    val errMsg = "Gemini 请求失败 HTTP $code: $errBody"
                    Log.e(TAG, errMsg)
                    withContext(Dispatchers.Main) {
                        onError(RuntimeException(errMsg))
                    }
                    return@withContext
                }

                val inputStream = response.body?.byteStream() ?: throw IllegalStateException("空响应流")
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var line: String?
                var isFirstChunk = true

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
                                    if (parts != null) {
                                        for (i in 0 until parts.size()) {
                                            val partObj = parts[i].asJsonObject
                                            // 过滤模型内部思考流，只将最终回答文本发送给前台
                                            val isThought = partObj.get("thought")?.asBoolean == true
                                            if (!isThought) {
                                                val text = partObj.get("text")?.asString.orEmpty()
                                                if (text.isNotEmpty()) {
                                                    if (isFirstChunk) {
                                                        isFirstChunk = false
                                                        withContext(Dispatchers.Main) {
                                                            onStatusUpdate("Gemini 正在回答...")
                                                        }
                                                    }
                                                    fullResponse.append(text)
                                                    withContext(Dispatchers.Main) {
                                                        onChunk(text)
                                                    }
                                                }
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
                return@withContext

            } catch (e: Throwable) {
                if (attempt < maxAttempts) {
                    Log.w(TAG, "连接异常，自动重试: ${e.message}")
                    delay(1200)
                    continue
                }
                Log.e(TAG, "Gemini 连接异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
                return@withContext
            }
        }
    }
}
