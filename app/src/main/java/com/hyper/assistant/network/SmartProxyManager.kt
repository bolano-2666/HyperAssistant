package com.hyper.assistant.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 智能双通道网络调度引擎
 * 通道 1：当检测到手机已开启「迷雾通」或系统 VPN 时，自动直连 Google / OpenAI 官方节点
 * 通道 2：当未开启 VPN 时，自动无感切换为内置免翻墙反代 / Cloudflare Worker 中转通道
 */
object SmartProxyManager {

    private const val TAG = "SmartProxyManager"
    private const val PREFS_NAME = "hyper_assistant_config"

    // 官方默认端点
    const val OFFICIAL_GEMINI_HOST = "https://generativelanguage.googleapis.com"
    const val OFFICIAL_OPENAI_HOST = "https://api.openai.com"

    // 默认内置的高速免翻墙反向代理镜像线路 (支持 Cloudflare Worker 免费部署反代)
    const val DEFAULT_CF_PROXY_HOST = "https://gemini-cf-proxy.hyperassistant.workers.dev"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // 流式长连接需要较长读取超时
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
            Log.d(TAG, "[网络通道 A] 迷雾通/VPN 已开启，直连官方 Gemini 节点")
            OFFICIAL_GEMINI_HOST
        } else {
            val endpoint = if (customProxy.isNotEmpty()) customProxy else DEFAULT_CF_PROXY_HOST
            Log.d(TAG, "[网络通道 B] VPN 未开启，自动启用免科学上网反向代理: $endpoint")
            endpoint
        }
    }

    /**
     * 获取当前生效的 OpenAI API Base URL
     */
    fun getOpenAiBaseUrl(context: Context): String {
        val isVpnActive = GephVpnController.isVpnConnected(context)
        val customProxy = getPrefs(context).getString("custom_proxy_base", "")?.trim().orEmpty()

        return if (isVpnActive) {
            Log.d(TAG, "[网络通道 A] 迷雾通/VPN 已开启，直连官方 OpenAI 节点")
            OFFICIAL_OPENAI_HOST
        } else {
            val endpoint = if (customProxy.isNotEmpty()) customProxy else DEFAULT_CF_PROXY_HOST
            Log.d(TAG, "[网络通道 B] VPN 未开启，自动启用免翻墙反向代理: $endpoint")
            endpoint
        }
    }

    fun saveCustomProxyBase(context: Context, proxyBase: String) {
        getPrefs(context).edit().putString("custom_proxy_base", proxyBase.trim()).apply()
    }

    fun getSavedCustomProxyBase(context: Context): String {
        return getPrefs(context).getString("custom_proxy_base", "") ?: ""
    }
}
