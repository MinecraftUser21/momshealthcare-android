package com.momshealthcare.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** Lays children out left to right, wrapping onto new lines. Small enough to skip a library for. */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val gap = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineHeight = 0
        var widest = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChild(child, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), heightMeasureSpec)
            val w = child.measuredWidth
            if (x > 0 && x + w > maxWidth) {
                x = 0
                y += lineHeight + gap
                lineHeight = 0
            }
            x += w + gap
            widest = maxOf(widest, x - gap)
            lineHeight = maxOf(lineHeight, child.measuredHeight)
        }
        val height = y + lineHeight + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(widest + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val w = child.measuredWidth
            val h = child.measuredHeight
            if (x > 0 && x + w > maxWidth) {
                x = 0
                y += lineHeight + gap
                lineHeight = 0
            }
            child.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + gap
            lineHeight = maxOf(lineHeight, h)
        }
    }
}
