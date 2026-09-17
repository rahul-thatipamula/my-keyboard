package com.caxone.my_keyboard.media

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/**
 * Delivers a sticker to the current app. Apps that accept rich content (most messengers) get it
 * inline through commitContent; anything else gets a share sheet.
 */
object StickerSender {

    private const val MIME = "image/png"

    /** Returns true when the sticker went straight into the field, false when it was shared instead. */
    fun send(context: Context, ic: InputConnection?, info: EditorInfo?, uri: Uri): Boolean {
        if (ic != null && info != null && supportsInline(info)) {
            val content = InputContentInfoCompat(uri, ClipDescription("sticker", arrayOf(MIME)), null)
            val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            if (InputConnectionCompat.commitContent(ic, info, content, flags, null)) return true
        }
        share(context, uri)
        return false
    }

    private fun supportsInline(info: EditorInfo): Boolean =
        EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(MIME, it) }

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }
}
