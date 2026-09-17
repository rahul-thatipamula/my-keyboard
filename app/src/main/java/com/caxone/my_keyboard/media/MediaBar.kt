package com.caxone.my_keyboard.media

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.theme.KeyboardTheme

/** The strip under the keys with Emoji / GIF / Stickers tabs. Tapping the active tab closes it. */
class MediaBar(context: Context) : LinearLayout(context) {

    enum class Tab { EMOJI, GIF, STICKERS }

    var onTabSelected: ((Tab?) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val tabs = LinkedHashMap<Tab, LinearLayout>()
    private var theme: KeyboardTheme = KeyboardTheme.LIGHT
    var selected: Tab? = null
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = (HEIGHT_DP * density).toInt()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (HEIGHT_DP * density).toInt())
        addTab(Tab.EMOJI, R.drawable.ic_emoji, "Emoji")
        addTab(Tab.GIF, R.drawable.ic_gif, "GIF")
        addTab(Tab.STICKERS, R.drawable.ic_sticker, "Stickers")
        applyTheme(theme)
    }

    private fun addTab(tab: Tab, iconRes: Int, label: String) {
        val ripple = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
        val v = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { select(if (selected == tab) null else tab) }
        }
        v.addView(ImageView(context).apply {
            setImageResource(iconRes)
            tag = "icon"
        }, LayoutParams((20 * density).toInt(), (20 * density).toInt()))
        v.addView(TextView(context).apply {
            text = label
            textSize = 12f
            tag = "label"
            setPadding((6 * density).toInt(), 0, 0, 0)
        })
        addView(v, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        tabs[tab] = v
    }

    fun select(tab: Tab?) {
        selected = tab
        applyTheme(theme)
        onTabSelected?.invoke(tab)
    }

    /** Puts the bar back to "keyboard showing" without firing the callback. */
    fun reset() {
        selected = null
        applyTheme(theme)
    }

    fun applyTheme(theme: KeyboardTheme) {
        this.theme = theme
        setBackgroundColor(theme.background)
        for ((tab, v) in tabs) {
            val active = tab == selected
            val color = if (active) theme.accent else theme.hintText
            v.findViewWithTag<ImageView>("icon").imageTintList = ColorStateList.valueOf(color)
            v.findViewWithTag<TextView>("label").apply {
                setTextColor(if (active) theme.accent else theme.suggestionText)
                setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
    }

    companion object {
        const val HEIGHT_DP = 40
    }
}
