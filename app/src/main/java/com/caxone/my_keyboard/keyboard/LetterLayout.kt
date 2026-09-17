package com.caxone.my_keyboard.keyboard

/**
 * The letter arrangements the user can pick from. Each is three rows of characters; the
 * shift / delete keys and the bottom row are added by [Layouts.letters].
 */
enum class LetterLayout(
    val id: String,
    val displayName: String,
    val description: String,
    val rows: List<String>
) {
    QWERTY(
        "qwerty", "QWERTY", "The standard layout most phones and computers use",
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    ),
    QWERTZ(
        "qwertz", "QWERTZ", "Used in German-speaking countries; Y and Z are swapped",
        listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
    ),
    AZERTY(
        "azerty", "AZERTY", "Used in France and Belgium",
        listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
    ),
    DVORAK(
        "dvorak", "Dvorak", "Vowels on the left of the home row for less finger travel",
        listOf("',.pyfgcrl", "aoeuidhtns", ";qjkxbmwvz")
    ),
    COLEMAK(
        "colemak", "Colemak", "Common letters on the home row; keeps most keys where QWERTY has them",
        listOf("qwfpgjluy;", "arstdhneio", "zxcvbkm")
    ),
    WORKMAN(
        "workman", "Workman", "A Colemak alternative that favours the strongest fingers",
        listOf("qdrwbjfup;", "ashtgyneoi", "zxmcvkl")
    );

    companion object {
        val DEFAULT = QWERTY

        fun fromId(id: String?): LetterLayout = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
