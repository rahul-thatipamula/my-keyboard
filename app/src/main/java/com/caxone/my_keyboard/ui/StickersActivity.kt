package com.caxone.my_keyboard.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.media.StickerSender
import com.caxone.my_keyboard.media.StickerStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File

/** Manage saved stickers: share, delete, or make a new one. */
class StickersActivity : AppCompatActivity() {

    private lateinit var adapter: Adapter
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stickers)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        empty = findViewById(R.id.empty)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = Adapter()
        recycler.adapter = adapter
        findViewById<ExtendedFloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(Intent(this, StickerMakerActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val files = StickerStore.list(this)
        adapter.submit(files)
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.sticker_delete)
            .setMessage(R.string.sticker_delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> StickerStore.delete(file); reload() }
            .show()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        private var files: List<File> = emptyList()

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.image)
            val share: ImageButton = view.findViewById(R.id.share)
            val delete: ImageButton = view.findViewById(R.id.delete)
        }

        fun submit(list: List<File>) { files = list; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false))

        override fun getItemCount() = files.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = files[position]
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            holder.image.setImageBitmap(BitmapFactory.decodeFile(file.path, opts))
            holder.share.setOnClickListener { StickerSender.share(this@StickersActivity, StickerStore.uri(this@StickersActivity, file)) }
            holder.delete.setOnClickListener { confirmDelete(file) }
        }
    }
}
