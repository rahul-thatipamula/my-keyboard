package com.caxone.my_keyboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.prediction.UserModel
import com.google.android.material.appbar.MaterialToolbar

/** Lists everything in the personal language model with per-word forget and a reset-all action. */
class LearnedWordsActivity : AppCompatActivity() {

    private lateinit var model: UserModel
    private lateinit var adapter: WordAdapter
    private lateinit var empty: TextView
    private var all: List<Pair<String, Int>> = emptyList()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learned_words)
        model = UserModel.get(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_reset) { confirmReset(); true } else false
        }

        empty = findViewById(R.id.empty)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = WordAdapter { word -> forget(word) }
        recycler.adapter = adapter

        findViewById<EditText>(R.id.search).doAfterTextChanged {
            query = it?.toString()?.trim()?.lowercase() ?: ""
            applyFilter()
        }

        Thread {
            model.load()
            runOnUiThread { reload() }
        }.start()
    }

    private fun reload() {
        all = model.allWords()
        applyFilter()
    }

    private fun applyFilter() {
        val shown = if (query.isEmpty()) all else all.filter { it.first.contains(query) }
        adapter.submit(shown)
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun forget(word: String) {
        model.remove(word)
        Toast.makeText(this, getString(R.string.learned_deleted, word), Toast.LENGTH_SHORT).show()
        reload()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_learned)
            .setMessage(R.string.clear_learned_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                model.clear()
                Toast.makeText(this, R.string.clear_learned_done, Toast.LENGTH_SHORT).show()
                reload()
            }
            .show()
    }

    private class WordAdapter(private val onDelete: (String) -> Unit) : RecyclerView.Adapter<WordAdapter.Holder>() {

        private var items: List<Pair<String, Int>> = emptyList()

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val word: TextView = view.findViewById(R.id.word)
            val count: TextView = view.findViewById(R.id.count)
            val delete: ImageButton = view.findViewById(R.id.delete)
        }

        fun submit(list: List<Pair<String, Int>>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.row_word, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (word, count) = items[position]
            val res = holder.itemView.resources
            holder.word.text = word
            holder.count.text = if (count == 1) res.getString(R.string.learned_count_one) else res.getString(R.string.learned_count, count)
            holder.delete.setOnClickListener { onDelete(word) }
        }
    }
}
