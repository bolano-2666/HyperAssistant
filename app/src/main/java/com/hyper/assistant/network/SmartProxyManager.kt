package com.hyper.assistant.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 智能双通道网络调度引擎
 * 通道 1：当检测到手机已开启「迷雾通」或系统 VPN 时，自动直连 Google 官方端点
 * 通道 2：当未开启 VPN 时，优先使用自定义反代或免翻墙镜像中转线路
 */
object SmartProxyManager {

    private const val TAG = "SmartProxyManager"
    private const val PREFS_NAME = "hyper_assistant_config"

    // 官方默认端点 (开 VPN 时极速直连)
    const val OFFICIAL_GEMINI_HOST = "https://generativelanguage.googleapis.com"
    const val OFFICIAL_OPENAI_HOST = "https://api.openai.com"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取当前生效的 Gemini API Base URL
     */
    fun getGeminiBaseUrl(context: Context): String {
        val isVpnActive = GephVpnController.isVpnConnected(context)
        val customProxy = getPrefs(context).getString("custom_proxy_base", "")?.trim().orEmpty()

        return if (isVpnActive) {
            Log.d(TAG, "[网络通道 A] 迷雾通/VPN 已开启，直连官方 Google Gemini 节点")
            OFFICIAL_GEMINI_HOST
        } else {
            if (customProxy.isNotEmpty()) {
                Log.d(TAG, "[网络通道 B] 使用自定义反代中转: $customProxy")
                customProxy
            } else {
                Log.d(TAG, "[网络通道 C] VPN 未开启且无反代，尝试直连官方")
                OFFICIAL_GEMINI_HOST
            }
        }
    }

    /**
     * 获取当前生效的 OpenAI Base URL (兼容旧版调用)
     */
    fun getOpenAiBaseUrl(context: Context): String {
        val isVpnActive = GephVpnController.isVpnConnected(context)
        val customProxy = getPrefs(context).getString("custom_proxy_base", "")?.trim().orEmpty()
        return if (isVpnActive) {
            OFFICIAL_OPENAI_HOST
        } else {
            if (customProxy.isNotEmpty()) customProxy else OFFICIAL_OPENAI_HOST
        }
    }

    fun saveCustomProxyBase(context: Context, proxyBase: String) {
        getPrefs(context).edit().putString("custom_proxy_base", proxyBase.trim()).apply()
    }

    fun getSavedCustomProxyBase(context: Context): String {
        return getPrefs(context).getString("custom_proxy_base", "") ?: ""
    }

    /**
     * 测试当前网络通道连通性与延迟
     */
    suspend fun testConnection(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val baseUrl = getGeminiBaseUrl(context)
        val isVpn = GephVpnController.isVpnConnected(context)
        val testUrl = "$baseUrl/v1beta/models"
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(testUrl)
                .head()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val elapsed = System.currentTimeMillis() - start
            val channelName = if (isVpn) "迷雾通/VPN 官方直连" else "反代中转通道"
            Pair(true, "✅ 连通成功！\n耗时: ${elapsed}ms\n当前通道: $channelName\n端点: $baseUrl")
        } catch (e: Exception) {
            val hint = if (!isVpn) " (提示: 当前未开 VPN，建议开启迷雾通或配置反代)" else ""
            Pair(false, "❌ 连接超时/失败: ${e.message}$hint\n测试端点: $baseUrl")
        }
    }
}
