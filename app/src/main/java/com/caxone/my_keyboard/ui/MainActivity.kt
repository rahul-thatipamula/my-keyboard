package com.caxone.my_keyboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.keyboard.KeyboardView
import com.caxone.my_keyboard.keyboard.SuggestionStrip
import com.caxone.my_keyboard.prediction.UserModel
import com.caxone.my_keyboard.settings.Prefs
import com.caxone.my_keyboard.theme.KeyboardTheme
import com.caxone.my_keyboard.theme.ThemeStore
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var txtEnableStatus: TextView
    private lateinit var txtSelectStatus: TextView
    private lateinit var previewStrip: SuggestionStrip
    private lateinit var previewKeyboard: KeyboardView
    private lateinit var themeAdapter: ThemeAdapter
    private lateinit var customContainer: LinearLayout
    private val swatchRows = ArrayList<List<View>>()

    private val palette = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFF1F3F4.toInt(), 0xFFDADCE0.toInt(), 0xFF9AA0A6.toInt(), 0xFF5F6368.toInt(),
        0xFF3C4043.toInt(), 0xFF202124.toInt(), 0xFF000000.toInt(),
        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(),
        0xFF1A73E8.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(),
        0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(),
        0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF263238.toInt(), 0xFF37474F.toInt(), 0xFF0B3D5C.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtEnableStatus = findViewById(R.id.txtEnableStatus)
        txtSelectStatus = findViewById(R.id.txtSelectStatus)
        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnSelect).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        setupPreview()
        setupThemes()
        setupCustomTheme()
        setupSettings()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshPreview()
    }

    // ---- setup status -----------------------------------------------------------------------

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith(packageName)

        txtEnableStatus.text = getString(if (enabled) R.string.status_enabled else R.string.status_not_enabled)
        txtEnableStatus.setTextColor(if (enabled) 0xFF1E8E3E.toInt() else 0xFFD93025.toInt())
        txtSelectStatus.text = getString(if (selected) R.string.status_selected else R.string.status_not_selected)
        txtSelectStatus.setTextColor(if (selected) 0xFF1E8E3E.toInt() else 0xFFD93025.toInt())
    }

    // ---- live preview -----------------------------------------------------------------------

    private fun setupPreview() {
        val container = findViewById<LinearLayout>(R.id.previewContainer)
        previewStrip = SuggestionStrip(this)
        previewKeyboard = KeyboardView(this).apply { interactive = false }
        container.addView(previewStrip)
        container.addView(previewKeyboard)
    }

    private fun refreshPreview() {
        val theme = ThemeStore.current(this)
        previewKeyboard.theme = theme
        previewStrip.applyTheme(theme)
        previewStrip.setSuggestions(listOf("Hello", "Hey", "How"), -1)
    }

    // ---- presets ----------------------------------------------------------------------------

    private fun setupThemes() {
        val recycler = findViewById<RecyclerView>(R.id.recyclerThemes)
        recycler.layoutManager = GridLayoutManager(this, 2)
        themeAdapter = ThemeAdapter(KeyboardTheme.PRESETS, ThemeStore.selectedId(this)) { theme ->
            ThemeStore.select(this, theme.id)
            themeAdapter.select(theme.id)
            refreshPreview()
        }
        recycler.adapter = themeAdapter
    }

    // ---- custom theme -----------------------------------------------------------------------

    private fun setupCustomTheme() {
        customContainer = findViewById(R.id.customContainer)
        val labels = listOf(R.string.custom_background, R.string.custom_keys, R.string.custom_text, R.string.custom_accent)
        val colors = ThemeStore.customColors(this)
        for (index in labels.indices) {
            customContainer.addView(buildSwatchRow(index, getString(labels[index]), colors[index]))
        }
        findViewById<Button>(R.id.btnUseCustom).setOnClickListener {
            ThemeStore.select(this, KeyboardTheme.CUSTOM_ID)
            themeAdapter.select(KeyboardTheme.CUSTOM_ID)
            refreshPreview()
        }
    }

    private fun buildSwatchRow(index: Int, label: String, selectedColor: Int): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }
        row.addView(TextView(this).apply { text = label; textSize = 14f })

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val swatches = ArrayList<View>()
        for (color in palette) {
            val swatch = View(this)
            val size = (36 * density).toInt()
            val lp = LinearLayout.LayoutParams(size, size).also { it.setMargins((4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), 0) }
            swatch.layoutParams = lp
            swatch.tag = color
            swatch.background = swatchDrawable(color, color == selectedColor)
            swatch.setOnClickListener {
                ThemeStore.setCustomColor(this, index, color)
                ThemeStore.select(this, KeyboardTheme.CUSTOM_ID)
                themeAdapter.select(KeyboardTheme.CUSTOM_ID)
                for (v in swatches) v.background = swatchDrawable(v.tag as Int, v === swatch)
                refreshPreview()
            }
            strip.addView(swatch)
            swatches.add(swatch)
        }
        swatchRows.add(swatches)
        scroll.addView(strip)
        row.addView(scroll)
        return row
    }

    private fun swatchDrawable(color: Int, selected: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            val stroke = if (selected) (3 * density).toInt() else (1 * density).toInt()
            val strokeColor = if (selected) 0xFF1A73E8.toInt() else Color.argb(60, 0, 0, 0)
            setStroke(stroke, strokeColor)
        }
    }

    // ---- typing settings --------------------------------------------------------------------

    private fun setupSettings() {
        bindSwitch(R.id.swAutocorrect, Prefs.KEY_AUTOCORRECT, Prefs.autocorrect(this))
        bindSwitch(R.id.swPrediction, Prefs.KEY_PREDICTION, Prefs.prediction(this))
        bindSwitch(R.id.swPopup, Prefs.KEY_POPUP, Prefs.keyPopup(this))
        bindSwitch(R.id.swVibrate, Prefs.KEY_VIBRATE, Prefs.vibrate(this))
        bindSwitch(R.id.swSound, Prefs.KEY_SOUND, Prefs.sound(this))

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_learned)
                .setMessage(R.string.clear_learned_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    UserModel.get(this).clear()
                    Toast.makeText(this, R.string.clear_learned_done, Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun bindSwitch(id: Int, key: String, initial: Boolean) {
        findViewById<SwitchMaterial>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> Prefs.set(this@MainActivity, key, checked) }
        }
    }
}
