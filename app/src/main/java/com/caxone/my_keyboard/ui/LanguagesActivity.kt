package com.caxone.my_keyboard.ui

import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.prediction.LanguageEngine
import com.caxone.my_keyboard.settings.Prefs
import com.google.android.material.radiobutton.MaterialRadioButton

/** Tell the intelligent layer which languages you type; the keyboard adapts on the next field. */
class LanguagesActivity : BaseSettingsActivity() {

    override val titleRes = R.string.nav_languages

    private val radios = HashMap<LanguageEngine.Mode, MaterialRadioButton>()

    override fun build() {
        paragraph(R.string.languages_body)

        section(R.string.languages_section_choose)
        val current = Prefs.languages(this)
        val rows = LanguageEngine.Mode.entries.map { mode ->
            radioRow(getString(titleFor(mode)), getString(summaryFor(mode)), mode == current) { select(mode) }
                .also { radios[mode] = it.findViewById(R.id.radio) }
        }
        card(*rows.toTypedArray())

        section(R.string.languages_section_how)
        paragraph(R.string.languages_how_body)
    }

    private fun select(mode: LanguageEngine.Mode) {
        Prefs.setLanguages(this, mode)
        for ((m, radio) in radios) radio.isChecked = m == mode
    }

    companion object {
        fun titleFor(mode: LanguageEngine.Mode) = when (mode) {
            LanguageEngine.Mode.ENGLISH -> R.string.languages_english
            LanguageEngine.Mode.TELUGU -> R.string.languages_telugu
            LanguageEngine.Mode.BOTH -> R.string.languages_both
        }

        fun summaryFor(mode: LanguageEngine.Mode) = when (mode) {
            LanguageEngine.Mode.ENGLISH -> R.string.languages_english_sum
            LanguageEngine.Mode.TELUGU -> R.string.languages_telugu_sum
            LanguageEngine.Mode.BOTH -> R.string.languages_both_sum
        }
    }
}
