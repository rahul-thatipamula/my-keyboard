package com.caxone.my_keyboard.ui

import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import com.caxone.my_keyboard.R

class AboutActivity : BaseSettingsActivity() {

    override val titleRes = R.string.nav_about

    override fun build() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }

        section(R.string.app_name)
        card(block(getString(R.string.about_version, version), getString(R.string.tagline)))

        section(R.string.about_privacy_title)
        card(block(null, getString(R.string.about_privacy_body)))

        section(R.string.about_permissions_title)
        card(block(null, getString(R.string.about_permissions_body)))

        section(R.string.section_more)
        card(navRow(R.drawable.ic_info, R.string.about_source, getString(R.string.about_source_url)) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_source_url))))
        })
    }

    private fun block(title: String?, body: String): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        if (title != null) column.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        column.addView(TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            if (title != null) setPadding(0, dp(4), 0, 0)
        })
        return column
    }
}
