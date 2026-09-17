package com.caxone.my_keyboard.ui

import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.settings.Prefs

class FeedbackActivity : BaseSettingsActivity() {

    override val titleRes = R.string.nav_feedback

    override fun build() {
        section(R.string.nav_feedback)
        card(
            switchRow(R.string.pref_popup, R.string.pref_popup_sum, Prefs.KEY_POPUP),
            switchRow(R.string.pref_vibrate, null, Prefs.KEY_VIBRATE),
            switchRow(R.string.pref_sound, null, Prefs.KEY_SOUND)
        )
    }
}
