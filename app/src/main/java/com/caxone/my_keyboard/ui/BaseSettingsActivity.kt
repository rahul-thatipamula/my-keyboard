package com.caxone.my_keyboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.settings.Prefs
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * A settings screen: toolbar with back arrow above a scrolling column. Subclasses build the
 * column with the helpers here so every screen shares the same card-and-row look.
 */
abstract class BaseSettingsActivity : AppCompatActivity() {

    protected lateinit var content: LinearLayout
    protected val density: Float get() = resources.displayMetrics.density

    abstract val titleRes: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitle(titleRes)
        toolbar.setNavigationOnClickListener { finish() }
        content = findViewById(R.id.content)
        build()
    }

    /** Populate [content]. */
    abstract fun build()

    protected fun dp(v: Int): Int = (v * density).toInt()

    fun section(titleRes: Int): TextView {
        val tv = TextView(this, null, 0, R.style.SectionHeader)
        tv.setText(titleRes)
        tv.setTextAppearance(R.style.SectionHeader)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(20), 0, dp(8))
        tv.layoutParams = lp
        content.addView(tv)
        return tv
    }

    fun paragraph(textRes: Int): TextView = paragraph(getString(textRes))

    fun paragraph(text: CharSequence): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        content.addView(tv)
        return tv
    }

    /** A rounded card that stacks its children vertically with thin dividers between them. */
    fun card(vararg rows: View): MaterialCardView {
        val card = MaterialCardView(this, null, com.google.android.material.R.attr.materialCardViewStyle).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.card_stroke)
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((i, row) in rows.withIndex()) {
            if (i > 0) column.addView(MaterialDivider(this).apply {
                dividerInsetStart = dp(16)
                dividerInsetEnd = dp(16)
            })
            column.addView(row)
        }
        card.addView(column)
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    fun switchRow(titleRes: Int, summaryRes: Int?, key: String): View {
        val row = LayoutInflater.from(this).inflate(R.layout.row_switch, content, false)
        row.findViewById<TextView>(R.id.title).setText(titleRes)
        row.findViewById<TextView>(R.id.summary).apply {
            if (summaryRes != null) { setText(summaryRes); visibility = View.VISIBLE }
        }
        val toggle = row.findViewById<SwitchMaterial>(R.id.toggle)
        toggle.isChecked = Prefs.get(this).getBoolean(key, Prefs.default(key))
        toggle.setOnCheckedChangeListener { _, checked -> Prefs.set(this, key, checked) }
        row.setOnClickListener { toggle.toggle() }
        return row
    }

    /** One choice in a single-select group; the caller keeps the radios in sync. */
    fun radioRow(title: CharSequence, summary: CharSequence?, checked: Boolean, onClick: () -> Unit): View {
        val row = LayoutInflater.from(this).inflate(R.layout.row_radio, content, false)
        row.findViewById<TextView>(R.id.title).text = title
        row.findViewById<TextView>(R.id.summary).apply {
            text = summary
            visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        row.findViewById<MaterialRadioButton>(R.id.radio).isChecked = checked
        row.setOnClickListener { onClick() }
        return row
    }

    fun navRow(iconRes: Int, titleRes: Int, summary: CharSequence?, onClick: () -> Unit): View {
        val row = LayoutInflater.from(this).inflate(R.layout.row_nav, content, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.title).setText(titleRes)
        row.findViewById<TextView>(R.id.summary).apply {
            text = summary
            visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        row.setOnClickListener { onClick() }
        return row
    }
}
