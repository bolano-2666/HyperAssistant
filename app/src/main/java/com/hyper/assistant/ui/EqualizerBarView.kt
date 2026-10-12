package com.hyper.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * 仿超级小爱 / Apple Intelligence 四色彩色音浪律动条
 * 位于底部胶囊药丸右侧，4 根垂直圆角波形条随麦克风振幅或呼吸节奏动态律动
 */
class EqualizerBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = intArrayOf(
        Color.parseColor("#FF5E7E"), // 玫瑰粉
        Color.parseColor("#A259FF"), // 霓虹紫
        Color.parseColor("#00F2FE"), // 荧光青
        Color.parseColor("#4FACFE")  // 蔚蓝
    )

    private val paints = colors.map { c ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c
            style = Paint.Style.FILL
        }
    }

    private var targetAmplitude = 0f
    private var currentAmplitude = 0f
    private var animPhase = 0f
    private var animator: ValueAnimator? = null

    init {
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animPhase = it.animatedValue as Float
                currentAmplitude += (targetAmplitude - currentAmplitude) * 0.2f
                invalidate()
            }
            start()
        }
    }

    /**
     * 设置麦克风音量振幅 (0 ~ 100)
     */
    fun setAudioAmplitude(amplitude: Float) {
        targetAmplitude = amplitude.coerceIn(0f, 100f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val barCount = 4
        val barWidth = 7f // 粗细
        val gap = 6f
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (w - totalWidth) / 2f
        val centerY = h / 2f

        for (i in 0 until barCount) {
            val paint = paints[i]
            val x = startX + i * (barWidth + gap)

            // 基础呼吸高度 + 声音振幅增量
            val breathing = (sin(animPhase + i * 1.1) + 1.0) / 2.0 // 0.0 ~ 1.0
            val minHeight = 10f + breathing.toFloat() * 14f
            val dynamicHeight = minHeight + (currentAmplitude / 100f) * (h * 0.7f)
            val barHeight = dynamicHeight.coerceIn(8f, h * 0.9f)

            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            val rect = RectF(x, top, x + barWidth, bottom)
            val corner = barWidth / 2f
            canvas.drawRoundRect(rect, corner, corner, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
