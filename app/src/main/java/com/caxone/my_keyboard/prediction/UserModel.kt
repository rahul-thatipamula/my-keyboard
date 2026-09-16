package com.caxone.my_keyboard.prediction

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Personal language model that learns from what the user types.
 * Keeps unigram / bigram / trigram counts in memory (for instant lookups) and
 * mirrors every update to SQLite on a background thread so it survives restarts.
 */
class UserModel private constructor(context: Context) : SQLiteOpenHelper(context, "user_model.db", null, 1) {

    companion object {
        @Volatile
        private var instance: UserModel? = null

        /** One shared instance so the keyboard service and the settings screen see the same data. */
        fun get(context: Context): UserModel =
            instance ?: synchronized(this) {
                instance ?: UserModel(context.applicationContext).also { instance = it }
            }
    }

    private val unigrams = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val trigrams = HashMap<String, HashMap<String, Int>>()
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()

    @Volatile
    var isLoaded = false
        private set

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE unigram(word TEXT PRIMARY KEY, count INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE bigram(w1 TEXT, w2 TEXT, count INTEGER NOT NULL, PRIMARY KEY(w1, w2))")
        db.execSQL("CREATE TABLE trigram(w1 TEXT, w2 TEXT, w3 TEXT, count INTEGER NOT NULL, PRIMARY KEY(w1, w2, w3))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun load() {
        if (isLoaded) return
        val db = readableDatabase
        synchronized(lock) {
            db.rawQuery("SELECT word, count FROM unigram", null).use { c ->
                while (c.moveToNext()) unigrams[c.getString(0)] = c.getInt(1)
            }
            db.rawQuery("SELECT w1, w2, count FROM bigram", null).use { c ->
                while (c.moveToNext()) bigrams.getOrPut(c.getString(0)) { HashMap() }[c.getString(1)] = c.getInt(2)
            }
            db.rawQuery("SELECT w1, w2, w3, count FROM trigram", null).use { c ->
                while (c.moveToNext()) {
                    trigrams.getOrPut(key(c.getString(0), c.getString(1))) { HashMap() }[c.getString(2)] = c.getInt(3)
                }
            }
        }
        isLoaded = true
    }

    // Words never contain spaces, so a space is a safe separator for the trigram key.
    private fun key(w1: String, w2: String) = "$w1 $w2"

    /** Records that [word] followed [prev1] (and [prev2] before that). Use "<s>" for sentence start. */
    fun learn(prev2: String?, prev1: String?, word: String) {
        synchronized(lock) {
            unigrams[word] = (unigrams[word] ?: 0) + 1
            if (prev1 != null) {
                val m = bigrams.getOrPut(prev1) { HashMap() }
                m[word] = (m[word] ?: 0) + 1
                if (prev2 != null) {
                    val t = trigrams.getOrPut(key(prev2, prev1)) { HashMap() }
                    t[word] = (t[word] ?: 0) + 1
                }
            }
        }
        io.execute {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("INSERT OR IGNORE INTO unigram(word, count) VALUES(?, 0)", arrayOf(word))
                db.execSQL("UPDATE unigram SET count = count + 1 WHERE word = ?", arrayOf(word))
                if (prev1 != null) {
                    db.execSQL("INSERT OR IGNORE INTO bigram(w1, w2, count) VALUES(?, ?, 0)", arrayOf(prev1, word))
                    db.execSQL("UPDATE bigram SET count = count + 1 WHERE w1 = ? AND w2 = ?", arrayOf(prev1, word))
                    if (prev2 != null) {
                        db.execSQL(
                            "INSERT OR IGNORE INTO trigram(w1, w2, w3, count) VALUES(?, ?, ?, 0)",
                            arrayOf(prev2, prev1, word)
                        )
                        db.execSQL(
                            "UPDATE trigram SET count = count + 1 WHERE w1 = ? AND w2 = ? AND w3 = ?",
                            arrayOf(prev2, prev1, word)
                        )
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun count(word: String): Int = synchronized(lock) { unigrams[word] ?: 0 }

    fun after(prev1: String): Map<String, Int> =
        synchronized(lock) { bigrams[prev1]?.let { HashMap(it) } ?: emptyMap() }

    fun after(prev2: String, prev1: String): Map<String, Int> =
        synchronized(lock) { trigrams[key(prev2, prev1)]?.let { HashMap(it) } ?: emptyMap() }

    /** Learned words starting with [prefix], most used first. */
    fun completions(prefix: String, limit: Int): List<String> = synchronized(lock) {
        unigrams.entries
            .filter { it.key.length > prefix.length && it.key.startsWith(prefix) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /** Learned words whose length is within one of [length]. */
    fun wordsNearLength(length: Int): List<String> = synchronized(lock) {
        unigrams.keys.filter { abs(it.length - length) <= 1 }
    }

    fun clear() {
        synchronized(lock) {
            unigrams.clear()
            bigrams.clear()
            trigrams.clear()
        }
        io.execute {
            val db = writableDatabase
            db.execSQL("DELETE FROM unigram")
            db.execSQL("DELETE FROM bigram")
            db.execSQL("DELETE FROM trigram")
        }
    }

}
