package com.caxone.my_keyboard.prediction

import kotlin.math.ln
import kotlin.math.max

/**
 * Combines the built-in dictionary with the user's personal model to produce
 * next-word predictions, completions, and autocorrect candidates.
 */
class Predictor(private val dictionary: Dictionary, private val user: UserModel) {

    private val phrases = PhraseBuilder(user::after, user::after)

    /**
     * [words] always has three entries (may be empty strings). [primary] is the index of the
     * word that will be committed on space; [autoCorrect] tells whether that differs from what was typed.
     */
    class Result(val words: List<String>, val primary: Int, val autoCorrect: Boolean)

    // ---- next word -------------------------------------------------------------------------

    fun nextWords(prev2: String?, prev1: String?): List<String> {
        val p1 = prev1 ?: SENTENCE_START
        val scores = HashMap<String, Double>()
        fun add(w: String, s: Double) { scores[w] = (scores[w] ?: 0.0) + s }

        if (prev2 != null) {
            for ((w, c) in user.after(prev2, p1)) add(w, 4.0 + 2.0 * ln(1.0 + c))
        }
        for ((w, c) in user.after(p1)) add(w, 2.5 + 1.5 * ln(1.0 + c))
        for ((w, c) in dictionary.seedNext(p1)) add(w, 1.0 + c / 300.0)

        if (scores.size < 3) {
            for ((w, c) in dictionary.seedNext(SENTENCE_START)) if (w !in scores) add(w, 0.2 + c / 2000.0)
        }
        return scores.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    /**
     * Words the user model confidently expects after [word] (which follows [prev1]), so a
     * suggestion can be shown and committed as a phrase. Empty when the model is unsure.
     */
    fun phraseAfter(prev1: String?, word: String): List<String> =
        if (word.isEmpty()) emptyList() else phrases.extend(prev1, word.lowercase())

    // ---- composing --------------------------------------------------------------------------

    fun forComposing(typedRaw: String, prev2: String?, prev1: String?, autocorrectEnabled: Boolean): Result {
        val typed = typedRaw.lowercase()
        val p1 = prev1 ?: SENTENCE_START
        val tri = if (prev2 != null) user.after(prev2, p1) else emptyMap()
        val bi = user.after(p1)
        val seed = dictionary.seedNext(p1).toMap()

        fun context(w: String): Double {
            var s = 0.0
            tri[w]?.let { s += 3.0 + ln(1.0 + it) }
            bi[w]?.let { s += 2.0 + ln(1.0 + it) }
            seed[w]?.let { s += 1.0 + it / 600.0 }
            return s
        }

        fun base(w: String): Double {
            val f = dictionary.frequency(w)
            val u = user.count(w)
            var s = if (f > 0) f / 100.0 else 0.0
            if (u > 0) s = max(s, 2.5) + 1.5 * ln(1.0 + u)
            return s
        }

        val scores = HashMap<String, Double>()
        val distances = HashMap<String, Double>()

        // Completions
        for (w in user.completions(typed, 8)) {
            scores[w] = base(w) + context(w) - 0.35 * (w.length - typed.length)
            distances[w] = 0.0
        }
        for (w in dictionary.completions(typed, 12)) {
            if (w == typed || w in scores) continue
            scores[w] = base(w) + context(w) - 0.35 * (w.length - typed.length)
            distances[w] = 0.0
        }

        // Corrections
        if (typed.length >= 2) {
            val maxD = when {
                typed.length <= 3 -> 1.0
                typed.length <= 6 -> 1.6
                else -> 2.2
            }
            val candidates = ArrayList<String>()
            for (len in (typed.length - 1)..(typed.length + 1)) candidates.addAll(dictionary.wordsOfLength(len))
            candidates.addAll(user.wordsNearLength(typed.length))
            for (w in candidates) {
                if (w == typed || w in scores || !prefilter(typed, w)) continue
                val d = EditDistance.distance(typed, w, maxD)
                if (d <= maxD) {
                    scores[w] = base(w) + context(w) - 2.2 * d
                    distances[w] = d
                }
            }
        }

        val typedKnown = dictionary.contains(typed) || user.count(typed) > 0 || typed.length == 1
        val ranked = scores.entries.sortedByDescending { it.value }.map { it.key }
        val best = ranked.firstOrNull()

        var autoCorrect = false
        if (autocorrectEnabled && !typedKnown && best != null && typed.length >= 2) {
            val d = distances[best] ?: 0.0
            val lengthDiff = best.length - typed.length
            val strong = (scores[best] ?: 0.0) > 1.5
            val looksLikeName = typedRaw[0].isUpperCase() && prev1 != null && typedRaw.drop(1).any { it.isLowerCase() }
            if (strong && !looksLikeName && (d > 0 || lengthDiff <= 1)) autoCorrect = true
        }

        val words = ArrayList<String>(3)
        if (autoCorrect) {
            words.add(typedRaw)
            words.add(best!!)
            words.add(ranked.getOrNull(1) ?: "")
        } else {
            words.add(ranked.getOrNull(0) ?: "")
            words.add(typedRaw)
            words.add(ranked.getOrNull(1) ?: "")
        }
        return Result(words, 1, autoCorrect)
    }

    /** Cheap filter so we only run edit distance on plausible candidates. */
    private fun prefilter(typed: String, w: String): Boolean {
        if (w.isEmpty()) return false
        val t0 = typed[0]
        val w0 = w[0]
        if (t0 == w0 || EditDistance.adjacent(t0, w0)) return true
        if (typed.length > 1 && w.length > 1) {
            if (typed[1] == w[1]) return true
            if (typed[0] == w[1] && typed[1] == w[0]) return true
        }
        return false
    }

    companion object {
        const val SENTENCE_START = "<s>"

        /** Phrase suggestions: how far to extend a word and how sure the model must be. */
        const val PHRASE_MAX_EXTRA = 3
        const val PHRASE_MAX_CHARS = 26
        /** A follower must have been typed this often in its context before it is offered. */
        const val PHRASE_MIN_COUNT = 2
        /** …and must account for at least this share of everything typed in that context. */
        const val PHRASE_DOMINANCE = 0.5
    }
}
