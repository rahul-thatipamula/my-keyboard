package com.caxone.my_keyboard.prediction

/** A language the intelligent layer knows. Telugu here means Tenglish: Telugu typed in Latin letters. */
enum class Language(val id: String, val displayName: String, val wordsAsset: String, val bigramsAsset: String) {
    ENGLISH("en", "English", "words.txt", "bigrams.txt"),
    TELUGU("te", "Telugu (Tenglish)", "tenglish_words.txt", "tenglish_bigrams.txt");

    companion object {
        fun fromId(id: String?): Language? = entries.firstOrNull { it.id == id }
    }
}
