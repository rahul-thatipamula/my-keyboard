package com.caxone.my_keyboard.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.media.BackgroundRemover
import com.caxone.my_keyboard.media.PhotoEditor
import com.caxone.my_keyboard.media.StickerRenderer
import com.caxone.my_keyboard.media.StickerStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout

/**
 * The sticker editor: a live preview you can drag, pinch and brush on, plus tool tabs for the
 * photo (AI background removal, rotate, flip, zoom), colour adjustments, filters, text and
 * style (background, outline). Opened from the keyboard's Stickers tab and the Stickers screen.
 */
class StickerMakerActivity : AppCompatActivity() {

    private lateinit var canvas: StickerCanvasView
    private lateinit var caption: EditText
    private lateinit var emoji: EditText
    private lateinit var tabs: TabLayout
    private lateinit var panels: List<View>

    private lateinit var modeRow: View
    private lateinit var brushRow: View
    private lateinit var photoTools: View
    private lateinit var btnClearPhoto: MaterialButton
    private lateinit var btnRemoveBg: MaterialButton
    private lateinit var btnRestoreBg: MaterialButton
    private lateinit var zoomSlider: Slider
    private lateinit var brightness: Slider
    private lateinit var contrast: Slider
    private lateinit var saturation: Slider
    private lateinit var outlineWidthRow: View
    private lateinit var outlineWidth: Slider

    private var editor: PhotoEditor? = null
    private var remover: BackgroundRemover? = null
    private var removing = false

    private var background = BACKGROUNDS[0]
    private var transparent = false
    private var textColor = Color.WHITE
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var outlineColor: Int? = null
    private var brushSize = 0.12f

    private val bgSwatches = ArrayList<View>()
    private val textSwatches = ArrayList<View>()

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_maker)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        canvas = StickerCanvasView(this).also {
            findViewById<FrameLayout>(R.id.canvasHost).addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            it.onPan = { dx, dy -> if (editor != null) { panX += dx; panY += dy; render() } }
            it.onZoom = { f -> if (editor != null) setZoom(zoom * f, fromSlider = false) }
            it.onBrush = { x0, y0, x1, y1 -> brush(x0, y0, x1, y1) }
            it.onBrushEnd = { refreshPhotoButtons() }
        }
        caption = findViewById(R.id.caption)
        emoji = findViewById(R.id.emoji)
        caption.doAfterTextChanged { render() }
        emoji.doAfterTextChanged { render() }

        setupTabs()
        setupPhotoPanel()
        setupModeRow()
        setupAdjustPanel()
        setupFilterPanel()
        setupStylePanel()

        val textRow = findViewById<LinearLayout>(R.id.textColors)
        for (c in TEXT_COLORS) textRow.addView(swatch(c, textSwatches) { textColor = c; render() })
        refreshSwatches()

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        remover?.close()
    }

    // ---- tabs -------------------------------------------------------------------------------

    private fun setupTabs() {
        tabs = findViewById(R.id.tabs)
        panels = listOf(
            findViewById(R.id.panelPhoto), findViewById(R.id.panelAdjust), findViewById(R.id.panelFilters),
            findViewById(R.id.panelText), findViewById(R.id.panelStyle)
        )
        val titles = intArrayOf(R.string.maker_tab_photo, R.string.maker_tab_adjust, R.string.maker_tab_filters, R.string.maker_tab_text, R.string.maker_tab_style)
        for (t in titles) tabs.addTab(tabs.newTab().setText(t))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showPanel(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        showPanel(0)
    }

    private fun showPanel(index: Int) {
        for ((i, p) in panels.withIndex()) p.visibility = if (i == index) View.VISIBLE else View.GONE
    }

    // ---- photo ------------------------------------------------------------------------------

    private fun setupPhotoPanel() {
        photoTools = findViewById(R.id.photoTools)
        btnClearPhoto = findViewById(R.id.btnClearPhoto)
        btnRemoveBg = findViewById(R.id.btnRemoveBg)
        btnRestoreBg = findViewById(R.id.btnRestoreBg)
        zoomSlider = findViewById(R.id.zoom)

        findViewById<MaterialButton>(R.id.btnPhoto).setOnClickListener { pickPhoto.launch("image/*") }
        btnClearPhoto.setOnClickListener { setEditor(null) }
        btnRemoveBg.setOnClickListener { removeBackground() }
        btnRestoreBg.setOnClickListener { editor?.restoreBackground(); refreshPhotoButtons(); render() }
        findViewById<MaterialButton>(R.id.btnRotate).setOnClickListener { editor?.rotate(); render() }
        findViewById<MaterialButton>(R.id.btnFlip).setOnClickListener { editor?.flip(); render() }
        findViewById<MaterialButton>(R.id.btnCentre).setOnClickListener { panX = 0f; panY = 0f; setZoom(1f, fromSlider = false) }
        zoomSlider.addOnChangeListener { _, value, fromUser -> if (fromUser) setZoom(value, fromSlider = true) }
    }

    private fun setZoom(value: Float, fromSlider: Boolean) {
        zoom = value.coerceIn(zoomSlider.valueFrom, zoomSlider.valueTo)
        if (!fromSlider) zoomSlider.value = zoom
        render()
    }

    private fun setEditor(e: PhotoEditor?) {
        editor = e
        panX = 0f
        panY = 0f
        setZoom(1f, fromSlider = false)
        val has = e != null
        btnClearPhoto.visibility = if (has) View.VISIBLE else View.GONE
        photoTools.visibility = if (has) View.VISIBLE else View.GONE
        modeRow.visibility = if (has) View.VISIBLE else View.GONE
        if (!has) selectMode(StickerCanvasView.Mode.MOVE)
        refreshPhotoButtons()
        render()
    }

    private fun refreshPhotoButtons() {
        val e = editor
        btnRemoveBg.isEnabled = e != null && !removing
        btnRemoveBg.setText(if (removing) R.string.maker_remove_bg_working else R.string.maker_remove_bg)
        btnRestoreBg.visibility = if (e != null && e.hasCutout) View.VISIBLE else View.GONE
        outlineWidthRow.visibility = if (outlineColor != null) View.VISIBLE else View.GONE
    }

    private fun removeBackground() {
        val e = editor ?: return
        if (removing) return
        removing = true
        refreshPhotoButtons()
        val source = e.source
        Thread {
            var mask: Bitmap? = null
            var error: String? = null
            try {
                val r = remover ?: BackgroundRemover(this).also { remover = it }
                mask = r.mask(source)
            } catch (t: Throwable) {
                error = t.localizedMessage ?: t.javaClass.simpleName
            }
            runOnUiThread {
                removing = false
                val m = mask
                when {
                    error != null -> Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    // The photo may have been swapped or rotated while the model was running.
                    editor !== e || e.source !== source -> Unit
                    m == null -> Toast.makeText(this, R.string.maker_remove_bg_failed, Toast.LENGTH_SHORT).show()
                    else -> { e.applyMask(m); transparent = true; refreshSwatches() }
                }
                refreshPhotoButtons()
                render()
            }
        }.start()
    }

    // ---- brushes ----------------------------------------------------------------------------

    private fun setupModeRow() {
        modeRow = findViewById(R.id.modeRow)
        brushRow = findViewById(R.id.brushRow)
        findViewById<ChipGroup>(R.id.modeGroup).setOnCheckedStateChangeListener { _, ids ->
            selectMode(
                when (ids.firstOrNull()) {
                    R.id.modeErase -> StickerCanvasView.Mode.ERASE
                    R.id.modeRestore -> StickerCanvasView.Mode.RESTORE
                    else -> StickerCanvasView.Mode.MOVE
                }
            )
        }
        findViewById<Slider>(R.id.brushSize).addOnChangeListener { _, v, _ -> brushSize = v; canvas.brushSize = v }
    }

    private fun selectMode(mode: StickerCanvasView.Mode) {
        canvas.mode = mode
        brushRow.visibility = if (mode == StickerCanvasView.Mode.MOVE) View.GONE else View.VISIBLE
        findViewById<Chip>(
            when (mode) {
                StickerCanvasView.Mode.MOVE -> R.id.modeMove
                StickerCanvasView.Mode.ERASE -> R.id.modeErase
                StickerCanvasView.Mode.RESTORE -> R.id.modeRestore
            }
        ).isChecked = true
    }

    /** Converts a stroke in sticker space to photo pixels and applies it. */
    private fun brush(x0: Float, y0: Float, x1: Float, y1: Float) {
        val e = editor ?: return
        val inverse = Matrix()
        if (!StickerRenderer.photoMatrix(spec(), PREVIEW_SIZE).invert(inverse)) return
        val pts = floatArrayOf(x0 * PREVIEW_SIZE, y0 * PREVIEW_SIZE, x1 * PREVIEW_SIZE, y1 * PREVIEW_SIZE)
        inverse.mapPoints(pts)
        // Radius in photo pixels: the brush covers brushSize of the sticker, and the sticker's
        // short-side scale is size / min(w, h) * zoom.
        val radius = brushSize / 2f * minOf(e.source.width, e.source.height) / zoom
        e.brush(pts[0], pts[1], pts[2], pts[3], radius, erase = canvas.mode == StickerCanvasView.Mode.ERASE)
        render()
    }

    // ---- adjust & filters -------------------------------------------------------------------

    private fun setupAdjustPanel() {
        brightness = findViewById(R.id.brightness)
        contrast = findViewById(R.id.contrast)
        saturation = findViewById(R.id.saturation)
        brightness.addOnChangeListener { _, v, _ -> editor?.brightness = v; render() }
        contrast.addOnChangeListener { _, v, _ -> editor?.contrast = v; render() }
        saturation.addOnChangeListener { _, v, _ -> editor?.saturation = v; render() }
        findViewById<MaterialButton>(R.id.btnResetAdjust).setOnClickListener {
            brightness.value = 0f
            contrast.value = 0f
            saturation.value = 0f
            editor?.resetColor()
            findViewById<ChipGroup>(R.id.filterGroup).check(PhotoEditor.Filter.ORIGINAL.ordinal + 1)
            render()
        }
    }

    private fun setupFilterPanel() {
        val group = findViewById<ChipGroup>(R.id.filterGroup)
        for (f in PhotoEditor.Filter.entries) {
            val chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                id = f.ordinal + 1 // ChipGroup ids must be positive; 0 would be View.NO_ID
                text = f.label
                isCheckable = true
                isChecked = f == PhotoEditor.Filter.ORIGINAL
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { _, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            editor?.filter = PhotoEditor.Filter.entries[id - 1]
            render()
        }
    }

    // ---- style ------------------------------------------------------------------------------

    private fun setupStylePanel() {
        outlineWidthRow = findViewById(R.id.outlineWidthRow)
        outlineWidth = findViewById(R.id.outlineWidth)
        val bgRow = findViewById<LinearLayout>(R.id.backgrounds)
        bgRow.addView(swatch(TRANSPARENT_TAG, bgSwatches) { transparent = true; render() })
        for (c in BACKGROUNDS) bgRow.addView(swatch(c, bgSwatches) { background = c; transparent = false; render() })

        findViewById<ChipGroup>(R.id.outlineGroup).setOnCheckedStateChangeListener { _, ids ->
            outlineColor = when (ids.firstOrNull()) {
                R.id.outlineWhite -> Color.WHITE
                R.id.outlineBlack -> Color.BLACK
                else -> null
            }
            refreshPhotoButtons()
            render()
        }
        outlineWidth.addOnChangeListener { _, _, _ -> render() }
    }

    // ---- rendering --------------------------------------------------------------------------

    private fun spec(): StickerRenderer.Spec {
        val e = editor
        return StickerRenderer.Spec(
            photo = e?.cutout,
            background = background,
            transparent = transparent,
            caption = caption.text?.toString()?.trim() ?: "",
            captionColor = textColor,
            emoji = emoji.text?.toString()?.trim() ?: "",
            zoom = zoom,
            panX = panX,
            panY = panY,
            colorFilter = e?.colorFilter(),
            outlineWidth = if (e != null && outlineColor != null) outlineWidth.value else 0f,
            outlineColor = outlineColor ?: Color.WHITE
        )
    }

    private fun render() {
        if (!::canvas.isInitialized || !::outlineWidth.isInitialized) return
        canvas.bitmap = StickerRenderer.render(spec(), PREVIEW_SIZE)
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
            brightness.value = 0f
            contrast.value = 0f
            saturation.value = 0f
            findViewById<ChipGroup>(R.id.filterGroup).check(PhotoEditor.Filter.ORIGINAL.ordinal + 1)
            transparent = false
            refreshSwatches()
            setEditor(PhotoEditor(bmp))
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage ?: "Couldn't load image", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- swatches ---------------------------------------------------------------------------

    private fun swatch(color: Int, group: MutableList<View>, onPick: () -> Unit): View {
        val d = resources.displayMetrics.density
        val v = View(this)
        v.tag = color
        v.layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).also {
            it.setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        }
        v.contentDescription = if (color == TRANSPARENT_TAG) getString(R.string.maker_bg_transparent) else null
        v.setOnClickListener { onPick(); refreshSwatches() }
        group.add(v)
        return v
    }

    private fun refreshSwatches() {
        if (!::canvas.isInitialized) return
        val d = resources.displayMetrics.density
        fun paint(v: View, selected: Boolean) {
            val color = v.tag as Int
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (color == TRANSPARENT_TAG) {
                    setColor(Color.WHITE)
                    setStroke(((if (selected) 3 else 1) * d).toInt(), if (selected) getColor(R.color.brand_500) else Color.argb(60, 0, 0, 0), 4 * d, 3 * d)
                } else {
                    setColor(color)
                    setStroke(((if (selected) 3 else 1) * d).toInt(), if (selected) getColor(R.color.brand_500) else Color.argb(60, 0, 0, 0))
                }
            }
        }
        for (v in bgSwatches) paint(v, if (transparent) v.tag == TRANSPARENT_TAG else v.tag == background)
        for (v in textSwatches) paint(v, v.tag == textColor)
    }

    companion object {
        private const val PREVIEW_SIZE = 512
        /** Marker tag for the "no background" swatch; not a real colour. */
        private const val TRANSPARENT_TAG = 1

        private val BACKGROUNDS = intArrayOf(
            0xFFFFC107.toInt(), 0xFFFF7043.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF3F51B5.toInt(),
            0xFF1A73E8.toInt(), 0xFF00BCD4.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFF795548.toInt(),
            0xFF607D8B.toInt(), 0xFF202124.toInt(), 0xFFFFFFFF.toInt()
        )
        private val TEXT_COLORS = intArrayOf(Color.WHITE, Color.BLACK, 0xFFFFEB3B.toInt(), 0xFF1A73E8.toInt(), 0xFFE91E63.toInt())
    }
}
