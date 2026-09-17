package com.caxone.my_keyboard.prediction

import android.content.Context

/**
 * The intelligent layer under the predictor. It owns one built-in lexicon per enabled
 * [Language] plus the user's personal model, works out which language the current sentence
 * is in from the words before the cursor, and answers every lookup the predictor and glide
 * decoder make with that bias applied — so after "nuvvu ela" the strip leans Tenglish, and
 * after "how are" it leans English, with words the user has taught it in either.
 *
 * Everything here is plain lookup tables and counts on the phone. No model is downloaded and
 * nothing is sent anywhere.
 */
class LanguageEngine(private val context: Context, private val user: UserModel) {

    /** Which languages the user asked for. */
    enum class Mode(val id: String) {
        ENGLISH("english"), TELUGU("telugu"), BOTH("both");

        val languages: List<Language>
            get() = when (this) {
                ENGLISH -> listOf(Language.ENGLISH)
                TELUGU -> listOf(Language.TELUGU, Language.ENGLISH)
                BOTH -> listOf(Language.ENGLISH, Language.TELUGU)
            }

        companion object {
            fun fromId(id: String?): Mode = entries.firstOrNull { it.id == id } ?: BOTH
        }
    }

    private val lexicons = HashMap<Language, Dictionary>()

    @Volatile
    var mode: Mode = Mode.BOTH
        set(value) { field = value; weights = weightsFor(value, contextLanguage) }

    /** The language the words before the cursor are in, or null when it is unclear. */
    @Volatile
    var contextLanguage: Language? = null
        private set

    @Volatile
    private var weights: Map<Language, Double> = weightsFor(Mode.BOTH, null)

    @Volatile
    var isLoaded = false
        private set

    val teluguEnabled: Boolean get() = mode != Mode.ENGLISH

    /** Loads every lexicon once; safe to call again after [mode] gains a language. */
    fun load() {
        for (lang in Language.entries) {
            lexicons.getOrPut(lang) { Dictionary(context, lang) }.load()
        }
        isLoaded = true
    }

    // ---- context ----------------------------------------------------------------------------

    /** Re-reads the sentence language from the last two words and updates the lookup bias. */
    fun setContext(prev2: String?, prev1: String?) {
        val votes = HashMap<Language, Int>()
        prev1?.let { languageOf(it) }?.let { votes[it] = (votes[it] ?: 0) + 2 }
        prev2?.let { languageOf(it) }?.let { votes[it] = (votes[it] ?: 0) + 1 }
        val lang = votes.maxByOrNull { it.value }?.key?.takeIf { teluguEnabled }
        contextLanguage = lang
        weights = weightsFor(mode, lang)
    }

    /**
     * The language [word] belongs to: the lexicon it is only in, the tag the user model gave it,
     * or null when it is in both (loanwords like "office") or in neither.
     */
    fun languageOf(word: String): Language? {
        var found: Language? = null
        for ((lang, dict) in lexicons) {
            if (dict.contains(word)) {
                if (found != null) return null
                found = lang
            }
        }
        return found ?: user.languageOf(word)
    }

    /** Best guess for tagging a word the user just typed: its lexicon, else the sentence language. */
    fun guessLanguage(word: String): Language? = languageOf(word) ?: contextLanguage

    private fun active(): List<Dictionary> = mode.languages.mapNotNull { lexicons[it] }

    private fun weight(lang: Language): Double = weights[lang] ?: 0.0

    // ---- lookups (the Dictionary API, merged and biased) --------------------------------------

    /** The word's frequency scaled by how well its language fits the sentence. */
    fun frequency(word: String): Int {
        var best = 0.0
        for (d in active()) {
            val f = d.frequency(word) * weight(d.language)
            if (f > best) best = f
        }
        return best.toInt()
    }

    fun contains(word: String): Boolean = active().any { it.contains(word) }

    fun wordsOfLength(len: Int): List<String> = active().flatMap { it.wordsOfLength(len) }

    fun wordsStartingWith(c: Char): List<String> = active().flatMap { it.wordsStartingWith(c) }

    fun seedNext(prev: String): List<Pair<String, Int>> {
        val merged = HashMap<String, Int>()
        for (d in active()) {
            val w = weight(d.language)
            for ((word, count) in d.seedNext(prev)) {
                val scaled = (count * w).toInt()
                merged[word] = maxOf(merged[word] ?: 0, scaled)
            }
        }
        return merged.entries.map { it.key to it.value }
    }

    fun completions(prefix: String, limit: Int): List<String> {
        val scored = HashMap<String, Double>()
        for (d in active()) {
            val w = weight(d.language)
            for (word in d.completions(prefix, limit)) {
                val s = d.frequency(word) * w
                if (s > (scored[word] ?: 0.0)) scored[word] = s
            }
        }
        return scored.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    companion object {
        /** How much a lexicon counts when it matches / doesn't match the sentence language. */
        const val WEIGHT_MATCH = 1.0
        const val WEIGHT_OTHER = 0.55
        /** With no context clue, the secondary language of a mode is slightly discounted. */
        const val WEIGHT_SECONDARY = 0.85

        /** Pure so it can be unit tested: per-language lookup weights for a mode and context. */
        fun weightsFor(mode: Mode, context: Language?): Map<Language, Double> {
            val langs = mode.languages
            return langs.withIndex().associate { (i, lang) ->
                lang to when {
                    context == null -> if (i == 0 || mode == Mode.BOTH) WEIGHT_MATCH else WEIGHT_SECONDARY
                    lang == context -> WEIGHT_MATCH
                    else -> WEIGHT_OTHER
                }
            }
        }
    }
}
