package com.caxone.my_keyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.theme.KeyboardTheme
import com.google.android.material.card.MaterialCardView

class ThemeAdapter(
    private val themes: List<KeyboardTheme>,
    var selectedId: String,
    private val onPick: (KeyboardTheme) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card)
        val preview: ThemePreviewView = view.findViewById(R.id.preview)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_theme, parent, false))

    override fun getItemCount() = themes.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val theme = themes[position]
        val density = holder.itemView.resources.displayMetrics.density
        holder.preview.theme = theme
        holder.name.text = theme.name
        val selected = theme.id == selectedId
        holder.card.strokeWidth = if (selected) (3 * density).toInt() else 0
        holder.card.strokeColor = theme.accent
        holder.card.setOnClickListener { onPick(theme) }
    }

    fun select(id: String) {
        selectedId = id
        notifyDataSetChanged()
    }
}
