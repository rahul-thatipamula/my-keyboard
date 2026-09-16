package com.caxone.my_keyboard.theme

import android.content.Context
import com.caxone.my_keyboard.settings.Prefs

/** Persists the selected theme and the user's custom colours. */
object ThemeStore {
    private const val KEY_THEME = "theme_id"
    private const val KEY_CUSTOM_BG = "custom_bg"
    private const val KEY_CUSTOM_KEY = "custom_key"
    private const val KEY_CUSTOM_TEXT = "custom_text"
    private const val KEY_CUSTOM_ACCENT = "custom_accent"

    fun selectedId(context: Context): String =
        Prefs.get(context).getString(KEY_THEME, KeyboardTheme.LIGHT.id) ?: KeyboardTheme.LIGHT.id

    fun select(context: Context, id: String) {
        Prefs.get(context).edit().putString(KEY_THEME, id).apply()
    }

    fun current(context: Context): KeyboardTheme {
        val id = selectedId(context)
        if (id == KeyboardTheme.CUSTOM_ID) return customTheme(context)
        return KeyboardTheme.PRESETS.firstOrNull { it.id == id } ?: KeyboardTheme.LIGHT
    }

    /** Custom colours in the order: background, key, text, accent. */
    fun customColors(context: Context): IntArray {
        val p = Prefs.get(context)
        return intArrayOf(
            p.getInt(KEY_CUSTOM_BG, 0xFF263238.toInt()),
            p.getInt(KEY_CUSTOM_KEY, 0xFF37474F.toInt()),
            p.getInt(KEY_CUSTOM_TEXT, 0xFFFFFFFF.toInt()),
            p.getInt(KEY_CUSTOM_ACCENT, 0xFFFF9800.toInt())
        )
    }

    fun setCustomColor(context: Context, index: Int, color: Int) {
        val key = when (index) {
            0 -> KEY_CUSTOM_BG
            1 -> KEY_CUSTOM_KEY
            2 -> KEY_CUSTOM_TEXT
            else -> KEY_CUSTOM_ACCENT
        }
        Prefs.get(context).edit().putInt(key, color).apply()
    }

    fun customTheme(context: Context): KeyboardTheme {
        val c = customColors(context)
        return KeyboardTheme.custom(c[0], c[1], c[2], c[3])
    }
}
