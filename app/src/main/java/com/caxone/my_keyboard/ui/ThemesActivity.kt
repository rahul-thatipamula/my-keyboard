package com.caxone.my_keyboard.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.keyboard.KeyboardView
import com.caxone.my_keyboard.keyboard.Layouts
import com.caxone.my_keyboard.keyboard.SuggestionStrip
import com.caxone.my_keyboard.settings.Prefs
import com.caxone.my_keyboard.theme.KeyboardTheme
import com.caxone.my_keyboard.theme.ThemeStore
import com.google.android.material.button.MaterialButton

/** Preset grid, live preview, and the four-colour custom theme builder. */
class ThemesActivity : BaseSettingsActivity() {

    override val titleRes = R.string.nav_themes

    private lateinit var previewStrip: SuggestionStrip
    private lateinit var previewKeyboard: KeyboardView
    private lateinit var themeAdapter: ThemeAdapter

    private val palette = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFF1F3F4.toInt(), 0xFFDADCE0.toInt(), 0xFF9AA0A6.toInt(), 0xFF5F6368.toInt(),
        0xFF3C4043.toInt(), 0xFF202124.toInt(), 0xFF000000.toInt(),
        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(),
        0xFF1A73E8.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(),
        0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(),
        0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF263238.toInt(), 0xFF37474F.toInt(), 0xFF0B3D5C.toInt()
    )

    override fun build() {
        paragraph(R.string.themes_body)

        // Preview
        val previewColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        previewStrip = SuggestionStrip(this)
        previewKeyboard = KeyboardView(this).apply { interactive = false }
        previewColumn.addView(previewStrip)
        previewColumn.addView(previewKeyboard)
        collapsibleCard(R.string.home_preview_title, Prefs.KEY_PREVIEW_COLLAPSED, previewColumn)

        // Presets
        section(R.string.presets_title)
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@ThemesActivity, 2)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        themeAdapter = ThemeAdapter(KeyboardTheme.PRESETS, ThemeStore.selectedId(this)) { theme ->
            ThemeStore.select(this, theme.id)
            themeAdapter.select(theme.id)
            refreshPreview()
        }
        recycler.adapter = themeAdapter
        content.addView(recycler)

        // Custom
        section(R.string.custom_title)
        paragraph(R.string.custom_body)
        val customColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val labels = listOf(R.string.custom_background, R.string.custom_keys, R.string.custom_text, R.string.custom_accent)
        val colors = ThemeStore.customColors(this)
        for (index in labels.indices) customColumn.addView(swatchRow(index, getString(labels[index]), colors[index]))
        customColumn.addView(MaterialButton(this).apply {
            setText(R.string.custom_apply)
            isAllCaps = false
            cornerRadius = dp(20)
            setOnClickListener {
                ThemeStore.select(this@ThemesActivity, KeyboardTheme.CUSTOM_ID)
                themeAdapter.select(KeyboardTheme.CUSTOM_ID)
                refreshPreview()
            }
        })
        card(customColumn)
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    private fun refreshPreview() {
        val theme = ThemeStore.current(this)
        previewKeyboard.theme = theme
        previewKeyboard.layout = Layouts.letters(Prefs.layout(this), Prefs.numberRow(this))
        previewStrip.applyTheme(theme)
        previewStrip.setSuggestions(listOf("Hello", "Hey", "How"), -1)
    }

    private fun swatchRow(index: Int, label: String, selectedColor: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply { text = label; textSize = 14f })

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val swatches = ArrayList<View>()
        for (color in palette) {
            val swatch = View(this)
            val size = dp(36)
            swatch.layoutParams = LinearLayout.LayoutParams(size, size).also { it.setMargins(dp(4), dp(6), dp(4), 0) }
            swatch.tag = color
            swatch.background = swatchDrawable(color, color == selectedColor)
            swatch.setOnClickListener {
                ThemeStore.setCustomColor(this, index, color)
                ThemeStore.select(this, KeyboardTheme.CUSTOM_ID)
                themeAdapter.select(KeyboardTheme.CUSTOM_ID)
                for (v in swatches) v.background = swatchDrawable(v.tag as Int, v === swatch)
                refreshPreview()
            }
            strip.addView(swatch)
            swatches.add(swatch)
        }
        scroll.addView(strip)
        row.addView(scroll)
        return row
    }

    private fun swatchDrawable(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            val stroke = if (selected) dp(3) else dp(1)
            val strokeColor = if (selected) 0xFF1A73E8.toInt() else Color.argb(60, 0, 0, 0)
            setStroke(stroke, strokeColor)
        }
}
