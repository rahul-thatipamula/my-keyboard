package com.caxone.my_keyboard.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.media.StickerRenderer
import com.caxone.my_keyboard.media.StickerStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * Builds a sticker from any mix of a photo, an emoji and a caption, with a live preview.
 * Opened from the keyboard's Stickers tab and from the Stickers screen.
 */
class StickerMakerActivity : AppCompatActivity() {

    private lateinit var preview: ImageView
    private lateinit var caption: EditText
    private lateinit var emoji: EditText
    private lateinit var btnClearPhoto: MaterialButton

    private var photo: Bitmap? = null
    private var background = BACKGROUNDS[0]
    private var textColor = Color.WHITE
    private val bgSwatches = ArrayList<View>()
    private val textSwatches = ArrayList<View>()

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_maker)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        preview = findViewById(R.id.preview)
        caption = findViewById(R.id.caption)
        emoji = findViewById(R.id.emoji)
        btnClearPhoto = findViewById(R.id.btnClearPhoto)

        findViewById<MaterialButton>(R.id.btnPhoto).setOnClickListener { pickPhoto.launch("image/*") }
        btnClearPhoto.setOnClickListener {
            photo = null
            btnClearPhoto.visibility = View.GONE
            render()
        }
        caption.doAfterTextChanged { render() }
        emoji.doAfterTextChanged { render() }

        val bgRow = findViewById<LinearLayout>(R.id.backgrounds)
        for (c in BACKGROUNDS) bgRow.addView(swatch(c, bgSwatches) { background = c; render() })
        val textRow = findViewById<LinearLayout>(R.id.textColors)
        for (c in TEXT_COLORS) textRow.addView(swatch(c, textSwatches) { textColor = c; render() })
        refreshSwatches()

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        render()
    }

    private fun spec() = StickerRenderer.Spec(
        photo = photo,
        background = background,
        caption = caption.text?.toString()?.trim() ?: "",
        captionColor = textColor,
        emoji = emoji.text?.toString()?.trim() ?: ""
    )

    private fun render() {
        preview.setImageBitmap(StickerRenderer.render(spec(), 384))
    }

    private fun save() {
        val s = spec()
        if (s.photo == null && s.caption.isEmpty() && s.emoji.isEmpty()) {
            Toast.makeText(this, R.string.maker_empty, Toast.LENGTH_SHORT).show()
            return
        }
        StickerStore.save(this, StickerRenderer.render(s))
        Toast.makeText(this, R.string.maker_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun loadPhoto(uri: Uri) {
        try {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
                    decoder.setTargetSampleSize(2)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            photo = bmp
            btnClearPhoto.visibility = View.VISIBLE
            render()
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage ?: "Couldn't load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun swatch(color: Int, group: MutableList<View>, onPick: () -> Unit): View {
        val d = resources.displayMetrics.density
        val v = View(this)
        v.tag = color
        v.layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).also {
            it.setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        }
        v.setOnClickListener { onPick(); refreshSwatches() }
        group.add(v)
        return v
    }

    private fun refreshSwatches() {
        val d = resources.displayMetrics.density
        fun paint(v: View, selected: Boolean) {
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(v.tag as Int)
                setStroke(((if (selected) 3 else 1) * d).toInt(), if (selected) getColor(R.color.brand_500) else Color.argb(60, 0, 0, 0))
            }
        }
        for (v in bgSwatches) paint(v, v.tag == background)
        for (v in textSwatches) paint(v, v.tag == textColor)
    }

    companion object {
        private val BACKGROUNDS = intArrayOf(
            0xFFFFC107.toInt(), 0xFFFF7043.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF3F51B5.toInt(),
            0xFF1A73E8.toInt(), 0xFF00BCD4.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFF795548.toInt(),
            0xFF607D8B.toInt(), 0xFF202124.toInt(), 0xFFFFFFFF.toInt()
        )
        private val TEXT_COLORS = intArrayOf(Color.WHITE, Color.BLACK, 0xFFFFEB3B.toInt(), 0xFF1A73E8.toInt(), 0xFFE91E63.toInt())
    }
}
