package com.hyper.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.hyper.assistant.R
import com.hyper.assistant.ai.AudioRecordManager
import com.hyper.assistant.ai.ChatGptClient
import com.hyper.assistant.ai.GeminiClient
import com.hyper.assistant.ai.TtsPlaybackManager
import com.hyper.assistant.network.GephVpnController
import com.hyper.assistant.ui.SiriGlowOverlayView
import com.hyper.assistant.ui.VoiceWaveVisualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 全屏 Siri 流光与多模态交互悬浮服务
 * 负责渲染浮窗、调度 Gemini 3 / ChatGPT 流式推理与音频律动
 */
class FloatingOverlayService : Service() {

    private val TAG = "FloatingOverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private lateinit var audioRecordManager: AudioRecordManager
    private lateinit var ttsPlaybackManager: TtsPlaybackManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentAiJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        audioRecordManager = AudioRecordManager(this)
        ttsPlaybackManager = TtsPlaybackManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showSiriOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showSiriOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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

        // 启动拾音
        startListeningVoice()
    }

    private fun bindOverlayViews(view: View) {
        val siriGlow = view.findViewById<SiriGlowOverlayView>(R.id.siriGlowView)
        val voiceWave = view.findViewById<VoiceWaveVisualizer>(R.id.voiceWaveView)
        val tvModelBadge = view.findViewById<TextView>(R.id.tvModelBadge)
        val tvVpnStatus = view.findViewById<TextView>(R.id.tvOverlayVpnStatus)
        val tvUserQuery = view.findViewById<TextView>(R.id.tvUserQuery)
        val tvAiResponse = view.findViewById<TextView>(R.id.tvAiResponse)
        val tvMicStatus = view.findViewById<TextView>(R.id.tvMicStatus)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseOverlay)
        val btnKeyboard = view.findViewById<ImageView>(R.id.btnSwitchKeyboard)
        val layoutTextInput = view.findViewById<LinearLayout>(R.id.layoutTextInput)
        val etManualInput = view.findViewById<EditText>(R.id.etManualInput)
        val btnSendText = view.findViewById<Button>(R.id.btnSendText)
        val btnMicTrigger = view.findViewById<FrameLayout>(R.id.btnMicTrigger)
        val btnScreenCapture = view.findViewById<ImageView>(R.id.btnScreenCapture)

        val prefs = getSharedPreferences("hyper_assistant_config", Context.MODE_PRIVATE)
        val isGemini = prefs.getBoolean("use_gemini", true)
        val modelName = prefs.getString("model_name", if (isGemini) "gemini-3.1-pro" else "gpt-4o") ?: "gemini-3.1-pro"
        tvModelBadge.text = if (isGemini) "✨ $modelName" else "⚡ $modelName"

        val isVpnConnected = GephVpnController.isVpnConnected(this)
        tvVpnStatus.text = if (isVpnConnected) "🛡️ 迷雾通已联通" else "🌐 免翻墙反代直连中"
        tvVpnStatus.setTextColor(if (isVpnConnected) 0xFF34C759.toInt() else 0xFF00F2FE.toInt())

        // 振幅联动声波动画
        audioRecordManager.onAmplitudeChanged = { amp ->
            voiceWave.setAudioAmplitude(amp)
        }

        audioRecordManager.onRecognizedResult = { queryText ->
            tvUserQuery.text = "🗣️ \"$queryText\""
            tvMicStatus.text = "思考中..."
            queryAiModel(queryText, null, tvAiResponse, tvMicStatus)
        }

        audioRecordManager.onErrorOccurred = { err ->
            tvUserQuery.text = err
            tvMicStatus.text = "点击重新麦克风"
        }

        btnMicTrigger.setOnClickListener {
            ttsPlaybackManager.stop() // 打断当前播放
            startListeningVoice()
        }

        btnKeyboard.setOnClickListener {
            layoutTextInput.visibility = if (layoutTextInput.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnSendText.setOnClickListener {
            val input = etManualInput.text.toString().trim()
            if (input.isNotEmpty()) {
                audioRecordManager.stopListening()
                ttsPlaybackManager.stop()
                tvUserQuery.text = "⌨️ \"$input\""
                etManualInput.setText("")
                layoutTextInput.visibility = View.GONE
                queryAiModel(input, null, tvAiResponse, tvMicStatus)
            }
        }

        btnScreenCapture.setOnClickListener {
            // 触发屏幕理解 (可结合 MediaProjection 或 Accessibility 截图)
            tvUserQuery.text = "🔍 正在扫描屏幕内容..."
            queryAiModel("请帮我总结并分析当前手机屏幕上的核心信息与操作建议", null, tvAiResponse, tvMicStatus)
        }

        btnClose.setOnClickListener {
            closeOverlay()
        }
    }

    private fun startListeningVoice() {
        val tvMicStatus = overlayView?.findViewById<TextView>(R.id.tvMicStatus)
        tvMicStatus?.text = "正在聆听..."
        audioRecordManager.startListening()
    }

    private fun queryAiModel(
        prompt: String,
        imageBase64: String?,
        tvResponse: TextView,
        tvMicStatus: TextView
    ) {
        currentAiJob?.cancel()
        tvResponse.text = ""

        val prefs = getSharedPreferences("hyper_assistant_config", Context.MODE_PRIVATE)
        val isGemini = prefs.getBoolean("use_gemini", true)
        val modelName = prefs.getString("model_name", if (isGemini) "gemini-3.1-pro" else "gpt-4o") ?: "gemini-3.1-pro"
        val apiKey = prefs.getString("api_key", "")?.trim().orEmpty()

        if (apiKey.isEmpty()) {
            tvResponse.text = "请先在主应用中配置 API Key (Gemini 或 ChatGPT)"
            tvMicStatus.text = "未配置 API Key"
            return
        }

        val speechBuffer = StringBuilder()

        currentAiJob = serviceScope.launch {
            if (isGemini) {
                GeminiClient.streamChat(
                    context = this@FloatingOverlayService,
                    apiKey = apiKey,
                    modelName = modelName,
                    prompt = prompt,
                    imageBase64 = imageBase64,
                    onChunk = { chunk ->
                        tvResponse.append(chunk)
                        speechBuffer.append(chunk)
                        // 遇到句号、问号、换行时流式断句朗读
                        if (speechBuffer.contains("。") || speechBuffer.contains("？") || speechBuffer.contains("！") || speechBuffer.contains("\n")) {
                            ttsPlaybackManager.speak(speechBuffer.toString())
                            speechBuffer.clear()
                        }
                    },
                    onComplete = { fullText ->
                        tvMicStatus.text = "回答完毕"
                        if (speechBuffer.isNotEmpty()) {
                            ttsPlaybackManager.speak(speechBuffer.toString())
                            speechBuffer.clear()
                        }
                    },
                    onError = { err ->
                        tvResponse.text = "请求异常: ${err.message}"
                        tvMicStatus.text = "发生错误"
                    }
                )
            } else {
                ChatGptClient.streamChat(
                    context = this@FloatingOverlayService,
                    apiKey = apiKey,
                    modelName = modelName,
                    prompt = prompt,
                    onChunk = { chunk ->
                        tvResponse.append(chunk)
                        speechBuffer.append(chunk)
                        if (speechBuffer.contains("。") || speechBuffer.contains("？") || speechBuffer.contains("！") || speechBuffer.contains("\n")) {
                            ttsPlaybackManager.speak(speechBuffer.toString())
                            speechBuffer.clear()
                        }
                    },
                    onComplete = {
                        tvMicStatus.text = "回答完毕"
                        if (speechBuffer.isNotEmpty()) {
                            ttsPlaybackManager.speak(speechBuffer.toString())
                            speechBuffer.clear()
                        }
                    },
                    onError = { err ->
                        tvResponse.text = "请求异常: ${err.message}"
                        tvMicStatus.text = "发生错误"
                    }
                )
            }
        }
    }

    private fun closeOverlay() {
        currentAiJob?.cancel()
        audioRecordManager.stopListening()
        ttsPlaybackManager.stop()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
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
            .setContentText("长按电源键即可随时唤起 Gemini / ChatGPT")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, notification)
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
