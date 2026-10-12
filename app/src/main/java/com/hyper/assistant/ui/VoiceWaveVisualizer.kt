package com.hyper.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * 仿 Siri 动态多彩声波律动控件
 * 随用户讲话音量或 AI 朗读流动态变换振幅与波浪波形
 */
class VoiceWaveVisualizer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#8000F2FE")
    }

    private val wavePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#99F355DA")
    }

    private val wavePaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#B34FACFE")
    }

    private var phase = 0f
    private var targetAmplitude = 18f
    private var currentAmplitude = 18f
    private var phaseAnimator: ValueAnimator? = null

    init {
        phaseAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                // 平滑逼近目标音量振幅
                currentAmplitude += (targetAmplitude - currentAmplitude) * 0.15f
                invalidate()
            }
            start()
        }
    }

    /**
     * 传入实时麦克风振幅 (0 ~ 100)
     */
    fun setAudioAmplitude(amplitude: Float) {
        // 映射振幅范围：基础高度 15f，最大峰值 70f
        targetAmplitude = 15f + (amplitude.coerceIn(0f, 100f) / 100f) * 55f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val centerY = h / 2f
        drawWave(canvas, w, centerY, phase, currentAmplitude, wavePaint1, 1.0f)
        drawWave(canvas, w, centerY, phase + 1.2f, currentAmplitude * 0.85f, wavePaint2, 1.3f)
        drawWave(canvas, w, centerY, phase + 2.5f, currentAmplitude * 0.7f, wavePaint3, 0.8f)
    }

    private fun drawWave(
        canvas: Canvas,
        width: Float,
        centerY: Float,
        phaseOffset: Float,
        amplitude: Float,
        paint: Paint,
        frequencyFactor: Float
    ) {
        val path = Path()
        path.moveTo(0f, centerY)

        val step = 10f
        var x = 0f
        while (x <= width) {
            // 使用汉宁窗 (Hanning Window) 衰减两端振幅，使波形在屏幕两边收敛为 0，中央最高
            val windowFactor = sin((x / width) * Math.PI).toFloat()
            val y = centerY + sin(x * 0.02f * frequencyFactor + phaseOffset).toFloat() * amplitude * windowFactor
            path.lineTo(x, y)
            x += step
        }

        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        phaseAnimator?.cancel()
    }
}
