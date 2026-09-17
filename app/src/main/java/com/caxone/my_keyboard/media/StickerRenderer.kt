package com.caxone.my_keyboard.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a square sticker: an optional photo (framed by zoom / pan, tinted by a colour filter,
 * optionally outlined) over a solid or transparent background, then an emoji and a caption.
 */
object StickerRenderer {

    class Spec(
        val photo: Bitmap? = null,
        val background: Int = 0xFFFFC107.toInt(),
        /** No fill behind the photo; with a cut-out this gives the classic die-cut sticker. */
        val transparent: Boolean = false,
        val caption: String = "",
        val captionColor: Int = Color.WHITE,
        val emoji: String = "",
        /** 1 = the photo's short side fills the sticker. */
        val zoom: Float = 1f,
        /** Photo offset from centre as a fraction of the sticker size. */
        val panX: Float = 0f,
        val panY: Float = 0f,
        val colorFilter: ColorFilter? = null,
        /** Outline thickness as a fraction of the sticker size; 0 disables it. */
        val outlineWidth: Float = 0f,
        val outlineColor: Int = Color.WHITE
    )

    /** Maps photo pixels to sticker pixels for a sticker [size] wide. */
    fun photoMatrix(spec: Spec, size: Int): Matrix {
        val m = Matrix()
        val photo = spec.photo ?: return m
        val scale = size / min(photo.width, photo.height).toFloat() * spec.zoom
        m.postScale(scale, scale)
        m.postTranslate(
            (size - photo.width * scale) / 2f + spec.panX * size,
            (size - photo.height * scale) / 2f + spec.panY * size
        )
        return m
    }

    fun render(spec: Spec, size: Int = StickerStore.SIZE): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = size * 0.18f
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())

        val clip = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        if (!spec.transparent) canvas.drawColor(spec.background)

        val photo = spec.photo
        if (photo != null) {
            val matrix = photoMatrix(spec, size)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            if (spec.outlineWidth > 0f) drawOutline(canvas, photo, matrix, spec.outlineWidth * size, spec.outlineColor)
            paint.colorFilter = spec.colorFilter
            canvas.drawBitmap(photo, matrix, paint)
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

    /**
     * A solid halo around the photo's opaque pixels: the photo is stamped in [color] at many
     * offsets on a ring of radius [width] (and a smaller inner ring so the band is filled).
     */
    private fun drawOutline(canvas: Canvas, photo: Bitmap, matrix: Matrix, width: Float, color: Int) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        val stamp = Matrix()
        for ((radius, steps) in listOf(width to 24, width * 0.5f to 12)) {
            for (i in 0 until steps) {
                val a = i * 2.0 * Math.PI / steps
                stamp.set(matrix)
                stamp.postTranslate((radius * cos(a)).toFloat(), (radius * sin(a)).toFloat())
                canvas.drawBitmap(photo, stamp, paint)
            }
        }
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
