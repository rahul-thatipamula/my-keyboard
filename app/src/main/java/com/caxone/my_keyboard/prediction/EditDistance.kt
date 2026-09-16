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
    private val neighbours = HashMap<Char, Set<Char>>()

    init {
        val rows = ROWS_QWERTY.split('|')
        val offsets = doubleArrayOf(0.0, 0.5, 1.5)
        for (r in rows.indices) {
            for (c in rows[r].indices) {
                val set = HashSet<Char>()
                val x = c + offsets[r]
                for (r2 in rows.indices) {
                    for (c2 in rows[r2].indices) {
                        if (r == r2 && c == c2) continue
                        val x2 = c2 + offsets[r2]
                        if (abs(r - r2) <= 1 && abs(x - x2) <= 1.0) set.add(rows[r2][c2])
                    }
                }
                neighbours[rows[r][c]] = set
            }
        }
    }

    fun adjacent(a: Char, b: Char): Boolean = neighbours[a]?.contains(b) == true

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
