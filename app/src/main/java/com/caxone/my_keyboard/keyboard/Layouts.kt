package com.caxone.my_keyboard.keyboard

enum class KeyType { CHAR, SHIFT, DELETE, SPACE, ENTER, TO_SYMBOLS, TO_SYMBOLS2, TO_LETTERS }

/** One key on the keyboard. Geometry is filled in by [KeyboardView] when it lays out. */
class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val width: Float = 1f,
    val hint: String? = null,
    val output: String = label
) {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f

    val isFunction: Boolean get() = type != KeyType.CHAR && type != KeyType.SPACE
    val isLetter: Boolean get() = type == KeyType.CHAR && output.length == 1 && output[0].isLetter()

    fun contains(px: Float, py: Float) = px >= x && px < x + w && py >= y && py < y + h
}

class Layout(val id: String, val rows: List<List<Key>>) {
    val keys: List<Key> = rows.flatten()
    val isLetters: Boolean get() = id == "qwerty"
}

/** Gboard-style layouts. Fresh instances are returned because keys carry geometry. */
object Layouts {

    private fun chars(s: String, hints: String? = null): List<Key> =
        s.mapIndexed { i, c -> Key(c.toString(), hint = hints?.getOrNull(i)?.toString()) }

    private fun bottomRow(switch: KeyType): List<Key> {
        val switchLabel = if (switch == KeyType.TO_LETTERS) "ABC" else "?123"
        return listOf(
            Key(switchLabel, switch, 1.5f),
            Key(",", hint = "!"),
            Key(" ", KeyType.SPACE, 5f, output = " "),
            Key(".", hint = "?"),
            Key("↵", KeyType.ENTER, 1.5f)
        )
    }

    fun qwerty() = Layout(
        "qwerty",
        listOf(
            chars("qwertyuiop", "1234567890"),
            chars("asdfghjkl", "@#$%&-+()"),
            listOf(Key("⇧", KeyType.SHIFT, 1.5f)) + chars("zxcvbnm", "*\"':;!?") + Key("⌫", KeyType.DELETE, 1.5f),
            bottomRow(KeyType.TO_SYMBOLS)
        )
    )

    fun symbols() = Layout(
        "symbols",
        listOf(
            chars("1234567890"),
            chars("@#\$_&-+()/"),
            listOf(Key("=\\<", KeyType.TO_SYMBOLS2, 1.5f)) + chars("*\"':;!?") + Key("⌫", KeyType.DELETE, 1.5f),
            bottomRow(KeyType.TO_LETTERS)
        )
    )

    fun symbols2() = Layout(
        "symbols2",
        listOf(
            chars("~`|•√π÷×¶∆"),
            chars("£¢€¥^°={}"),
            listOf(Key("?123", KeyType.TO_SYMBOLS, 1.5f)) + chars("\\©®™✓[]") + Key("⌫", KeyType.DELETE, 1.5f),
            bottomRow(KeyType.TO_LETTERS)
        )
    )
}
