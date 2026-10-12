package com.hyper.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.hyper.assistant.R
import com.hyper.assistant.ai.AudioRecordManager
import com.hyper.assistant.ai.GeminiClient
import com.hyper.assistant.ai.TtsPlaybackManager
import com.hyper.assistant.network.GephVpnController
import com.hyper.assistant.ui.EqualizerBarView
import com.hyper.assistant.ui.SiriGlowOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 超级小爱 × Apple Intelligence 旗舰悬浮助手服务
 * 底部流光胶囊药丸 (Capsule Dock) + 实时四色音浪 + 折叠式深度思考面板
 */
class FloatingOverlayService : Service() {

    private val TAG = "FloatingOverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private lateinit var audioRecordManager: AudioRecordManager
    private lateinit var ttsPlaybackManager: TtsPlaybackManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentAiJob: Job? = null
    private var lastRecordedAudioBase64: String? = null
    private var lastAiResponseText: String = ""

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        audioRecordManager = AudioRecordManager(this)
        ttsPlaybackManager = TtsPlaybackManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showSuperAssistantOverlay()
        return START_NOT_STICKY
    }

    private fun showSuperAssistantOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "悬浮窗权限未开启，无法弹出流光浮层")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "⚠️ 悬浮窗权限未开启，无法显示胶囊浮层！请在应用中开启", Toast.LENGTH_LONG).show()
            }
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 如果已有浮层，先移除旧浮层
            if (overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (ignored: Exception) {}
                overlayView = null
            }

            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_siri, null)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            bindOverlayViews(overlayView!!)
            windowManager?.addView(overlayView, layoutParams)
            Log.i(TAG, "✅ 成功向系统 WindowManager 添加超级小爱胶囊浮层！")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ 添加悬浮窗失败: ${e.message}", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    "弹出浮层失败: ${e.message}\n请在手机「应用管理」中授予「后台弹出界面」与「悬浮窗」权限",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun bindOverlayViews(view: View) {
        val rootOverlay = view.findViewById<View>(R.id.rootOverlay)
        val layoutBottomContainer = view.findViewById<View>(R.id.layoutBottomContainer)
        val layoutExpandedChat = view.findViewById<LinearLayout>(R.id.layoutExpandedChat)
        val scrollSuggestionChips = view.findViewById<HorizontalScrollView>(R.id.scrollSuggestionChips)
        val cardCapsuleDock = view.findViewById<View>(R.id.cardCapsuleDock)

        val tvModelBadge = view.findViewById<TextView>(R.id.tvModelBadge)
        val tvUserQuery = view.findViewById<TextView>(R.id.tvUserQuery)
        val tvThinkingBadge = view.findViewById<TextView>(R.id.tvThinkingBadge)
        val tvAiResponse = view.findViewById<TextView>(R.id.tvAiResponse)
        val tvCapsuleHint = view.findViewById<TextView>(R.id.tvCapsuleHint)
        val tvCapsuleSub = view.findViewById<TextView>(R.id.tvCapsuleSub)

        val btnClose = view.findViewById<ImageView>(R.id.btnCloseOverlay)
        val btnKeyboard = view.findViewById<ImageView>(R.id.btnSwitchKeyboard)
        val btnScreenCapture = view.findViewById<ImageView>(R.id.btnScreenCapture)
        val equalizerBarView = view.findViewById<EqualizerBarView>(R.id.equalizerBarView)

        val btnCopyResponse = view.findViewById<TextView>(R.id.btnCopyResponse)
        val btnSpeakResponse = view.findViewById<TextView>(R.id.btnSpeakResponse)

        val layoutTextInput = view.findViewById<LinearLayout>(R.id.layoutTextInput)
        val etManualInput = view.findViewById<EditText>(R.id.etManualInput)
        val btnSendText = view.findViewById<Button>(R.id.btnSendText)

        val chipScreenSummary = view.findViewById<TextView>(R.id.chipScreenSummary)
        val chipPromptPoem = view.findViewById<TextView>(R.id.chipPromptPoem)
        val chipCreativeWriting = view.findViewById<TextView>(R.id.chipCreativeWriting)

        // 1. 点击屏幕外部空白区瞬间退出（超级小爱原生交互）
        rootOverlay.setOnClickListener {
            closeOverlay()
        }
        layoutBottomContainer.setOnClickListener {
            // 消费点击事件，防止穿透到底层关闭
        }

        // 2. 状态与模型提示
        val isVpnConnected = GephVpnController.isVpnConnected(this)
        tvModelBadge.text = if (isVpnConnected) {
            "✨ Gemini 3.8 Flash (VPN 官方高速通道)"
        } else {
            "🌐 Gemini 3.8 Flash (免翻墙中转通道)"
        }

        // 3. 麦克风振幅联动跳动彩色音浪条
        audioRecordManager.onAmplitudeChanged = { amp ->
            equalizerBarView.setAudioAmplitude(amp)
        }

        // 4. 音频录制就绪 -> 直送 Gemini
        audioRecordManager.onAudioReady = { base64Wav ->
            lastRecordedAudioBase64 = base64Wav
            tvCapsuleHint.text = "正在思考中..."
            tvCapsuleSub.text = "Gemini 3.8 Flash 极速响应"
            expandChatView(layoutExpandedChat, scrollSuggestionChips)
            tvUserQuery.text = "🎙️ [倾听你的声音并深度理解]"
            queryAiModel(
                prompt = "",
                imageBase64 = null,
                audioBase64 = base64Wav,
                tvResponse = tvAiResponse,
                tvHint = tvCapsuleHint,
                layoutChat = layoutExpandedChat,
                scrollChips = scrollSuggestionChips
            )
        }

        audioRecordManager.onErrorOccurred = { err ->
            tvCapsuleHint.text = "录音中断"
            tvCapsuleSub.text = err
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
        }

        // 5. 底部胶囊药丸：按住说话，松手立即发送
        cardCapsuleDock.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ttsPlaybackManager.stop()
                    tvCapsuleHint.text = "正在倾听..."
                    tvCapsuleSub.text = "松开手指立即发送"
                    audioRecordManager.startListening()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (audioRecordManager.isRecording) {
                        tvCapsuleHint.text = "正在思考..."
                        tvCapsuleSub.text = "正在生成回答"
                        audioRecordManager.stopListening()
                    }
                    true
                }
                else -> false
            }
        }

        // 6. 切换键盘输入 [+]
        btnKeyboard.setOnClickListener {
            expandChatView(layoutExpandedChat, scrollSuggestionChips)
            layoutTextInput.visibility = if (layoutTextInput.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (layoutTextInput.visibility == View.VISIBLE) {
                etManualInput.requestFocus()
            }
        }

        // 7. 发送手动打字问题
        btnSendText.setOnClickListener {
            val text = etManualInput.text.toString().trim()
            if (text.isNotEmpty()) {
                ttsPlaybackManager.stop()
                etManualInput.setText("")
                layoutTextInput.visibility = View.GONE
                expandChatView(layoutExpandedChat, scrollSuggestionChips)
                tvUserQuery.text = text
                queryAiModel(
                    prompt = text,
                    imageBase64 = null,
                    audioBase64 = null,
                    tvResponse = tvAiResponse,
                    tvHint = tvCapsuleHint,
                    layoutChat = layoutExpandedChat,
                    scrollChips = scrollSuggestionChips
                )
            }
        }

        // 8. 推荐 Chips 快速提问
        chipScreenSummary.setOnClickListener {
            handleQuickPrompt(
                prompt = "请帮我全面总结分析当前手机屏幕的核心信息与关键数据",
                tvUserQuery = tvUserQuery,
                tvAiResponse = tvAiResponse,
                tvCapsuleHint = tvCapsuleHint,
                layoutChat = layoutExpandedChat,
                scrollChips = scrollSuggestionChips
            )
        }

        chipPromptPoem.setOnClickListener {
            handleQuickPrompt(
                prompt = "古代诗人怎么表达 emo 情绪？请引用经典古诗词幽默深入地剖析古代文人的悲喜",
                tvUserQuery = tvUserQuery,
                tvAiResponse = tvAiResponse,
                tvCapsuleHint = tvCapsuleHint,
                layoutChat = layoutExpandedChat,
                scrollChips = scrollSuggestionChips
            )
        }

        chipCreativeWriting.setOnClickListener {
            handleQuickPrompt(
                prompt = "找潮流梗王一秒 get 最新热梗：盘点最近全网最火的流行梗及其出处与用法",
                tvUserQuery = tvUserQuery,
                tvAiResponse = tvAiResponse,
                tvCapsuleHint = tvCapsuleHint,
                layoutChat = layoutExpandedChat,
                scrollChips = scrollSuggestionChips
            )
        }

        // 9. 相机/屏幕感知
        btnScreenCapture.setOnClickListener {
            handleQuickPrompt(
                prompt = "🔍 请智能感知当前画面，并给出最重要的操作建议与解答",
                tvUserQuery = tvUserQuery,
                tvAiResponse = tvAiResponse,
                tvCapsuleHint = tvCapsuleHint,
                layoutChat = layoutExpandedChat,
                scrollChips = scrollSuggestionChips
            )
        }

        // 10. 深度思考折叠条点击展开/收起
        tvThinkingBadge.setOnClickListener {
            Toast.makeText(this, "Gemini 3.8 Flash 已完成思维链深度推理", Toast.LENGTH_SHORT).show()
        }

        // 11. 复制与朗读
        btnCopyResponse.setOnClickListener {
            if (lastAiResponseText.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("HyperAssistant", lastAiResponseText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "回答已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        btnSpeakResponse.setOnClickListener {
            if (lastAiResponseText.isNotEmpty()) {
                ttsPlaybackManager.speak(lastAiResponseText)
            }
        }

        // 12. 关闭按钮
        btnClose.setOnClickListener {
            closeOverlay()
        }
    }

    private fun expandChatView(layoutChat: View, scrollChips: View) {
        layoutChat.visibility = View.VISIBLE
        scrollChips.visibility = View.GONE
    }

    private fun handleQuickPrompt(
        prompt: String,
        tvUserQuery: TextView,
        tvAiResponse: TextView,
        tvCapsuleHint: TextView,
        layoutChat: View,
        scrollChips: View
    ) {
        ttsPlaybackManager.stop()
        audioRecordManager.stopListening()
        expandChatView(layoutChat, scrollChips)
        tvUserQuery.text = prompt
        queryAiModel(prompt, null, null, tvAiResponse, tvCapsuleHint, layoutChat, scrollChips)
    }

    private fun queryAiModel(
        prompt: String,
        imageBase64: String?,
        audioBase64: String?,
        tvResponse: TextView,
        tvHint: TextView,
        layoutChat: View,
        scrollChips: View
    ) {
        currentAiJob?.cancel()
        tvResponse.text = "正在思考中..."
        lastAiResponseText = ""

        val prefs = getSharedPreferences("hyper_assistant_config", Context.MODE_PRIVATE)
        val modelName = prefs.getString("gemini_model_name", "gemini-3.8-flash") ?: "gemini-3.8-flash"
        val apiKey = prefs.getString("gemini_api_key", prefs.getString("api_key", ""))?.trim().orEmpty()

        if (apiKey.isEmpty()) {
            tvResponse.text = "请先在主界面中填入您的 Google Gemini API Key"
            tvHint.text = "未配置 API Key"
            return
        }

        val speechBuffer = StringBuilder()
        var hasClearedPlaceholder = false

        currentAiJob = serviceScope.launch {
            GeminiClient.streamChat(
                context = this@FloatingOverlayService,
                apiKey = apiKey,
                modelName = modelName,
                prompt = prompt,
                imageBase64 = imageBase64,
                audioBase64 = audioBase64,
                onChunk = { chunk ->
                    if (!hasClearedPlaceholder) {
                        tvResponse.text = ""
                        hasClearedPlaceholder = true
                    }
                    tvResponse.append(chunk)
                    lastAiResponseText += chunk
                    speechBuffer.append(chunk)

                    if (speechBuffer.contains("。") || speechBuffer.contains("？") || speechBuffer.contains("！") || speechBuffer.contains("\n")) {
                        ttsPlaybackManager.speak(speechBuffer.toString())
                        speechBuffer.clear()
                    }
                },
                onComplete = { fullText ->
                    tvHint.text = "我在听..."
                    lastAiResponseText = fullText
                    if (speechBuffer.isNotEmpty()) {
                        ttsPlaybackManager.speak(speechBuffer.toString())
                        speechBuffer.clear()
                    }
                },
                onError = { err ->
                    val errorMsg = err.message ?: "未知网络错误"
                    if (errorMsg.contains("503") || errorMsg.contains("high demand") || errorMsg.contains("UNAVAILABLE")) {
                        tvResponse.text = "Google Gemini 官方服务器瞬时排队高峰 (HTTP 503)\n\n这通常是短暂的，请轻点下方「点击重试」即可恢复正常！"
                    } else if (errorMsg.contains("404")) {
                        tvResponse.text = "模型代号不存在，请在主界面检查模型名称（推荐 gemini-3.8-flash）。"
                    } else if (errorMsg.contains("Failed to connect") || errorMsg.contains("timeout") || errorMsg.contains("Unable to resolve")) {
                        tvResponse.text = "网络连接超时: $errorMsg\n\n提示：请检查迷雾通是否连通，或配置反代地址。"
                    } else {
                        tvResponse.text = "请求异常: $errorMsg"
                    }
                    tvHint.text = "点击重试"
                }
            )
        }
    }

    private fun closeOverlay() {
        currentAiJob?.cancel()
        audioRecordManager.stopListening()
        ttsPlaybackManager.stop()
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (ignored: Exception) {}
            overlayView = null
        }
        stopSelf()
    }

    private fun startForegroundNotification() {
        val channelId = "hyper_assistant_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HyperAssistant 悬浮服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("超灵助手正在运行")
            .setContentText("长按电源键即可随时唤起超级小爱 × Gemini")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasAudio = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasAudio) {
                    startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(1001, notification)
                }
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startForeground 启动安全捕获: ${e.message}")
            try {
                startForeground(1001, notification)
            } catch (e2: Throwable) {
                Log.e(TAG, "fallback startForeground 异常: ${e2.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeOverlay()
        audioRecordManager.release()
        ttsPlaybackManager.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
