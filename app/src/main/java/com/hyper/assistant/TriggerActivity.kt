package com.hyper.assistant

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.hyper.assistant.service.FloatingOverlayService

/**
 * 0 毫秒透明跳板 Activity
 * 专门用于与小爱同学快捷指令（例如对小爱说“召唤Gemini”、“打开超灵助手”）无缝联动
 * 瞬间唤起全屏 Siri 浮层并立即退出自身，实现完全无感的暗号呼叫融合。
 */
class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceIntent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        finish()
    }
}
