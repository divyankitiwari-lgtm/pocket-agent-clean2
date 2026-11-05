package com.example.pocketagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * JARVIS identity orb. The centre remains transparent/black: only the fine,
 * layered red-orange and electric-blue liquid contours are painted.
 * Designed for Android 10+ without RenderEffect or newer graphics APIs.
 */
class LiquidOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, AUTOMATING, PAUSED, ERROR }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paths = Array(4) { Path() }
    private val shaderMatrix = Matrix()
    private val rimShader: SweepGradient
    private var phase = 0f
    private var state = State.IDLE
    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        contentDescription = "JARVIS. Tap to speak."
        rimShader = SweepGradient(
            0f, 0f,
            intArrayOf(
                Color.rgb(105, 111, 255),
                Color.rgb(52, 119, 255),
                Color.rgb(90, 108, 255),
                Color.rgb(255, 100, 82),
                Color.rgb(255, 125, 108),
                Color.rgb(112, 105, 255)
            ),
            floatArrayOf(0f, 0.24f, 0.48f, 0.72f, 0.84f, 1f)
        )
        startMotion()
    }

    fun setState(next: State) {
        if (state == next) return
        state = next
        contentDescription = when (next) {
            State.IDLE -> "JARVIS idle. Tap to speak."
            State.LISTENING -> "JARVIS is listening."
            State.THINKING -> "JARVIS is thinking."
            State.SPEAKING -> "JARVIS is speaking."
            State.AUTOMATING -> "JARVIS is automating."
            State.PAUSED -> "JARVIS automation paused."
            State.ERROR -> "JARVIS needs attention."
        }
        startMotion()
        invalidate()
    }

    private fun startMotion() {
        animator?.cancel()
        val profile = OrbMotionProfile.forState(state)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = profile.durationMs
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

    private fun buildContour(path: Path, cx: Float, cy: Float, radius: Float,
                             contourIndex: Int, amplitude: Float) {
        path.reset()
        val phaseOffset = contourIndex * 1.07f
        val points = 180
        for (i in 0..points) {
            val angle = (i.toFloat() / points) * (Math.PI.toFloat() * 2f)
            val waveA = sin(angle * 3.0f + phase * 1.0f + phaseOffset) * 0.052f
            val waveB = cos(angle * 5.0f - phase * 1.28f + phaseOffset) * 0.031f
            val waveC = sin(angle * 2.0f + phase * 0.62f - phaseOffset) * 0.024f
            val fold = sin(angle * 1.0f - phase * 0.48f + phaseOffset) * 0.018f
            val radial = radius * (1f + (waveA + waveB + waveC + fold) * (0.55f + amplitude * 5.0f))
            val rotation = phaseOffset * 0.13f + sin(phase * 0.55f + contourIndex) * 0.075f
            val x = cx + cos(angle + rotation) * radial
            val y = cy + sin(angle + rotation) * radial
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = minOf(width, height) * 0.345f
        if (baseRadius <= 0f) return

        val profile = OrbMotionProfile.forState(state)
        val amplitude = profile.amplitude
        val pulse = 1f + sin(phase * 2.0f) * amplitude * 0.45f
        val base = baseRadius * pulse

        // Rotate the red highlight around the upper rim while blue stays concentrated below.
        shaderMatrix.setRotate(sin(phase * 0.35f) * 8f, cx, cy)
        rimShader.setLocalMatrix(shaderMatrix)
        paint.shader = rimShader

        // Four independent, offset contours create the layered folds from the supplied clip.
        val scales = floatArrayOf(1.00f, 0.965f, 0.915f, 0.865f)
        for (index in paths.indices) {
            val path = paths[index]
            val radius = base * scales[index]
            buildContour(path, cx, cy, radius, index, amplitude)

            val opacity = when (index) {
                0 -> 255
                1 -> 170
                2 -> 118
                else -> 82
            }
            // Soft localized bloom, followed by a narrow luminous edge.
            paint.strokeWidth = dp(if (index == 0) 8f else 5f)
            paint.alpha = if (index == 0) 30 else 17
            canvas.drawPath(path, paint)

            paint.strokeWidth = dp(if (index == 0) 2.0f else 1.15f)
            paint.alpha = opacity
            canvas.drawPath(path, paint)

            // A tiny moving specular glint makes the contour read as liquid glass.
            if (index < 2) {
                val glint = Path()
                val start = (phase * 0.17f + index * 2.1f) % (Math.PI.toFloat() * 2f)
                val span = 0.16f + amplitude * 0.65f
                val steps = 18
                for (step in 0..steps) {
                    val angle = start + span * step / steps
                    val wave = sin(angle * 3f + phase + index) * 0.045f
                    val rr = radius * (1f + wave)
                    val x = cx + cos(angle) * rr
                    val y = cy + sin(angle) * rr
                    if (step == 0) glint.moveTo(x, y) else glint.lineTo(x, y)
                }
                paint.shader = null
                paint.color = Color.argb(if (index == 0) 150 else 90, 235, 246, 255)
                paint.strokeWidth = dp(0.85f)
                canvas.drawPath(glint, paint)
                paint.shader = rimShader
            }
        }
        paint.alpha = 255
        paint.shader = null
        paint.color = Color.WHITE
    }
}
