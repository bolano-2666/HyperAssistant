package com.hyper.assistant.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * 麦克风原生高保真录音管理器 (Gemini 原生听觉耳朵)
 * 使用标准 AudioRecord 录制 16kHz 16-bit Mono 音频，无需系统安装任何第三方语音识别组件！
 * 自动计算音浪振幅驱动 Siri 声波动画，并在录音结束时生成标准 WAV Base64 直送 Gemini 多模态模型。
 */
class AudioRecordManager(private val context: Context) {

    private val TAG = "AudioRecordManager"
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    var isRecording = false
        private set

    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onAudioReady: ((base64Wav: String) -> Unit)? = null
    var onErrorOccurred: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onErrorOccurred?.invoke("麦克风初始化错误: 缓冲区不可用")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorOccurred?.invoke("无法获取麦克风录音权限或被占用")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "已启动原生 PCM 录音...")

            recordJob = scope.launch {
                val buffer = ByteArray(minBufferSize)
                val outputStream = ByteArrayOutputStream()

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)

                        // 计算 RMS 振幅 (0 ~ 100)
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / (read / 2))
                        val amplitude = (rms / 32767.0 * 200.0).toFloat().coerceIn(10f, 100f)

                        withContext(Dispatchers.Main) {
                            onAmplitudeChanged?.invoke(amplitude)
                        }
                    }
                }

                // 录音结束，封装 WAV 格式并转 Base64
                val pcmData = outputStream.toByteArray()
                if (pcmData.isNotEmpty()) {
                    val wavData = pcmToWav(pcmData, SAMPLE_RATE, 1)
                    val base64Wav = Base64.encodeToString(wavData, Base64.NO_WRAP)
                    Log.d(TAG, "音频捕获完成，大小: ${wavData.size} 字节")
                    withContext(Dispatchers.Main) {
                        onAudioReady?.invoke(base64Wav)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "启动录音失败: ${e.message}", e)
            isRecording = false
            onErrorOccurred?.invoke("录音启动异常: ${e.message}")
        }
    }

    fun stopListening() {
        if (!isRecording) return
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "停止录音异常: ${e.message}")
        } finally {
            audioRecord = null
            recordJob?.cancel()
            onAmplitudeChanged?.invoke(10f)
        }
    }

    fun release() {
        stopListening()
        scope.cancel()
    }

    /**
     * 将原始 PCM 加上标准 44 字节 WAV 报头
     */
    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 16 / 8).toLong()
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
        header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
        header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = ((byteRate shr 8) and 0xffL).toByte()
        header[30] = ((byteRate shr 16) and 0xffL).toByte()
        header[31] = ((byteRate shr 24) and 0xffL).toByte()
        header[32] = (channels * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

        return header + pcmData
    }
}
