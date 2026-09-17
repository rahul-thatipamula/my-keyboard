package com.caxone.my_keyboard.media

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.theme.KeyboardTheme
import java.io.File

/** Grid of the user's stickers with a "+" tile to make a new one. Long-press a sticker to remove it. */
class StickerPanel(context: Context) : PanelBase(context) {

    var onSticker: ((File) -> Unit)? = null
    var onCreate: (() -> Unit)? = null
    var onDeleteRequest: ((File) -> Unit)? = null

    private val grid = RecyclerView(context)
    private val empty = TextView(context).apply {
        text = "No stickers yet.\nTap + to make one from a photo, an emoji or some text."
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
    }
    private val adapter = StickerAdapter()
    private val thumbs = LruCache<String, Bitmap>(64)

    init {
        val host = FrameLayout(context)
        grid.layoutManager = GridLayoutManager(context, COLUMNS)
        grid.adapter = adapter
        grid.overScrollMode = View.OVER_SCROLL_NEVER
        grid.setPadding(dp(6), dp(6), dp(6), dp(6))
        grid.clipToPadding = false
        host.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        host.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(host, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        buildBottomRow()
    }

    fun refresh() {
        val files = StickerStore.list(context)
        adapter.submit(files)
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun applyTheme(theme: KeyboardTheme) {
        super.applyTheme(theme)
        empty.setTextColor(theme.hintText)
        adapter.notifyDataSetChanged()
    }

    private fun thumb(file: File): Bitmap? {
        val key = "${file.path}:${file.lastModified()}"
        thumbs.get(key)?.let { return it }
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 } // 512 -> 128 px
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
        thumbs.put(key, bmp)
        return bmp
    }

    private inner class StickerAdapter : RecyclerView.Adapter<StickerAdapter.Holder>() {
        private var files: List<File> = emptyList()

        inner class Holder(val frame: FrameLayout, val image: ImageView) : RecyclerView.ViewHolder(frame)

        fun submit(list: List<File>) { files = list; notifyDataSetChanged() }

        override fun getItemViewType(position: Int) = if (position == 0) TYPE_ADD else TYPE_STICKER

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val frame = FrameLayout(parent.context)
            val size = (parent.width - dp(12)) / COLUMNS
            frame.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (size > 0) size else dp(84))
            frame.setPadding(dp(5), dp(5), dp(5), dp(5))
            val image = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(ripple())
            }
            frame.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            return Holder(frame, image)
        }

        override fun getItemCount() = files.size + 1

        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (position == 0) {
                holder.image.setImageResource(R.drawable.ic_add)
                holder.image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                holder.image.imageTintList = ColorStateList.valueOf(theme.accent)
                holder.image.background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(theme.keyBackground)
                    setStroke(dp(2), theme.accent, dp(6).toFloat(), dp(4).toFloat())
                }
                holder.image.setOnClickListener { onCreate?.invoke() }
                holder.image.setOnLongClickListener(null)
                return
            }
            val file = files[position - 1]
            holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.image.imageTintList = null
            holder.image.background = null
            holder.image.setImageBitmap(thumb(file))
            holder.image.setOnClickListener { onSticker?.invoke(file) }
            holder.image.setOnLongClickListener { onDeleteRequest?.invoke(file); true }
        }
    }

    companion object {
        private const val COLUMNS = 4
        private const val TYPE_ADD = 0
        private const val TYPE_STICKER = 1
    }
}
