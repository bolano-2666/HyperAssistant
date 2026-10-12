package com.hyper.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Android 标准数字助手服务 (VoiceInteractionService)
 * 支持在原生手势导航下（屏幕左右底角向内上滑）或快捷 Assist 调用时直接原生唤醒
 */
class SystemAssistService : VoiceInteractionService()

class SystemAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return SystemAssistSession(this)
    }
}

class SystemAssistSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // 唤醒我们的全屏 Siri 浮层
        val intent = Intent(context, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        hide()
    }
}
