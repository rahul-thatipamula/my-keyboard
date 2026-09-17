package com.caxone.my_keyboard.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Stickers the user has made, stored as PNG files in the app's private storage and handed to
 * other apps through a FileProvider content URI.
 */
object StickerStore {

    const val SIZE = 512

    private fun dir(context: Context): File = File(context.filesDir, "stickers").also { it.mkdirs() }

    private fun authority(context: Context) = "${context.packageName}.stickers"

    /** Newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun count(context: Context): Int = list(context).size

    fun save(context: Context, bitmap: Bitmap): File {
        val file = File(dir(context), "sticker_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun delete(file: File): Boolean = file.delete()

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)
}
