package com.caxone.my_keyboard.ui

import android.widget.LinearLayout
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.keyboard.KeyboardView
import com.caxone.my_keyboard.keyboard.LetterLayout
import com.caxone.my_keyboard.keyboard.Layouts
import com.caxone.my_keyboard.keyboard.SuggestionStrip
import com.caxone.my_keyboard.settings.Prefs
import com.caxone.my_keyboard.theme.ThemeStore
import com.google.android.material.radiobutton.MaterialRadioButton

/** Pick a letter arrangement; the preview redraws as soon as one is tapped. */
class LayoutActivity : BaseSettingsActivity() {

    override val titleRes = R.string.nav_layout

    private lateinit var previewStrip: SuggestionStrip
    private lateinit var previewKeyboard: KeyboardView
    private val radios = HashMap<LetterLayout, MaterialRadioButton>()

    override fun build() {
        paragraph(R.string.layout_body)

        val previewColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        previewStrip = SuggestionStrip(this)
        previewKeyboard = KeyboardView(this).apply { interactive = false }
        previewColumn.addView(previewStrip)
        previewColumn.addView(previewKeyboard)
        collapsibleCard(R.string.home_preview_title, Prefs.KEY_PREVIEW_COLLAPSED, previewColumn)

        section(R.string.layout_section_choose)
        val current = Prefs.layout(this)
        val rows = LetterLayout.entries.map { layout ->
            radioRow(layout.displayName, layout.description, layout == current) { select(layout) }
                .also { radios[layout] = it.findViewById(R.id.radio) }
        }
        card(*rows.toTypedArray())

        section(R.string.typing_section_layout)
        card(switchRow(R.string.pref_number_row, R.string.pref_number_row_sum, Prefs.KEY_NUMBER_ROW))
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    private fun select(layout: LetterLayout) {
        Prefs.setLayout(this, layout)
        for ((l, radio) in radios) radio.isChecked = l == layout
        refreshPreview()
    }

    private fun refreshPreview() {
        val theme = ThemeStore.current(this)
        previewKeyboard.theme = theme
        previewKeyboard.layout = Layouts.letters(Prefs.layout(this), Prefs.numberRow(this))
        previewStrip.applyTheme(theme)
        previewStrip.setSuggestions(listOf("Hello", "Hey", "How"), -1)
    }
}
