package com.caxone.my_keyboard.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.min

/** Draws a square sticker: an optional photo or solid colour, a rounded mask, and a caption. */
object StickerRenderer {

    class Spec(
        val photo: Bitmap? = null,
        val background: Int = 0xFFFFC107.toInt(),
        val caption: String = "",
        val captionColor: Int = Color.WHITE,
        val emoji: String = ""
    )

    fun render(spec: Spec, size: Int = StickerStore.SIZE): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = size * 0.18f
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())

        val clip = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        val photo = spec.photo
        if (photo != null) {
            val scale = size / min(photo.width, photo.height).toFloat()
            val w = photo.width * scale
            val h = photo.height * scale
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(photo, null, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), paint)
        } else {
            canvas.drawColor(spec.background)
        }

        if (spec.emoji.isNotEmpty()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * (if (spec.caption.isEmpty()) 0.55f else 0.42f)
                textAlign = Paint.Align.CENTER
            }
            val cy = if (spec.caption.isEmpty()) size / 2f else size * 0.42f
            val baseline = cy - (p.descent() + p.ascent()) / 2
            canvas.drawText(spec.emoji, size / 2f, baseline, p)
        }

        if (spec.caption.isNotBlank()) {
            val textSize = when {
                spec.caption.length <= 8 -> size * 0.17f
                spec.caption.length <= 16 -> size * 0.13f
                else -> size * 0.10f
            }
            val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.captionColor
                this.textSize = textSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val stroke = TextPaint(fill).apply {
                style = Paint.Style.STROKE
                strokeWidth = textSize * 0.14f
                strokeJoin = Paint.Join.ROUND
                color = if (luminance(spec.captionColor) > 0.5) Color.BLACK else Color.WHITE
            }
            val width = (size * 0.86f).toInt()
            val strokeLayout = layout(spec.caption, stroke, width)
            val fillLayout = layout(spec.caption, fill, width)
            val top = if (spec.photo == null && spec.emoji.isEmpty()) (size - fillLayout.height) / 2f
            else size - fillLayout.height - size * 0.07f
            canvas.save()
            canvas.translate((size - width) / 2f, top)
            strokeLayout.draw(canvas)
            fillLayout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
        return out
    }

    @Suppress("DEPRECATION")
    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

    private fun luminance(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
}
