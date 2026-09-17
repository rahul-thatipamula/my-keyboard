package com.caxone.my_keyboard.media

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.theme.KeyboardTheme

/** Shared pieces for the panels that replace the keys: a bottom row with ABC and backspace. */
abstract class PanelBase(context: Context) : LinearLayout(context) {

    var onBack: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null

    protected val density = resources.displayMetrics.density
    protected var theme: KeyboardTheme = KeyboardTheme.LIGHT

    protected lateinit var bottomRow: LinearLayout
    private lateinit var abc: TextView
    private lateinit var backspace: ImageView

    init {
        orientation = VERTICAL
    }

    protected fun dp(v: Int) = (v * density).toInt()

    protected fun ripple(): Int = TypedValue().also {
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
    }.resourceId

    /** Builds the bottom row; [middle] is stretched between the two buttons. */
    protected fun buildBottomRow(middle: View? = null) {
        bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        abc = TextView(context).apply {
            text = "ABC"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            setBackgroundResource(ripple())
            setOnClickListener { onBack?.invoke() }
        }
        bottomRow.addView(abc, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        bottomRow.addView(middle ?: View(context), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        backspace = ImageView(context).apply {
            setImageResource(R.drawable.ic_backspace)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setBackgroundResource(ripple())
            setOnClickListener { onBackspace?.invoke() }
        }
        bottomRow.addView(backspace, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(bottomRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
    }

    open fun applyTheme(theme: KeyboardTheme) {
        this.theme = theme
        setBackgroundColor(theme.background)
        if (::abc.isInitialized) {
            abc.setTextColor(theme.keyText)
            backspace.imageTintList = ColorStateList.valueOf(theme.keyText)
        }
    }
}
