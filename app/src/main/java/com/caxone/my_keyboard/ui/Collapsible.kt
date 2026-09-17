package com.caxone.my_keyboard.ui

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.settings.Prefs

/**
 * Wires a [R.layout.row_collapsible_header] row to the [body] it controls. The collapsed state
 * is remembered under [prefKey] so a preview hidden on one screen stays hidden on the others.
 */
object Collapsible {

    fun bind(header: View, body: View, titleRes: Int, prefKey: String) {
        val context = header.context
        header.findViewById<TextView>(R.id.title).setText(titleRes)
        val chevron = header.findViewById<ImageView>(R.id.chevron)
        val state = header.findViewById<TextView>(R.id.state)

        fun apply(collapsed: Boolean, animate: Boolean) {
            body.visibility = if (collapsed) View.GONE else View.VISIBLE
            state.text = context.getString(if (collapsed) R.string.collapsible_show else R.string.collapsible_hide)
            val rotation = if (collapsed) 0f else 90f
            if (animate) chevron.animate().rotation(rotation).setDuration(150).start() else chevron.rotation = rotation
        }

        apply(Prefs.get(context).getBoolean(prefKey, false), animate = false)
        header.setOnClickListener {
            val collapsed = body.visibility == View.VISIBLE
            Prefs.set(context, prefKey, collapsed)
            apply(collapsed, animate = true)
        }
    }
}
