package com.caxone.my_keyboard.prediction

import kotlin.math.abs
import kotlin.math.min

/**
 * Damerau-Levenshtein distance weighted for touch typing:
 * substituting a key with one of its physical neighbours is cheap,
 * and swapping two adjacent letters is cheaper than two edits.
 */
object EditDistance {

    private const val ROWS_QWERTY = "qwertyuiop|asdfghjkl|zxcvbnm"

    @Volatile
    private var neighbours: Map<Char, Set<Char>> = build(qwertyPositions())

    /** Standard QWERTY, used until the keyboard installs the layout the user picked. */
    private fun qwertyPositions(): Map<Char, Pair<Int, Float>> {
        val rows = ROWS_QWERTY.split('|')
        val offsets = floatArrayOf(0f, 0.5f, 1.5f)
        val positions = HashMap<Char, Pair<Int, Float>>()
        for (r in rows.indices) for (c in rows[r].indices) positions[rows[r][c]] = r to c + offsets[r] + 0.5f
        return positions
    }

    /**
     * Rebuilds the neighbour map for a letter layout. [positions] maps each character to its
     * (row, horizontal centre in key widths); two keys are neighbours when they sit on the same
     * or an adjacent row and within one key width of each other.
     */
    fun useLayout(positions: Map<Char, Pair<Int, Float>>) {
        neighbours = build(positions)
    }

    private fun build(positions: Map<Char, Pair<Int, Float>>): Map<Char, Set<Char>> {
        val map = HashMap<Char, Set<Char>>()
        for ((a, pa) in positions) {
            val set = HashSet<Char>()
            for ((b, pb) in positions) {
                if (a == b) continue
                if (abs(pa.first - pb.first) <= 1 && abs(pa.second - pb.second) <= 1.0f) set.add(b)
            }
            map[a] = set
        }
        return map
    }

    fun adjacent(a: Char, b: Char): Boolean = neighbours[a]?.contains(b) == true

    fun neighboursOf(c: Char): Set<Char> = neighbours[c] ?: emptySet()

    /** Returns the weighted distance, or a value > [max] as soon as it is certain to exceed it. */
    fun distance(a: String, b: String, max: Double): Double {
        val n = a.length
        val m = b.length
        if (abs(n - m) > max) return max + 1
        var prevPrev: DoubleArray? = null
        var prev = DoubleArray(m + 1) { it.toDouble() }
        var cur = DoubleArray(m + 1)
        for (i in 1..n) {
            cur[0] = i.toDouble()
            var rowMin = cur[0]
            val ca = a[i - 1]
            for (j in 1..m) {
                val cb = b[j - 1]
                val subCost = when {
                    ca == cb -> 0.0
                    adjacent(ca, cb) -> 0.6
                    else -> 1.0
                }
                var v = min(min(prev[j] + 1.0, cur[j - 1] + 1.0), prev[j - 1] + subCost)
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
                    v = min(v, prevPrev!![j - 2] + 0.8)
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val tmp = prevPrev ?: DoubleArray(m + 1)
            prevPrev = prev
            prev = cur
            cur = tmp
        }
        return prev[m]
    }
}
