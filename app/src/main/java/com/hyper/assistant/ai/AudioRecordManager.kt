package com.hyper.assistant.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * 麦克风拾音、音量振幅计算与语音实时转写管理器
 * 将用户讲话的音量实时喂给 VoiceWaveVisualizer，同时转写文字触发 Gemini / ChatGPT
 */
class AudioRecordManager(private val context: Context) {

    private val TAG = "AudioRecordManager"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onRecognizedResult: ((String) -> Unit)? = null
    var onErrorOccurred: ((String) -> Unit)? = null

    fun startListening() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "当前系统没有默认语音识别引擎")
            onErrorOccurred?.invoke("系统未检测到语音识别组件，可双击切换手动打字")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "已就绪，正在聆听用户说话...")
                    isListening = true
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "检测到用户开始讲话")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // rmsdB 范围通常在 -2 到 10 dB 之间，映射到 0 ~ 100 振幅
                    val amplitude = ((rmsdB + 2f) / 12f * 100f).coerceIn(5f, 100f)
                    onAmplitudeChanged?.invoke(amplitude)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "用户停止讲话")
                    isListening = false
                    onAmplitudeChanged?.invoke(10f)
                }

                override fun onError(error: Int) {
                    val errMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "未听清，请再说一次"
                        SpeechRecognizer.ERROR_NETWORK -> "网络不可用"
                        SpeechRecognizer.ERROR_AUDIO -> "麦克风录音异常"
                        else -> "语音识别暂时中断 ($error)"
                    }
                    Log.w(TAG, "识别错误: $errMsg")
                    isListening = false
                    onErrorOccurred?.invoke(errMsg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    isListening = false
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "语音识别成功: $text")
                        onRecognizedResult?.invoke(text)
                    } else {
                        onErrorOccurred?.invoke("未能识别到语音内容")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = partialMatches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotEmpty()) {
                        Log.v(TAG, "实时流式语音: $partial")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "启动语音监听失败: ${e.message}")
            onErrorOccurred?.invoke("启动录音失败: ${e.message}")
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "停止监听异常: ${e.message}")
        } finally {
            isListening = false
        }
    }

    fun release() {
        stopListening()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        speechRecognizer = null
    }
}
