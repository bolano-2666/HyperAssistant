package com.hyper.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 仿 Apple Intelligence (iOS 18 Siri) 屏幕四周全彩流光边框动画控件
 * 通过围绕屏幕四周边缘的高斯模糊渐变光带与动态旋转渲染，完美还原 Siri 呼出时的视觉沉浸感。
 */
class SiriGlowOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f // 光晕厚度
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    private var rotationAngle = 0f
    private var pulseScale = 1f
    private var animator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    // Siri 标志性流光色彩：青蓝、靛蓝、紫红、金橙
    private val glowColors = intArrayOf(
        Color.parseColor("#00F2FE"),
        Color.parseColor("#4FACFE"),
        Color.parseColor("#9B51E0"),
        Color.parseColor("#F355DA"),
        Color.parseColor("#FF7675"),
        Color.parseColor("#00F2FE")
    )

    private val glowPositions = floatArrayOf(0.0f, 0.2f, 0.45f, 0.7f, 0.88f, 1.0f)

    init {
        // 开启软件加速以支持 BlurMaskFilter
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startGlowAnimation()
    }

    private fun startGlowAnimation() {
        // 角度连续旋转动画
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // 呼吸脉动动画 (根据语音或处于待命时微弱律动)
        pulseAnimator = ValueAnimator.ofFloat(0.85f, 1.15f).apply {
            duration = 1600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                paint.strokeWidth = 22f * pulseScale
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val centerX = w / 2f
        val centerY = h / 2f

        // 构建四周流光渐变矩阵
        val shader = SweepGradient(centerX, centerY, glowColors, glowPositions)
        val matrix = Matrix()
        matrix.postRotate(rotationAngle, centerX, centerY)
        shader.setLocalMatrix(matrix)

        paint.shader = shader

        // 绘制圆角矩形光晕（贴合 Redmi 手机四周圆角边框）
        val inset = paint.strokeWidth / 2f
        val cornerRadius = 64f // Redmi 屏幕大圆角
        val rect = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    fun stopAnimation() {
        animator?.cancel()
        pulseAnimator?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
