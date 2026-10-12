package com.hyper.assistant.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * 实时流式 TTS 朗读管理器
 * 支持首句流式快速发音、语速调节以及用户说话打断 (Barge-in)
 */
class TtsPlaybackManager(context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "TtsPlaybackManager"
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    var onSpeechFinished: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "系统 TTS 缺少中文语音包，回退默认语言")
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setPitch(1.05f) // 略微提亮音调，更接近 Siri 亲切感
            tts?.setSpeechRate(1.1f) // 微快语速提升对话爽快度

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS 开始朗读")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS 朗读完毕")
                    onSpeechFinished?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS 朗读出错")
                }
            })

            isInitialized = true
            Log.d(TAG, "TTS 引擎初始化成功")
        } else {
            Log.e(TAG, "TTS 初始化失败: $status")
        }
    }

    /**
     * 追加或打断朗读
     */
    fun speak(text: String, isFlush: Boolean = false) {
        if (!isInitialized || tts == null) return
        val mode = if (isFlush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "UTTERANCE_${System.currentTimeMillis()}")
    }

    /**
     * 语音打断 (Barge-in)
     */
    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        tts = null
    }
}
