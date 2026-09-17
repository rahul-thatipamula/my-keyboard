package com.caxone.my_keyboard.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * The editable state of a sticker photo. [source] is the photo as loaded (after rotate / flip);
 * [cutout] is what gets rendered: the same pixels with the background cleared by the segmenter
 * and/or the erase brush, and put back by the restore brush.
 *
 * Colour work (brightness, contrast, saturation, filter) is not baked into the pixels; it is
 * expressed as a [ColorMatrix] applied at render time, so it stays reversible.
 */
class PhotoEditor(original: Bitmap) {

    enum class Filter(val label: String, val matrix: ColorMatrix) {
        ORIGINAL("Original", ColorMatrix()),
        MONO("Mono", ColorMatrix().apply { setSaturation(0f) }),
        SEPIA("Sepia", ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))),
        VIVID("Vivid", ColorMatrix().apply {
            setSaturation(1.45f)
            postConcat(contrast(1.12f))
        }),
        COOL("Cool", ColorMatrix(floatArrayOf(
            0.92f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1.10f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        ))),
        WARM("Warm", ColorMatrix(floatArrayOf(
            1.10f, 0f, 0f, 0f, 8f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.90f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))),
        FADE("Fade", ColorMatrix().apply {
            setSaturation(0.8f)
            postConcat(contrast(0.85f))
            postConcat(ColorMatrix().apply { set(floatArrayOf(
                1f, 0f, 0f, 0f, 22f,
                0f, 1f, 0f, 0f, 22f,
                0f, 0f, 1f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )) })
        });
    }

    var source: Bitmap = fit(original)
        private set
    var cutout: Bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        private set

    /** True once the background has been removed or the erase brush has been used. */
    var hasCutout = false
        private set

    /** -1..1 */
    var brightness = 0f
    /** -1..1 */
    var contrast = 0f
    /** -1..1 */
    var saturation = 0f
    var filter = Filter.ORIGINAL

    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val restorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ---- geometry ---------------------------------------------------------------------------

    fun rotate() = transform(Matrix().apply { postRotate(90f) })

    fun flip() = transform(Matrix().apply { postScale(-1f, 1f) })

    private fun transform(m: Matrix) {
        source = Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        cutout = Bitmap.createBitmap(cutout, 0, 0, cutout.width, cutout.height, m, true)
            .let { if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true) }
    }

    // ---- cut-out ----------------------------------------------------------------------------

    /** Replaces the cut-out with [alphaMask] (an ALPHA_8 bitmap the size of [source]) applied to the source. */
    fun applyMask(alphaMask: Bitmap) {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        Canvas(out).drawBitmap(alphaMask, null, android.graphics.Rect(0, 0, out.width, out.height), paint)
        cutout = out
        hasCutout = true
    }

    /** Puts every pixel back. */
    fun restoreBackground() {
        cutout = source.copy(Bitmap.Config.ARGB_8888, true)
        hasCutout = false
    }

    /**
     * A brush stroke from ([x0],[y0]) to ([x1],[y1]) in photo pixels, [radius] pixels wide. Erasing
     * clears pixels; restoring copies them back from [source].
     */
    fun brush(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float, erase: Boolean) {
        val path = Path().apply { moveTo(x0, y0); lineTo(x1, y1) }
        val canvas = Canvas(cutout)
        if (erase) {
            erasePaint.strokeWidth = radius * 2
            canvas.drawPath(path, erasePaint)
            hasCutout = true
        } else {
            restorePaint.strokeWidth = radius * 2
            canvas.save()
            // Stroke the path into the clip, then paint the source through it.
            val strokePath = Path()
            restorePaint.getFillPath(path, strokePath)
            canvas.clipPath(strokePath)
            canvas.drawBitmap(source, 0f, 0f, null)
            canvas.restore()
        }
    }

    // ---- colour -----------------------------------------------------------------------------

    val hasColorEdits: Boolean
        get() = brightness != 0f || contrast != 0f || saturation != 0f || filter != Filter.ORIGINAL

    fun resetColor() {
        brightness = 0f
        contrast = 0f
        saturation = 0f
        filter = Filter.ORIGINAL
    }

    /** The combined adjustments + filter, or null when everything is at its default. */
    fun colorFilter(): ColorMatrixColorFilter? {
        if (!hasColorEdits) return null
        val m = ColorMatrix(filter.matrix)
        if (saturation != 0f) m.postConcat(ColorMatrix().apply { setSaturation(1f + saturation) })
        if (contrast != 0f) m.postConcat(contrast(1f + contrast * 0.8f))
        if (brightness != 0f) {
            val b = brightness * 100f
            m.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        return ColorMatrixColorFilter(m)
    }

    companion object {
        /** Photos are shrunk to this many pixels on the long side so brushing stays responsive. */
        const val MAX_SIDE = 1024

        private fun contrast(c: Float): ColorMatrix {
            val t = (1f - c) * 128f
            return ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            ))
        }

        private fun fit(b: Bitmap): Bitmap {
            val long = maxOf(b.width, b.height)
            val scaled = if (long > MAX_SIDE) {
                val s = MAX_SIDE / long.toFloat()
                Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
            } else b
            return if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false)
        }
    }
}
