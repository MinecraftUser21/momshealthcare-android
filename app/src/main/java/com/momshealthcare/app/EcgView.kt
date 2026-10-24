package com.momshealthcare.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

/**
 * The hero's heartbeat line: a faint trace with a bright pulse sweeping along it, like the
 * website's ECG. Draws one path and one segment per frame, with no allocations while running.
 */
class EcgView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Loop forever. When false (low-end phones) the pulse sweeps a couple of times, then rests. */
    var ambient = true

    private val density = resources.displayMetrics.density
    private val pulseColor = ContextCompat.getColor(context, R.color.text)

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ecg_base)
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pulseColor }

    private val path = Path()
    private val segment = Path()
    private val measure = PathMeasure()
    private val pos = FloatArray(2)
    private val shaderMatrix = Matrix()
    private var shader: LinearGradient? = null
    private var length = 0f
    private var tail = 0f

    /** Head position along the trace, 0..1; outside that range nothing bright is drawn. */
    private var progress = -1f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        buildPath(w.toFloat(), h.toFloat())
    }

    private fun buildPath(w: Float, h: Float) {
        // Same waveform as the website's SVG, kept at a natural size and centred.
        val u = minOf(0.9f * density, w * 0.72f / 272f)
        val b = h * 0.6f
        val sx = (w - 272f * u) / 2f
        path.reset()
        path.moveTo(0f, b)
        path.lineTo(sx, b)
        path.rLineTo(18 * u, -22 * u)
        path.rLineTo(18 * u, 22 * u)
        path.lineTo(sx + 90 * u, b)
        path.rLineTo(14 * u, -54 * u)
        path.rLineTo(18 * u, 92 * u)
        path.rLineTo(16 * u, -58 * u)
        path.rLineTo(12 * u, 20 * u)
        path.lineTo(sx + 240 * u, b)
        path.rLineTo(16 * u, -14 * u)
        path.rLineTo(16 * u, 14 * u)
        path.lineTo(w, b)

        measure.setPath(path, false)
        length = measure.length
        tail = w * 0.34f
        val clear = Color.argb(0, Color.red(pulseColor), Color.green(pulseColor), Color.blue(pulseColor))
        val mid = Color.argb(110, Color.red(pulseColor), Color.green(pulseColor), Color.blue(pulseColor))
        shader = LinearGradient(0f, 0f, tail, 0f, intArrayOf(clear, mid, pulseColor), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        pulsePaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, basePaint)
        val p = progress
        if (p < 0f || p > 1f || length == 0f) return

        val head = p * (length + tail)
        val start = (head - tail).coerceAtLeast(0f)
        val end = head.coerceAtMost(length)
        if (end <= start) return

        segment.reset()
        measure.getSegment(start, end, segment, true)
        measure.getPosTan(end, pos, null)
        val headX = pos[0] + (head - length).coerceAtLeast(0f)
        shaderMatrix.setTranslate(headX - tail, 0f)
        shader?.setLocalMatrix(shaderMatrix)
        canvas.drawPath(segment, pulsePaint)

        if (head <= length) {
            headPaint.alpha = 50
            canvas.drawCircle(pos[0], pos[1], 5f * density, headPaint)
            headPaint.alpha = 255
            canvas.drawCircle(pos[0], pos[1], 2f * density, headPaint)
        }
    }

    fun start() {
        if (!Motion.enabled(context)) return
        animator?.let {
            if (it.isPaused) it.resume()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600
            startDelay = 700
            interpolator = LinearInterpolator()
            repeatCount = if (ambient) ValueAnimator.INFINITE else 2
            addUpdateListener {
                val next = (it.animatedValue as Float) / 0.8f // last fifth is a rest beat
                val wasDrawing = progress in 0f..1f
                progress = next
                if (wasDrawing || next <= 1f) invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progress = -1f
                    invalidate()
                }
            })
            start()
        }
    }

    fun pause() {
        animator?.takeIf { it.isRunning }?.pause()
    }

    fun resume() {
        animator?.takeIf { it.isPaused }?.resume()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
