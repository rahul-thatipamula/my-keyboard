package com.caxone.my_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

/**
 * The square live preview in the sticker editor. Shows the rendered sticker over a checkerboard
 * (so transparency is visible) and turns touches into pan / zoom / brush callbacks expressed in
 * sticker space, where (0,0)–(1,1) spans the sticker.
 */
class StickerCanvasView(context: Context) : View(context) {

    enum class Mode { MOVE, ERASE, RESTORE }

    var mode = Mode.MOVE
        set(value) { field = value; cursor = null; invalidate() }

    /** Rendered sticker to display; any size, drawn to fit. */
    var bitmap: Bitmap? = null
        set(value) { field = value; invalidate() }

    /** Brush diameter as a fraction of the sticker size, used to draw the cursor. */
    var brushSize = 0.12f
        set(value) { field = value; invalidate() }

    var onPan: ((dx: Float, dy: Float) -> Unit)? = null
    var onZoom: ((factor: Float) -> Unit)? = null
    /** From → to in sticker space; called for every segment of a stroke. */
    var onBrush: ((x0: Float, y0: Float, x1: Float, y1: Float) -> Unit)? = null
    var onBrushEnd: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val bounds = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val checker = Paint().apply { shader = checkerboard() }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
        setShadowLayer(3 * density, 0f, 0f, Color.BLACK)
    }
    private var cursor: Pair<Float, Float>? = null

    private var lastX = 0f
    private var lastY = 0f
    private var scaling = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaling = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onZoom?.invoke(detector.scaleFactor)
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { scaling = false }
    })

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val side = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) w else min(w, h)
        setMeasuredDimension(side, side)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = min(w, h).toFloat()
        bounds.set((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2)
    }

    override fun onDraw(canvas: Canvas) {
        val r = bounds.width() * 0.18f
        canvas.drawRoundRect(bounds, r, r, checker)
        bitmap?.let { canvas.drawBitmap(it, null, bounds, bitmapPaint) }
        val c = cursor
        if (c != null && mode != Mode.MOVE) {
            canvas.drawCircle(c.first, c.second, brushSize * bounds.width() / 2, cursorPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode == Mode.MOVE) scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                if (mode != Mode.MOVE) {
                    cursor = event.x to event.y
                    brush(event.x, event.y, event.x, event.y)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaling || event.pointerCount > 1) return true
                if (mode == Mode.MOVE) {
                    onPan?.invoke((event.x - lastX) / bounds.width(), (event.y - lastY) / bounds.height())
                } else {
                    cursor = event.x to event.y
                    brush(lastX, lastY, event.x, event.y)
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode != Mode.MOVE) onBrushEnd?.invoke()
                cursor = null
                invalidate()
            }
        }
        return true
    }

    private fun brush(x0: Float, y0: Float, x1: Float, y1: Float) {
        onBrush?.invoke(
            (x0 - bounds.left) / bounds.width(), (y0 - bounds.top) / bounds.height(),
            (x1 - bounds.left) / bounds.width(), (y1 - bounds.top) / bounds.height()
        )
    }

    private fun checkerboard(): Shader {
        val cell = (10 * density).toInt().coerceAtLeast(2)
        val b = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFE6E6E6.toInt())
        val dark = Paint().apply { color = 0xFFC8C8C8.toInt() }
        c.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), dark)
        c.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, dark)
        return BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
