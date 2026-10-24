package com.momshealthcare.app

import android.animation.ObjectAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.animation.Interpolator
import android.widget.TextView
import androidx.core.app.ActivityManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.animation.PathInterpolatorCompat

/**
 * Motion follows the website: one easing curve, short rises and fades, nothing that loops
 * on phones that can't spare the frames.
 */
object Motion {
    /** The website's --ease: cubic-bezier(0.22, 1, 0.36, 1). */
    val ease: Interpolator = PathInterpolatorCompat.create(0.22f, 1f, 0.36f, 1f)
    val easeInOut: Interpolator = PathInterpolatorCompat.create(0.65f, 0f, 0.35f, 1f)

    /** False when the person turned animations off, or battery saver is on. */
    fun enabled(context: Context): Boolean {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return scale > 0f && !power.isPowerSaveMode
    }

    /** Phones where looping, ambient motion isn't worth the battery or the frames. */
    fun lowEnd(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManagerCompat.isLowRamDevice(am) ||
            am.memoryClass < 128 ||
            Runtime.getRuntime().availableProcessors() < 4
    }

    fun ambient(context: Context) = enabled(context) && !lowEnd(context)

    fun dp(context: Context, value: Float) = value * context.resources.displayMetrics.density

    /** Staggered rise-in for a group of views arriving together. */
    fun riseIn(views: List<View>, startDelay: Long = 0, stagger: Long = 70, distanceDp: Float = 18f, duration: Long = 720) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(v.context, distanceDp)
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(startDelay + i * stagger)
                .setDuration(duration)
                .setInterpolator(ease)
                .withLayer()
                .start()
        }
    }

    /** A small sideways shake for a field that needs attention. */
    fun nudge(view: View) {
        if (!enabled(view.context)) return
        val d = dp(view.context, 6f)
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -d, d, -d * 0.6f, d * 0.6f, -d * 0.25f, 0f)
            .setDuration(420)
            .start()
    }
}

/** The website's pill toast. */
class Toaster(private val view: TextView) {
    private val hide = Runnable {
        view.animate()
            .alpha(0f)
            .translationY(Motion.dp(view.context, 12f))
            .setDuration(220)
            .setInterpolator(Motion.easeInOut)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    fun show(text: CharSequence, error: Boolean = false, duration: Long = 2800) {
        val ctx = view.context
        val icon = ContextCompat.getDrawable(ctx, if (error) R.drawable.ic_alert else R.drawable.ic_check_circle)!!.mutate()
        DrawableCompat.setTint(icon, ContextCompat.getColor(ctx, if (error) R.color.closed else R.color.open))
        val size = Motion.dp(ctx, 20f).toInt()
        icon.setBounds(0, 0, size, size)
        view.setCompoundDrawablesRelative(icon, null, null, null)
        view.text = text

        view.removeCallbacks(hide)
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.translationY = Motion.dp(ctx, 16f)
            view.visibility = View.VISIBLE
        }
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(Motion.ease)
            .start()
        view.postDelayed(hide, duration)
    }
}
