package com.caxone.my_keyboard.media

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caxone.my_keyboard.theme.KeyboardTheme

/** Category tabs on top, a grid of emoji in the middle, ABC / backspace at the bottom. */
class EmojiPanel(context: Context) : PanelBase(context) {

    var onEmoji: ((String) -> Unit)? = null

    private val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val tabs = ArrayList<TextView>()
    private val grid = RecyclerView(context)
    private val adapter = EmojiAdapter { e -> pick(e) }
    private var selected = 0
    private val recentsLabel = "🕒"

    init {
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        scroll.addView(tabRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(38)))

        addTab(recentsLabel, 0)
        for ((i, c) in EmojiData.CATEGORIES.withIndex()) addTab(c.icon, i + 1)

        grid.layoutManager = GridLayoutManager(context, COLUMNS)
        grid.adapter = adapter
        grid.overScrollMode = View.OVER_SCROLL_NEVER
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        buildBottomRow()
        show(if (EmojiData.recents(context).isEmpty()) 1 else 0)
    }

    private fun addTab(label: String, index: Int) {
        val tv = TextView(context).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            setBackgroundResource(ripple())
            setOnClickListener { show(index) }
        }
        tabRow.addView(tv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        tabs.add(tv)
    }

    private fun show(index: Int) {
        selected = index
        val list = if (index == 0) EmojiData.recents(context) else EmojiData.CATEGORIES[index - 1].emoji
        adapter.submit(list)
        grid.scrollToPosition(0)
        applyTheme(theme)
    }

    /** Called when the panel is reopened so the recents tab is current. */
    fun refresh() {
        if (selected == 0) show(0)
    }

    private fun pick(emoji: String) {
        EmojiData.addRecent(context, emoji)
        onEmoji?.invoke(emoji)
    }

    override fun applyTheme(theme: KeyboardTheme) {
        super.applyTheme(theme)
        for ((i, tv) in tabs.withIndex()) {
            tv.alpha = if (i == selected) 1f else 0.45f
            tv.setBackgroundColor(if (i == selected) theme.functionKeyBackground else 0)
        }
        adapter.textColor = theme.keyText
        adapter.notifyDataSetChanged()
    }

    private class EmojiAdapter(private val onPick: (String) -> Unit) : RecyclerView.Adapter<EmojiAdapter.Holder>() {
        private var items: List<String> = emptyList()
        var textColor = 0xFF000000.toInt()

        class Holder(val tv: TextView) : RecyclerView.ViewHolder(tv)

        fun submit(list: List<String>) { items = list; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val density = parent.resources.displayMetrics.density
            val tv = TextView(parent.context).apply {
                textSize = 26f
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (46 * density).toInt())
                val ripple = android.util.TypedValue().also {
                    parent.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                }
                setBackgroundResource(ripple.resourceId)
            }
            return Holder(tv)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val e = items[position]
            holder.tv.text = e
            holder.tv.setTextColor(textColor)
            holder.tv.setOnClickListener { onPick(e) }
        }
    }

    companion object {
        private const val COLUMNS = 8
    }
}
