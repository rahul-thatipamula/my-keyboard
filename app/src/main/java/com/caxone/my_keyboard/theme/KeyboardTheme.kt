package com.caxone.my_keyboard.theme

import android.graphics.Color

/** Every colour the keyboard needs to draw itself. */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val background: Int,
    val keyBackground: Int,
    val functionKeyBackground: Int,
    val keyPressed: Int,
    val keyText: Int,
    val hintText: Int,
    val accent: Int,
    val accentText: Int,
    val suggestionText: Int,
    val isDark: Boolean
) {
    companion object {
        private fun c(hex: Long) = hex.toInt()

        val LIGHT = KeyboardTheme(
            id = "light", name = "Light",
            background = c(0xFFE8EAED), keyBackground = c(0xFFFFFFFF), functionKeyBackground = c(0xFFCDD3DA),
            keyPressed = c(0xFFD2D5D9), keyText = c(0xFF202124), hintText = c(0xFF80868B),
            accent = c(0xFF1A73E8), accentText = c(0xFFFFFFFF), suggestionText = c(0xFF202124), isDark = false
        )
        val DARK = KeyboardTheme(
            id = "dark", name = "Dark",
            background = c(0xFF202124), keyBackground = c(0xFF3C4043), functionKeyBackground = c(0xFF2A2C2F),
            keyPressed = c(0xFF5F6368), keyText = c(0xFFE8EAED), hintText = c(0xFF9AA0A6),
            accent = c(0xFF8AB4F8), accentText = c(0xFF202124), suggestionText = c(0xFFE8EAED), isDark = true
        )
        val MIDNIGHT = KeyboardTheme(
            id = "midnight", name = "Midnight",
            background = c(0xFF000000), keyBackground = c(0xFF1F1F1F), functionKeyBackground = c(0xFF111111),
            keyPressed = c(0xFF3A3A3A), keyText = c(0xFFFFFFFF), hintText = c(0xFF8A8A8A),
            accent = c(0xFF00E5FF), accentText = c(0xFF000000), suggestionText = c(0xFFFFFFFF), isDark = true
        )
        val OCEAN = KeyboardTheme(
            id = "ocean", name = "Ocean",
            background = c(0xFF0B3D5C), keyBackground = c(0xFF155E86), functionKeyBackground = c(0xFF0F4B6E),
            keyPressed = c(0xFF1E76A7), keyText = c(0xFFFFFFFF), hintText = c(0xFF9BC5DC),
            accent = c(0xFF34D1BF), accentText = c(0xFF062A3F), suggestionText = c(0xFFE6F4FA), isDark = true
        )
        val SUNSET = KeyboardTheme(
            id = "sunset", name = "Sunset",
            background = c(0xFF3A1C3D), keyBackground = c(0xFF6B2D5C), functionKeyBackground = c(0xFF4E2249),
            keyPressed = c(0xFF83396F), keyText = c(0xFFFFE7F0), hintText = c(0xFFE0A9C6),
            accent = c(0xFFFF7B54), accentText = c(0xFF2E0F2A), suggestionText = c(0xFFFFE7F0), isDark = true
        )
        val FOREST = KeyboardTheme(
            id = "forest", name = "Forest",
            background = c(0xFF1B3A2A), keyBackground = c(0xFF2E5C42), functionKeyBackground = c(0xFF24492F),
            keyPressed = c(0xFF3C7554), keyText = c(0xFFE9F5EC), hintText = c(0xFFA8CDB3),
            accent = c(0xFF7CDE8B), accentText = c(0xFF0F2A18), suggestionText = c(0xFFE9F5EC), isDark = true
        )
        val ROSE = KeyboardTheme(
            id = "rose", name = "Rose",
            background = c(0xFFFCE4EC), keyBackground = c(0xFFFFFFFF), functionKeyBackground = c(0xFFF8BBD0),
            keyPressed = c(0xFFF3C7D5), keyText = c(0xFF4A1030), hintText = c(0xFFA0607A),
            accent = c(0xFFE91E63), accentText = c(0xFFFFFFFF), suggestionText = c(0xFF4A1030), isDark = false
        )
        val LAVENDER = KeyboardTheme(
            id = "lavender", name = "Lavender",
            background = c(0xFFEDE7F6), keyBackground = c(0xFFFFFFFF), functionKeyBackground = c(0xFFD1C4E9),
            keyPressed = c(0xFFDAD0EE), keyText = c(0xFF311B92), hintText = c(0xFF8878A8),
            accent = c(0xFF7E57C2), accentText = c(0xFFFFFFFF), suggestionText = c(0xFF311B92), isDark = false
        )

        val PRESETS = listOf(LIGHT, DARK, MIDNIGHT, OCEAN, SUNSET, FOREST, ROSE, LAVENDER)

        const val CUSTOM_ID = "custom"

        /** Derives a full theme from the four colours the user picks. */
        fun custom(background: Int, key: Int, text: Int, accent: Int): KeyboardTheme {
            val dark = luminance(background) < 0.5
            return KeyboardTheme(
                id = CUSTOM_ID, name = "Custom",
                background = background,
                keyBackground = key,
                functionKeyBackground = blend(key, background, 0.55f),
                keyPressed = blend(key, text, 0.25f),
                keyText = text,
                hintText = blend(text, key, 0.45f),
                accent = accent,
                accentText = if (luminance(accent) < 0.5) Color.WHITE else Color.BLACK,
                suggestionText = text,
                isDark = dark
            )
        }

        fun blend(a: Int, b: Int, ratio: Float): Int {
            val inv = 1f - ratio
            val r = Color.red(a) * inv + Color.red(b) * ratio
            val g = Color.green(a) * inv + Color.green(b) * ratio
            val bl = Color.blue(a) * inv + Color.blue(b) * ratio
            return Color.rgb(r.toInt(), g.toInt(), bl.toInt())
        }

        fun luminance(color: Int): Double =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
    }
}
