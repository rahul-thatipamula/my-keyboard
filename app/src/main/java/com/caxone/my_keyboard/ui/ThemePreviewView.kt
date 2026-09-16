package com.caxone.my_keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.caxone.my_keyboard.theme.KeyboardTheme

/** A tiny three-row keyboard drawing used as a theme thumbnail. */
class ThemePreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var theme: KeyboardTheme = KeyboardTheme.LIGHT
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rows = intArrayOf(10, 9, 9)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val density = resources.displayMetrics.density
        val pad = 6 * density
        val gap = 2 * density
        val rowH = (height - 2 * pad) / rows.size
        val unit = (width - 2 * pad) / 10f
        for (r in rows.indices) {
            val count = rows[r]
            val startX = pad + (10 - count) / 2f * unit
            for (i in 0 until count) {
                val isFunction = r == 2 && (i == 0 || i == count - 1)
                val isAccent = r == 2 && i == count - 1
                paint.color = when {
                    isAccent -> theme.accent
                    isFunction -> theme.functionKeyBackground
                    else -> theme.keyBackground
                }
                val left = startX + i * unit + gap / 2
                val top = pad + r * rowH + gap / 2
                rect.set(left, top, left + unit - gap, top + rowH - gap)
                canvas.drawRoundRect(rect, 3 * density, 3 * density, paint)
            }
        }
    }
}
