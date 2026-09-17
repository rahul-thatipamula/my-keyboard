package com.caxone.my_keyboard.media

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.io.Closeable
import java.nio.ByteOrder

/**
 * On-device background removal with MediaPipe's image segmenter and a bundled DeepLab v3
 * model (people, pets, vehicles and other everyday subjects). Nothing leaves the phone: the
 * model is an asset and inference runs on the CPU.
 */
class BackgroundRemover(private val context: Context) : Closeable {

    private var segmenter: ImageSegmenter? = null

    private fun segmenter(): ImageSegmenter = segmenter ?: ImageSegmenter.createFromOptions(
        context,
        ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(false)
            .setOutputConfidenceMasks(true)
            .build()
    ).also { segmenter = it }

    /**
     * An ALPHA_8 mask the size of [source]: opaque where the model sees a subject, clear where it
     * sees background, with a soft edge in between. Null when the model found no subject at all
     * (which would otherwise leave an empty sticker). Call off the main thread.
     */
    fun mask(source: Bitmap): Bitmap? {
        val input = if (source.config == Bitmap.Config.ARGB_8888) source else source.copy(Bitmap.Config.ARGB_8888, false)
        val image = BitmapImageBuilder(input).build()
        val result = segmenter().segment(image)
        val masks = result.confidenceMasks().get()
        val background = masks[0]
        val mw = background.width
        val mh = background.height
        val conf = ByteBufferExtractor.extract(background).order(ByteOrder.nativeOrder()).asFloatBuffer()

        val w = source.width
        val h = source.height
        val alpha = ByteArray(w * h)
        var kept = 0L
        for (y in 0 until h) {
            val my = (y * mh / h).coerceIn(0, mh - 1)
            for (x in 0 until w) {
                val mx = (x * mw / w).coerceIn(0, mw - 1)
                val subject = 1f - conf.get(my * mw + mx)
                val a = ((subject - EDGE_LOW) / (EDGE_HIGH - EDGE_LOW)).coerceIn(0f, 1f)
                alpha[y * w + x] = (a * 255f).toInt().toByte()
                if (a > 0.5f) kept++
            }
        }
        for (m in masks) m.close()
        image.close()
        if (kept < w.toLong() * h * MIN_SUBJECT_FRACTION) return null

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(alpha))
        return mask
    }

    override fun close() {
        segmenter?.close()
        segmenter = null
    }

    companion object {
        const val MODEL = "deeplab_v3.tflite"

        /** Subject confidence below this is background, above [EDGE_HIGH] is subject; between fades. */
        private const val EDGE_LOW = 0.35f
        private const val EDGE_HIGH = 0.65f
        /** A "subject" covering less of the photo than this is treated as nothing found. */
        private const val MIN_SUBJECT_FRACTION = 0.01f
    }
}
