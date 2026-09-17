package com.caxone.my_keyboard.media

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import com.caxone.my_keyboard.theme.KeyboardTheme

/** Placeholder until a GIF source exists; the keyboard has no network access by design. */
class GifPanel(context: Context) : PanelBase(context) {

    private val message = TextView(context).apply {
        text = "GIFs are coming soon"
        textSize = 15f
        gravity = Gravity.CENTER
    }

    init {
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        buildBottomRow()
    }

    override fun applyTheme(theme: KeyboardTheme) {
        super.applyTheme(theme)
        message.setTextColor(theme.hintText)
    }
}
