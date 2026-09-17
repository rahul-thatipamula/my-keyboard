package com.caxone.my_keyboard.ui

import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.settings.Prefs

class TypingActivity : BaseSettingsActivity() {

    override val titleRes = R.string.nav_typing

    override fun build() {
        section(R.string.typing_section_correction)
        card(
            switchRow(R.string.pref_autocorrect, R.string.pref_autocorrect_sum, Prefs.KEY_AUTOCORRECT),
            switchRow(R.string.pref_prediction, R.string.pref_prediction_sum, Prefs.KEY_PREDICTION),
            switchRow(R.string.pref_autocap, R.string.pref_autocap_sum, Prefs.KEY_AUTOCAP),
            switchRow(R.string.pref_double_space, R.string.pref_double_space_sum, Prefs.KEY_DOUBLE_SPACE)
        )

        section(R.string.typing_section_input)
        card(
            switchRow(R.string.pref_glide, R.string.pref_glide_sum, Prefs.KEY_GLIDE)
        )

        section(R.string.typing_section_layout)
        card(
            switchRow(R.string.pref_number_row, R.string.pref_number_row_sum, Prefs.KEY_NUMBER_ROW)
        )
    }
}
