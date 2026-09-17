package com.caxone.my_keyboard

import com.caxone.my_keyboard.prediction.Language
import com.caxone.my_keyboard.prediction.LanguageEngine
import com.caxone.my_keyboard.prediction.LanguageEngine.Mode
import com.caxone.my_keyboard.prediction.PhraseBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageEngineTest {

    @Test
    fun englishOnlyModeNeverLoadsTelugu() {
        val w = LanguageEngine.weightsFor(Mode.ENGLISH, null)
        assertEquals(setOf(Language.ENGLISH), w.keys)
        assertNull(LanguageEngine.weightsFor(Mode.ENGLISH, Language.TELUGU)[Language.TELUGU])
    }

    @Test
    fun contextLanguageWinsAndTheOtherIsDiscounted() {
        val te = LanguageEngine.weightsFor(Mode.BOTH, Language.TELUGU)
        assertEquals(LanguageEngine.WEIGHT_MATCH, te.getValue(Language.TELUGU), 0.0)
        assertEquals(LanguageEngine.WEIGHT_OTHER, te.getValue(Language.ENGLISH), 0.0)
        val en = LanguageEngine.weightsFor(Mode.TELUGU, Language.ENGLISH)
        assertEquals(LanguageEngine.WEIGHT_MATCH, en.getValue(Language.ENGLISH), 0.0)
        assertEquals(LanguageEngine.WEIGHT_OTHER, en.getValue(Language.TELUGU), 0.0)
    }

    @Test
    fun withoutContextBothModeIsEvenAndTeluguModePrefersTelugu() {
        val both = LanguageEngine.weightsFor(Mode.BOTH, null)
        assertEquals(both.getValue(Language.ENGLISH), both.getValue(Language.TELUGU), 0.0)
        val te = LanguageEngine.weightsFor(Mode.TELUGU, null)
        assertTrue(te.getValue(Language.TELUGU) > te.getValue(Language.ENGLISH))
    }

    @Test
    fun seedPhrasesNeedAClearMajority() {
        val seeds = mapOf(
            "ela" to mapOf("unnavu" to 900, "unnaru" to 300),        // 75 % → phrase
            "good" to mapOf("morning" to 620, "night" to 640),       // split → no phrase
            "naku" to mapOf("telusu" to 640, "teliyadu" to 660, "kavali" to 600)
        )
        val b = PhraseBuilder({ _, _ -> emptyMap() }, { emptyMap() }, { seeds[it] ?: emptyMap() })
        assertEquals(listOf("unnavu"), b.extend(null, "ela"))
        assertEquals(emptyList<String>(), b.extend(null, "good"))
        assertEquals(emptyList<String>(), b.extend(null, "naku"))
    }
}
