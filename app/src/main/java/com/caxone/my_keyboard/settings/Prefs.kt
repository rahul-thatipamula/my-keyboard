package com.caxone.my_keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import com.caxone.my_keyboard.keyboard.LetterLayout

/** Small wrapper around the app's SharedPreferences for typing settings. */
object Prefs {
    const val NAME = "my_keyboard_prefs"

    const val KEY_AUTOCORRECT = "autocorrect"
    const val KEY_PREDICTION = "prediction"
    const val KEY_GLIDE = "glide"
    const val KEY_NUMBER_ROW = "number_row"
    const val KEY_LAYOUT = "layout"
    const val KEY_AUTOCAP = "autocap"
    const val KEY_DOUBLE_SPACE = "double_space_period"
    const val KEY_POPUP = "key_popup"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_SOUND = "sound"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun autocorrect(context: Context) = get(context).getBoolean(KEY_AUTOCORRECT, true)
    fun prediction(context: Context) = get(context).getBoolean(KEY_PREDICTION, true)
    fun glide(context: Context) = get(context).getBoolean(KEY_GLIDE, true)
    fun numberRow(context: Context) = get(context).getBoolean(KEY_NUMBER_ROW, false)
    fun layout(context: Context): LetterLayout = LetterLayout.fromId(get(context).getString(KEY_LAYOUT, null))
    fun autoCap(context: Context) = get(context).getBoolean(KEY_AUTOCAP, true)
    fun doubleSpacePeriod(context: Context) = get(context).getBoolean(KEY_DOUBLE_SPACE, true)
    fun keyPopup(context: Context) = get(context).getBoolean(KEY_POPUP, true)
    fun vibrate(context: Context) = get(context).getBoolean(KEY_VIBRATE, true)
    fun sound(context: Context) = get(context).getBoolean(KEY_SOUND, false)

    /** Default for any boolean key, used by the settings screens. */
    fun default(key: String): Boolean = when (key) {
        KEY_NUMBER_ROW, KEY_SOUND -> false
        else -> true
    }

    fun set(context: Context, key: String, value: Boolean) {
        get(context).edit().putBoolean(key, value).apply()
    }

    fun setLayout(context: Context, layout: LetterLayout) {
        get(context).edit().putString(KEY_LAYOUT, layout.id).apply()
    }
}
