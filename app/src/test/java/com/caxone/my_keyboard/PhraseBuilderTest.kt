package com.caxone.my_keyboard

import com.caxone.my_keyboard.prediction.PhraseBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class PhraseBuilderTest {

    private fun builder(
        tri: Map<Pair<String, String>, Map<String, Int>> = emptyMap(),
        bi: Map<String, Map<String, Int>> = emptyMap()
    ) = PhraseBuilder({ a, b -> tri[a to b] ?: emptyMap() }, { a -> bi[a] ?: emptyMap() })

    @Test
    fun followsConfidentTrigramsIntoAPhrase() {
        val b = builder(
            tri = mapOf(
                ("how" to "are") to mapOf("you" to 5),
                ("are" to "you") to mapOf("doing" to 4, "ok" to 1),
                ("you" to "doing") to mapOf("today" to 3)
            )
        )
        assertEquals(listOf("you", "doing", "today"), b.extend("how", "are"))
    }

    @Test
    fun stopsWhenNoFollowerDominates() {
        val b = builder(bi = mapOf("i" to mapOf("am" to 3, "will" to 3, "think" to 2)))
        assertEquals(emptyList<String>(), b.extend(null, "i"))
    }

    @Test
    fun ignoresFollowersSeenOnlyOnce() {
        val b = builder(bi = mapOf("see" to mapOf("you" to 1)))
        assertEquals(emptyList<String>(), b.extend(null, "see"))
    }

    @Test
    fun fallsBackToBigramsWithoutTrigramContext() {
        val b = builder(bi = mapOf("thank" to mapOf("you" to 6, "god" to 1)))
        assertEquals(listOf("you"), b.extend(null, "thank"))
    }

    @Test
    fun respectsTheWordAndLengthCaps() {
        val bi = HashMap<String, Map<String, Int>>()
        val words = listOf("a", "b", "c", "d", "e", "f")
        for (i in 0 until words.size - 1) bi[words[i]] = mapOf(words[i + 1] to 9)
        assertEquals(3, builder(bi = bi).extend(null, "a").size)
        assertEquals(1, builder(bi = bi).extend(null, "a", maxExtra = 1).size)

        val long = builder(bi = mapOf("supercalifragilistic" to mapOf("expialidocious" to 9)))
        assertEquals(emptyList<String>(), long.extend(null, "supercalifragilistic"))
    }

    @Test
    fun doesNotStutter() {
        val b = builder(bi = mapOf("very" to mapOf("very" to 9)))
        assertEquals(emptyList<String>(), b.extend(null, "very"))
    }
}
