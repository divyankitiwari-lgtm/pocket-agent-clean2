package com.example.pocketagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/** Native, Android 8+ compatible liquid-glass orb driven by the voice/agent state. */
class LiquidOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, AUTOMATING, PAUSED, ERROR }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contour = Path()
    private var phase = 0f
    private var state = State.IDLE
    private var animator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    init {
        contentDescription = "JARVIS voice assistant"
        isClickable = true
        startMotion()
    }

    fun setState(next: State) {
        if (state == next) return
        state = next
        startMotion()
        contentDescription = when (next) {
            State.IDLE -> "JARVIS idle. Tap to speak."
            State.LISTENING -> "JARVIS is listening."
            State.THINKING -> "JARVIS is thinking."
            State.SPEAKING -> "JARVIS is speaking."
            State.AUTOMATING -> "JARVIS is automating."
            State.PAUSED -> "JARVIS automation paused."
            State.ERROR -> "JARVIS needs attention."
        }
        invalidate()
    }

    private fun startMotion() {
        animator?.cancel()
        val duration = when (state) {
            State.IDLE -> 7200L
            State.LISTENING, State.SPEAKING -> 2600L
            State.THINKING, State.AUTOMATING -> 3400L
            State.PAUSED -> 9000L
            State.ERROR -> 5000L
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedFraction * (Math.PI.toFloat() * 2f)
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator?.isStarted != true) startMotion()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.36f
        if (radius <= 0f) return

        val activity = when (state) {
            State.IDLE -> 0.035f
            State.LISTENING -> 0.11f
            State.THINKING -> 0.075f
            State.SPEAKING -> 0.14f
            State.AUTOMATING -> 0.09f
            State.PAUSED -> 0.018f
            State.ERROR -> 0.055f
        }
        val pulse = 1f + sin(phase * 2f) * activity
        val r = radius * pulse

        val (cyan, violet, core) = when (state) {
            State.ERROR -> Triple(Color.rgb(255, 100, 120), Color.rgb(180, 75, 170), Color.rgb(50, 24, 45))
            State.PAUSED -> Triple(Color.rgb(115, 145, 165), Color.rgb(90, 100, 145), Color.rgb(20, 32, 48))
            else -> Triple(Color.rgb(58, 245, 220), Color.rgb(115, 125, 255), Color.rgb(14, 42, 58))
        }

        // Soft outer bloom, built from native radial gradients (no Android 12 RenderEffect).
        paint.shader = RadialGradient(cx, cy, r * 1.48f,
            intArrayOf(Color.argb(0, cyan shr 16 and 255, cyan shr 8 and 255, cyan and 255),
                Color.argb(34, 65, 225, 220), Color.TRANSPARENT),
            floatArrayOf(0.58f, 0.82f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.48f, paint)
        paint.shader = null

        // Glass body.
        paint.shader = RadialGradient(cx - r * 0.34f, cy - r * 0.42f, r * 1.55f,
            intArrayOf(Color.rgb(226, 255, 252), cyan, core, Color.rgb(5, 10, 22)),
            floatArrayOf(0f, 0.24f, 0.68f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // Liquid contour: two harmonic waves create an organic, slowly changing silhouette.
        contour.reset()
        val points = 120
        for (i in 0..points) {
            val a = (i.toFloat() / points) * (Math.PI.toFloat() * 2f)
            val wave = sin(a * 3f + phase) * 0.035f +
                cos(a * 5f - phase * 1.35f) * 0.022f +
                sin(a * 2f + phase * 0.7f) * 0.018f
            val rr = r * (0.965f + wave * (1f + activity * 3f))
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) contour.moveTo(x, y) else contour.lineTo(x, y)
        }
        contour.close()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.4f)
        paint.shader = null
        paint.color = Color.argb(185, 205, 255, 249)
        canvas.drawPath(contour, paint)
        paint.style = Paint.Style.FILL

        // A moving translucent highlight gives the orb a glass-like surface.
        val hx = cx + cos(phase * 0.8f) * r * 0.22f
        val hy = cy - r * 0.38f + sin(phase * 0.8f) * r * 0.12f
        paint.shader = RadialGradient(hx, hy, r * 0.72f,
            intArrayOf(Color.argb(90, 255, 255, 255), Color.argb(25, 180, 255, 249), Color.TRANSPARENT),
            floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // Fine inner rim.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.8f)
        paint.color = Color.argb(90, 215, 255, 252)
        canvas.drawCircle(cx, cy, r * 0.91f, paint)
        paint.style = Paint.Style.FILL
    }
}
