package com.caxone.my_keyboard.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.caxone.my_keyboard.R
import com.google.android.material.button.MaterialButton

/** The two-step enable / select flow plus a field to try the keyboard in. */
class SetupActivity : BaseSettingsActivity() {

    override val titleRes = R.string.nav_setup

    private lateinit var txtEnable: TextView
    private lateinit var txtSelect: TextView

    override fun build() {
        section(R.string.step1_title)
        txtEnable = TextView(this)
        card(step(R.string.step1_body, txtEnable, R.string.step1_button) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        section(R.string.step2_title)
        txtSelect = TextView(this)
        card(step(R.string.step2_body, txtSelect, R.string.step2_button) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        section(R.string.try_title)
        val edit = EditText(this).apply {
            setHint(R.string.try_hint)
            minLines = 3
            gravity = Gravity.TOP
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = null
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
        card(edit)
    }

    private fun step(bodyRes: Int, status: TextView, buttonRes: Int, onClick: () -> Unit): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        column.addView(TextView(this).apply { setText(bodyRes); textSize = 15f })
        status.apply {
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        }
        column.addView(status)
        column.addView(MaterialButton(this).apply {
            setText(buttonRes)
            isAllCaps = false
            cornerRadius = dp(20)
            setOnClickListener { onClick() }
        })
        return column
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith(packageName)
        bind(txtEnable, enabled, R.string.status_enabled, R.string.status_not_enabled)
        bind(txtSelect, selected, R.string.status_selected, R.string.status_not_selected)
    }

    private fun bind(tv: TextView, ok: Boolean, okRes: Int, badRes: Int) {
        tv.text = (if (ok) "✓ " else "• ") + getString(if (ok) okRes else badRes)
        tv.setTextColor(getColor(if (ok) R.color.status_good else R.color.status_bad))
    }
}
