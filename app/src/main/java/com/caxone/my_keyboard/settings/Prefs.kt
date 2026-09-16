package com.caxone.my_keyboard.settings

import android.content.Context
import android.content.SharedPreferences

/** Small wrapper around the app's SharedPreferences for typing settings. */
object Prefs {
    const val NAME = "my_keyboard_prefs"

    const val KEY_AUTOCORRECT = "autocorrect"
    const val KEY_PREDICTION = "prediction"
    const val KEY_POPUP = "key_popup"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_SOUND = "sound"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun autocorrect(context: Context) = get(context).getBoolean(KEY_AUTOCORRECT, true)
    fun prediction(context: Context) = get(context).getBoolean(KEY_PREDICTION, true)
    fun keyPopup(context: Context) = get(context).getBoolean(KEY_POPUP, true)
    fun vibrate(context: Context) = get(context).getBoolean(KEY_VIBRATE, true)
    fun sound(context: Context) = get(context).getBoolean(KEY_SOUND, false)

    fun set(context: Context, key: String, value: Boolean) {
        get(context).edit().putBoolean(key, value).apply()
    }
}
