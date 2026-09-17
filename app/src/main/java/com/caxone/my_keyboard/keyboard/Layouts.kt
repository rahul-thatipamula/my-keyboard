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

class Layout(val id: String, val rows: List<List<Key>>, val isLetters: Boolean = false) {
    val keys: List<Key> = rows.flatten()
}

/** Gboard-style layouts. Fresh instances are returned because keys carry geometry. */
object Layouts {

    private const val NUMBER_ROW_SCALE = 0.78f

    /** A row is laid out on a grid this many standard keys wide; wider rows are shrunk to fit. */
    const val ROW_UNITS = 10f

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

    /** Long-press hints for the three letter rows; a hint is dropped when a row is shorter. */
    private const val HINTS_TOP_DIGITS = "1234567890"
    private const val HINTS_TOP_SYMBOLS = "%^~|[]<>{}"
    private const val HINTS_MIDDLE = "@#$&*-+()/"
    private const val HINTS_BOTTOM = "_\"':;!?<>"

    /**
     * Letters in the given [layout]. With [numberRow] a short row of digits sits on top and the
     * top letter row's hints become symbols instead of digits so nothing is duplicated.
     */
    fun letters(layout: LetterLayout = LetterLayout.DEFAULT, numberRow: Boolean = false): Layout {
        val (top, middle, bottom) = layout.rows
        val rows = ArrayList<List<Key>>(5)
        if (numberRow) rows.add(chars("1234567890", scale = NUMBER_ROW_SCALE))
        rows.add(chars(top, if (numberRow) HINTS_TOP_SYMBOLS else HINTS_TOP_DIGITS))
        rows.add(chars(middle, HINTS_MIDDLE))
        // A ten-letter bottom row (Dvorak) needs slimmer shift / delete keys to fit.
        val side = if (bottom.length >= 10) 1f else 1.5f
        rows.add(listOf(Key("⇧", KeyType.SHIFT, side)) + chars(bottom, HINTS_BOTTOM) + Key("⌫", KeyType.DELETE, side))
        rows.add(bottomRow(KeyType.TO_SYMBOLS))
        return Layout(layout.id, rows, isLetters = true)
    }

    /**
     * Where each character of [layout] sits, as (letter row index, horizontal centre in key
     * widths), using the same centring and overflow rules as [KeyboardView]. Feeds the
     * key-adjacency map used by autocorrect and glide decoding.
     */
    fun letterPositions(layout: LetterLayout): Map<Char, Pair<Int, Float>> {
        val positions = HashMap<Char, Pair<Int, Float>>()
        val letterRows = letters(layout).rows.filter { row -> row.any { it.isLetter } }
        for ((r, row) in letterRows.withIndex()) {
            val total = row.map { it.width }.sum()
            val scale = if (total > ROW_UNITS) ROW_UNITS / total else 1f
            var x = maxOf(0f, (ROW_UNITS - total) / 2f)
            for (key in row) {
                if (key.type == KeyType.CHAR && key.output.length == 1) {
                    positions[key.output[0]] = r to (x + key.width / 2f) * scale
                }
                x += key.width
            }
        }
        return positions
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
