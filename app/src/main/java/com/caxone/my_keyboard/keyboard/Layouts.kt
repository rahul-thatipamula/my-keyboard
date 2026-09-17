package com.caxone.my_keyboard.keyboard

enum class KeyType { CHAR, SHIFT, DELETE, SPACE, ENTER, TO_SYMBOLS, TO_SYMBOLS2, TO_LETTERS }

/** One key on the keyboard. Geometry is filled in by [KeyboardView] when it lays out. */
class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val width: Float = 1f,
    val hint: String? = null,
    val output: String = label,
    /** Row height multiplier; the number row is shorter than letter rows. */
    val heightScale: Float = 1f
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

    private const val NUMBER_ROW_SCALE = 0.78f

    private fun chars(s: String, hints: String? = null, scale: Float = 1f): List<Key> =
        s.mapIndexed { i, c -> Key(c.toString(), hint = hints?.getOrNull(i)?.toString(), heightScale = scale) }

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

    /**
     * Letters. With [numberRow] a short row of digits sits on top and the top letter row's
     * hints become symbols instead of digits so nothing is duplicated.
     */
    fun qwerty(numberRow: Boolean = false): Layout {
        val rows = ArrayList<List<Key>>(5)
        if (numberRow) rows.add(chars("1234567890", scale = NUMBER_ROW_SCALE))
        rows.add(chars("qwertyuiop", if (numberRow) "%^~|[]<>{}" else "1234567890"))
        rows.add(chars("asdfghjkl", "@#$&*-+()"))
        rows.add(listOf(Key("⇧", KeyType.SHIFT, 1.5f)) + chars("zxcvbnm", "_\"':;!?") + Key("⌫", KeyType.DELETE, 1.5f))
        rows.add(bottomRow(KeyType.TO_SYMBOLS))
        return Layout("qwerty", rows)
    }

    fun symbols() = Layout(
        "symbols",
        listOf(
            chars("1234567890", "¹²³⁴⁵⁶⁷⁸⁹⁰"),
            chars("@#\$_&-+()/", "•£€™—±≠<>÷"),
            listOf(Key("=\\<", KeyType.TO_SYMBOLS2, 1.5f)) + chars("*\"':;!?", "†«»‚„¡¿") + Key("⌫", KeyType.DELETE, 1.5f),
            bottomRow(KeyType.TO_LETTERS)
        )
    )

    fun symbols2() = Layout(
        "symbols2",
        listOf(
            chars("~`|•√π÷×¶∆"),
            chars("£¢€¥^°={}", "₹₽₩₺∞≈≡[]"),
            listOf(Key("?123", KeyType.TO_SYMBOLS, 1.5f)) + chars("\\©®™✓[]", "§℠℗☆‹›") + Key("⌫", KeyType.DELETE, 1.5f),
            bottomRow(KeyType.TO_LETTERS)
        )
    )
}
