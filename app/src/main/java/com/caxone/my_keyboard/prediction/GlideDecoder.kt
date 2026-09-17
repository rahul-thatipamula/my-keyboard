package com.caxone.my_keyboard.prediction

import kotlin.math.abs
import kotlin.math.ln

/**
 * Turns the sequence of letter keys a finger passed over into word candidates.
 *
 * The path is the deduplicated list of keys touched, and [GlidePath.corners] marks the
 * keys where the finger changed direction sharply (plus the first and last key). Corners are
 * the letters the user almost certainly meant; everything else may just be on the way.
 */
class GlideDecoder(private val dictionary: LanguageEngine, private val user: UserModel) {

    class GlidePath(val letters: String, val corners: BooleanArray)

    fun decode(path: GlidePath, limit: Int = 3): List<String> {
        val letters = path.letters
        if (letters.isEmpty()) return emptyList()
        if (letters.length == 1) return listOf(letters)

        val first = letters[0]
        val last = letters[letters.length - 1]
        val cornerLetters = letters.filterIndexed { i, _ -> path.corners[i] }

        val scored = HashMap<String, Double>()
        fun consider(w: String, firstPenalty: Double) {
            if (w.length < 2 || w in scored) return
            val wl = w[w.length - 1]
            val lastPenalty = when {
                wl == last -> 0.0
                EditDistance.adjacent(wl, last) -> 1.2
                else -> return
            }
            if (!isSubsequence(w, letters)) return

            var s = dictionary.frequency(w) / 100.0
            val u = user.count(w)
            if (u > 0) s = maxOf(s, 2.0) + 1.2 * ln(1.0 + u)
            s -= firstPenalty + lastPenalty

            // Every corner the finger made should show up in the word.
            var missed = 0
            for (c in cornerLetters) if (c !in w) missed++
            s -= 1.6 * missed

            // Words much longer or shorter than the number of deliberate turns are unlikely.
            s -= 0.25 * abs(w.length - (cornerLetters.length + 1))
            scored[w] = s
        }

        for (w in dictionary.wordsStartingWith(first)) consider(w, 0.0)
        for (w in user.wordsStartingWith(first)) consider(w, 0.0)
        for (c in EditDistance.neighboursOf(first)) {
            for (w in dictionary.wordsStartingWith(c)) consider(w, 1.2)
        }

        return scored.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /**
     * True when every letter of [word] appears in [path] in order. A doubled letter in the
     * word ("hello") may reuse the same path key, since the finger only visits it once.
     */
    private fun isSubsequence(word: String, path: String): Boolean {
        var p = 0
        var i = 0
        while (i < word.length) {
            val c = word[i]
            if (i > 0 && c == word[i - 1]) { i++; continue }
            while (p < path.length && path[p] != c) p++
            if (p == path.length) return false
            p++
            i++
        }
        return true
    }
}
