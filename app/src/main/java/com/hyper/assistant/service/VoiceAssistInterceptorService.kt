package com.hyper.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * HyperOS 3.0 小爱同学毫秒级无障碍拦截与接管引擎
 * 当用户在 Redmi K90 至尊版上长按电源键 0.5s 触发小爱同学 (com.miui.voiceassist) 时：
 * 1. 毫秒级捕获小爱窗口弹出事件
 * 2. 瞬间执行 GLOBAL_ACTION_BACK 将小爱静默收起
 * 3. 毫秒级调起 FloatingOverlayService，呈现类似 Apple Siri 视觉与 Gemini 3 对话
 */
class VoiceAssistInterceptorService : AccessibilityService() {

    private val TAG = "VoiceAssistInterceptor"
    private var lastInterceptTimestamp = 0L
    private val DEBOUNCE_INTERVAL_MS = 1200L // 防抖时间间隔

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()

        // 监测目标：小米澎湃系统 (HyperOS 3.0) 小爱同学包名
        if (pkgName == "com.miui.voiceassist" || pkgName == "com.xiaomi.voiceassistant") {
            val now = SystemClock.uptimeMillis()
            if (now - lastInterceptTimestamp < DEBOUNCE_INTERVAL_MS) {
                return // 防抖过滤重复触发
            }
            lastInterceptTimestamp = now

            Log.i(TAG, "⚡ 成功捕获 HyperOS 3.0 小爱同学唤起: pkg=$pkgName, class=$className")

            // 1. 立即压制/收缩小爱界面
            performGlobalAction(GLOBAL_ACTION_BACK)

            // 2. 调起我们自己的 Siri / Gemini 3 全屏流光助手
            launchAssistantOverlay()
        }
    }

    private fun launchAssistantOverlay() {
        try {
            val intent = Intent(this, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "已成功接管并唤起 Gemini 3 Siri 交互面板")
        } catch (e: Exception) {
            Log.e(TAG, "启动悬浮服务失败: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被系统中断")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "HyperOS 3.0 小爱同学无障碍拦截服务已成功连接并激活")
    }
}
