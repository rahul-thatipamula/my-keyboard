package com.caxone.my_keyboard.keyboard

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.caxone.my_keyboard.theme.KeyboardTheme

/** The three-slot suggestion bar shown above the keys, like Gboard's. */
class SuggestionStrip(context: Context) : LinearLayout(context) {

    var onSuggestionClick: ((Int) -> Unit)? = null

    private val slots = ArrayList<TextView>(3)
    private val dividers = ArrayList<View>(2)
    private var theme: KeyboardTheme = KeyboardTheme.LIGHT
    private val density = resources.displayMetrics.density

    init {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (44 * density).toInt())
        minimumHeight = (44 * density).toInt()
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }
        for (i in 0 until 3) {
            if (i > 0) {
                val d = View(context)
                addView(d, LayoutParams((1 * density).toInt(), (22 * density).toInt()).also { it.gravity = Gravity.CENTER_VERTICAL })
                dividers.add(d)
            }
            val tv = TextView(context).apply {
                gravity = Gravity.CENTER
                setSingleLine(true)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                setBackgroundResource(ripple.resourceId)
                setOnClickListener { if (text.isNotEmpty()) onSuggestionClick?.invoke(i) }
            }
            addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            slots.add(tv)
        }
        applyTheme(theme)
    }

    fun applyTheme(theme: KeyboardTheme) {
        this.theme = theme
        setBackgroundColor(theme.background)
        for (tv in slots) tv.setTextColor(theme.suggestionText)
        for (d in dividers) d.setBackgroundColor(theme.hintText and 0x60FFFFFF)
    }

    /**
     * Shows up to three [words]; [highlight] is bolded to show it will be committed on space.
     * A multi-word phrase gets a wider slot so it is not cut short.
     */
    fun setSuggestions(words: List<String>, highlight: Int) {
        for (i in slots.indices) {
            val tv = slots[i]
            val text = words.getOrNull(i) ?: ""
            tv.text = text
            val strong = i == highlight
            tv.typeface = if (strong) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.setTextColor(if (strong) theme.accent else theme.suggestionText)
            val wordCount = if (text.isEmpty()) 1 else text.count { it == ' ' } + 1
            (tv.layoutParams as LayoutParams).weight = 1f + 0.5f * (wordCount - 1).coerceAtMost(3)
        }
        requestLayout()
    }

    fun clear() = setSuggestions(emptyList(), -1)
}
