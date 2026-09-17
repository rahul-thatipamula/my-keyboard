package com.caxone.my_keyboard.prediction

import android.content.Context

/**
 * Built-in English dictionary: ~30k words with Zipf frequencies (x100) plus a small
 * seed of common bigrams so next-word prediction works before the user has typed anything.
 */
class Dictionary(private val context: Context) {

    private val frequency = HashMap<String, Int>(40_000)
    private var sorted: Array<String> = emptyArray()
    private val byLength = Array(MAX_LEN + 1) { ArrayList<String>() }
    private val byFirst = HashMap<Char, ArrayList<String>>()
    private val seedNext = HashMap<String, ArrayList<Pair<String, Int>>>()

    @Volatile
    var isLoaded = false
        private set

    fun load() {
        if (isLoaded) return
        val words = ArrayList<String>(32_000)
        context.assets.open("words.txt").bufferedReader().useLines { lines ->
            for (line in lines) {
                val sp = line.indexOf(' ')
                if (sp <= 0) continue
                val w = line.substring(0, sp)
                val f = line.substring(sp + 1).trim().toIntOrNull() ?: continue
                if (frequency.put(w, f) == null) {
                    words.add(w)
                    if (w.length <= MAX_LEN) byLength[w.length].add(w)
                    byFirst.getOrPut(w[0]) { ArrayList() }.add(w)
                }
            }
        }
        words.sort()
        sorted = words.toTypedArray()

        context.assets.open("bigrams.txt").bufferedReader().useLines { lines ->
            for (line in lines) {
                val parts = line.trim().split(' ')
                if (parts.size != 3) continue
                val count = parts[2].toIntOrNull() ?: continue
                seedNext.getOrPut(parts[0]) { ArrayList() }.add(parts[1] to count)
            }
        }
        isLoaded = true
    }

    fun frequency(word: String): Int = frequency[word] ?: 0

    fun contains(word: String): Boolean = frequency.containsKey(word)

    fun wordsOfLength(len: Int): List<String> =
        if (len in 1..MAX_LEN) byLength[len] else emptyList()

    fun wordsStartingWith(c: Char): List<String> = byFirst[c] ?: emptyList()

    fun seedNext(prev: String): List<Pair<String, Int>> = seedNext[prev] ?: emptyList()

    /** Most frequent dictionary words starting with [prefix]. */
    fun completions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty() || sorted.isEmpty()) return emptyList()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < prefix) lo = mid + 1 else hi = mid
        }
        val found = ArrayList<String>()
        var i = lo
        var scanned = 0
        while (i < sorted.size && sorted[i].startsWith(prefix) && scanned < MAX_SCAN) {
            found.add(sorted[i])
            i++
            scanned++
        }
        found.sortByDescending { frequency[it] ?: 0 }
        return if (found.size > limit) found.subList(0, limit) else found
    }

    companion object {
        const val MAX_LEN = 24
        private const val MAX_SCAN = 6000
    }
}
