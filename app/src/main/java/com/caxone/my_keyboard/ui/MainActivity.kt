package com.caxone.my_keyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.keyboard.KeyboardView
import com.caxone.my_keyboard.keyboard.Layouts
import com.caxone.my_keyboard.keyboard.SuggestionStrip
import com.caxone.my_keyboard.media.StickerStore
import com.caxone.my_keyboard.prediction.LanguageEngine
import com.caxone.my_keyboard.prediction.UserModel
import com.caxone.my_keyboard.settings.Prefs
import com.caxone.my_keyboard.theme.ThemeStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider

/** Home screen: setup status, a live preview, and links to each settings group. */
class MainActivity : AppCompatActivity() {

    private lateinit var previewStrip: SuggestionStrip
    private lateinit var previewKeyboard: KeyboardView
    private lateinit var navContainer: LinearLayout

    private lateinit var imgStatus: ImageView
    private lateinit var txtStatusTitle: TextView
    private lateinit var txtStatusBody: TextView
    private lateinit var btnStatusAction: MaterialButton

    private var themesSummary: TextView? = null
    private var layoutSummary: TextView? = null
    private var languagesSummary: TextView? = null
    private var stickersSummary: TextView? = null
    private var learnedSummary: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imgStatus = findViewById(R.id.imgStatus)
        txtStatusTitle = findViewById(R.id.txtStatusTitle)
        txtStatusBody = findViewById(R.id.txtStatusBody)
        btnStatusAction = findViewById(R.id.btnStatusAction)
        val openSetup = View.OnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
        btnStatusAction.setOnClickListener(openSetup)
        findViewById<MaterialCardView>(R.id.cardStatus).setOnClickListener(openSetup)

        val container = findViewById<LinearLayout>(R.id.previewContainer)
        previewStrip = SuggestionStrip(this)
        previewKeyboard = KeyboardView(this).apply { interactive = false }
        container.addView(previewStrip)
        container.addView(previewKeyboard)
        Collapsible.bind(findViewById(R.id.previewHeader), findViewById(R.id.previewCard), R.string.home_preview_title, Prefs.KEY_PREVIEW_COLLAPSED)

        navContainer = findViewById(R.id.navContainer)
        buildNavigation()

        Thread { UserModel.get(this).load() }.start()
        if (!Prefs.languagesAsked(this)) askLanguages()
    }

    /** One-time question so the intelligent layer knows which words to expect. */
    private fun askLanguages() {
        val modes = LanguageEngine.Mode.entries
        val labels = modes.map { getString(LanguagesActivity.titleFor(it)) }.toTypedArray()
        var picked = modes.indexOf(LanguageEngine.Mode.BOTH)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.languages_ask_title)
            .setMessage(R.string.languages_ask_body)
            .setSingleChoiceItems(labels, picked) { _, which -> picked = which }
            .setPositiveButton(R.string.languages_ask_ok) { _, _ ->
                Prefs.setLanguages(this, modes[picked])
                refreshSummaries()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshPreview()
        refreshSummaries()
    }

    // ---- status --------------------------------------------------------------------------

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith(packageName)
        val ready = enabled && selected

        txtStatusTitle.text = getString(
            when {
                ready -> R.string.home_status_ready
                enabled -> R.string.status_not_selected
                else -> R.string.status_not_enabled
            }
        )
        txtStatusBody.text = getString(if (ready) R.string.status_selected else R.string.nav_setup_sum)
        imgStatus.setImageResource(if (ready) R.drawable.ic_check_circle else R.drawable.ic_warning)
        imgStatus.imageTintList = android.content.res.ColorStateList.valueOf(
            getColor(if (ready) R.color.status_good else R.color.status_bad)
        )
        btnStatusAction.visibility = if (ready) View.GONE else View.VISIBLE
    }

    // ---- preview -------------------------------------------------------------------------

    private fun refreshPreview() {
        val theme = ThemeStore.current(this)
        previewKeyboard.theme = theme
        previewKeyboard.layout = Layouts.letters(Prefs.layout(this), Prefs.numberRow(this))
        previewStrip.applyTheme(theme)
        previewStrip.setSuggestions(listOf("Hello", "Hey", "How"), -1)
    }

    // ---- navigation ----------------------------------------------------------------------

    private fun buildNavigation() {
        header(R.string.section_customise)
        group(
            nav(R.drawable.ic_palette, R.string.nav_themes, "") { open(ThemesActivity::class.java) }.also { themesSummary = it.findViewById(R.id.summary) },
            nav(R.drawable.ic_keyboard, R.string.nav_layout, "") { open(LayoutActivity::class.java) }.also { layoutSummary = it.findViewById(R.id.summary) },
            nav(R.drawable.ic_translate, R.string.nav_languages, "") { open(LanguagesActivity::class.java) }.also { languagesSummary = it.findViewById(R.id.summary) },
            nav(R.drawable.ic_spellcheck, R.string.nav_typing, getString(R.string.nav_typing_sum)) { open(TypingActivity::class.java) },
            nav(R.drawable.ic_vibration, R.string.nav_feedback, getString(R.string.nav_feedback_sum)) { open(FeedbackActivity::class.java) }
        )
        header(R.string.section_data)
        group(
            nav(R.drawable.ic_sticker, R.string.nav_stickers, "") { open(StickersActivity::class.java) }.also { stickersSummary = it.findViewById(R.id.summary) },
            nav(R.drawable.ic_book, R.string.nav_learned, "") { open(LearnedWordsActivity::class.java) }.also { learnedSummary = it.findViewById(R.id.summary) }
        )
        header(R.string.section_more)
        group(
            nav(R.drawable.ic_keyboard, R.string.nav_setup, getString(R.string.nav_setup_sum)) { open(SetupActivity::class.java) },
            nav(R.drawable.ic_info, R.string.nav_about, getString(R.string.nav_about_sum)) { open(AboutActivity::class.java) }
        )
    }

    private fun refreshSummaries() {
        themesSummary?.text = getString(R.string.nav_themes_sum, ThemeStore.current(this).name)
        layoutSummary?.text = getString(R.string.nav_layout_sum, Prefs.layout(this).displayName)
        languagesSummary?.text = getString(LanguagesActivity.titleFor(Prefs.languages(this)))
        val stickers = StickerStore.count(this)
        stickersSummary?.text = if (stickers == 0) getString(R.string.nav_stickers_sum_none)
        else resources.getQuantityString(R.plurals.nav_stickers_sum, stickers, stickers)
        val model = UserModel.get(this)
        val n = if (model.isLoaded) model.size() else 0
        learnedSummary?.text = if (n == 0) getString(R.string.nav_learned_sum_none)
        else resources.getQuantityString(R.plurals.nav_learned_sum, n, n)
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun header(titleRes: Int) {
        val tv = TextView(this)
        tv.setText(titleRes)
        tv.setTextAppearance(R.style.SectionHeader)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(24), 0, dp(8))
        tv.layoutParams = lp
        navContainer.addView(tv)
    }

    private fun group(vararg rows: View) {
        val card = MaterialCardView(this, null, com.google.android.material.R.attr.materialCardViewStyle).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.card_stroke)
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((i, row) in rows.withIndex()) {
            if (i > 0) column.addView(MaterialDivider(this).apply { dividerInsetStart = dp(72); dividerInsetEnd = dp(16) })
            column.addView(row)
        }
        card.addView(column)
        navContainer.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun nav(iconRes: Int, titleRes: Int, summary: CharSequence, onClick: () -> Unit): View {
        val row = LayoutInflater.from(this).inflate(R.layout.row_nav, navContainer, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.title).setText(titleRes)
        row.findViewById<TextView>(R.id.summary).text = summary
        row.setOnClickListener { onClick() }
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
