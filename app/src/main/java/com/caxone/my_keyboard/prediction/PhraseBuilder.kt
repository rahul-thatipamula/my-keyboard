package com.caxone.my_keyboard.prediction

/**
 * Grows a single suggested word into a short phrase by repeatedly asking the personal model
 * what usually comes next, and stopping as soon as it is no longer sure. Only the user's own
 * trigrams and bigrams are consulted, so phrases are things this user actually types.
 *
 * [trigram] and [bigram] return follower → count maps for a context, like [UserModel.after].
 */
class PhraseBuilder(
    private val trigram: (String, String) -> Map<String, Int>,
    private val bigram: (String) -> Map<String, Int>
) {

    /**
     * Words to append after [word] (given the word before it, [prev1]). Empty when the model
     * has no confident continuation.
     */
    fun extend(prev1: String?, word: String, maxExtra: Int = Predictor.PHRASE_MAX_EXTRA): List<String> {
        val extra = ArrayList<String>(maxExtra)
        var p2: String? = prev1
        var p1 = word
        var length = word.length
        while (extra.size < maxExtra) {
            val next = confidentNext(p2, p1) ?: break
            if (next == p1) break // "very very" is more likely noise than intent
            length += 1 + next.length
            if (length > Predictor.PHRASE_MAX_CHARS) break
            extra.add(next)
            p2 = p1
            p1 = next
        }
        return extra
    }

    /** The single follower that dominates its context, or null when nothing does. */
    private fun confidentNext(p2: String?, p1: String): String? {
        val tri = if (p2 != null) trigram(p2, p1) else emptyMap()
        dominant(tri)?.let { return it }
        return dominant(bigram(p1))
    }

    private fun dominant(followers: Map<String, Int>): String? {
        if (followers.isEmpty()) return null
        var best: String? = null
        var bestCount = 0
        var total = 0
        for ((w, c) in followers) {
            total += c
            if (c > bestCount) { best = w; bestCount = c }
        }
        if (bestCount < Predictor.PHRASE_MIN_COUNT) return null
        if (bestCount < Predictor.PHRASE_DOMINANCE * total) return null
        return best
    }
}
