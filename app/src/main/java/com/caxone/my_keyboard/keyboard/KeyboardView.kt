package com.caxone.my_keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.caxone.my_keyboard.prediction.GlideDecoder
import com.caxone.my_keyboard.theme.KeyboardTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Draws the keys and turns touches into key events. Supports multi-touch, key preview
 * popups, long-press hints, delete auto-repeat, shift / caps-lock, sliding between keys,
 * and glide typing (drawing a path across letters to spell a word).
 */
class KeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onKey(key: Key)
        fun onText(text: String)
        fun onGlide(path: GlideDecoder.GlidePath)
    }

    enum class ShiftState { OFF, ON, LOCKED }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardTheme.LIGHT
        set(value) { field = value; invalidate() }

    var layout: Layout = Layouts.qwerty()
        set(value) { field = value; geometryDirty = true; requestLayout(); invalidate() }

    var shift: ShiftState = ShiftState.OFF
        set(value) { field = value; invalidate() }

    var enterLabel: String = "↵"
        set(value) { field = value; invalidate() }

    var showPreview = true
    var hapticEnabled = true
    var soundEnabled = false
    var glideEnabled = true

    /** When false the view is purely decorative (used for the in-app preview). */
    var interactive = true

    private val density = resources.displayMetrics.density
    private val keyHeight = 52f * density
    private val padH = 3f * density
    private val padTop = 6f * density
    private val padBottom = 8f * density
    private val gapH = 3f * density
    private val gapV = 5f * density
    private val corner = 6f * density

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val rect = RectF()

    private var geometryDirty = true
    private val pressed = HashMap<Int, Key>()
    private var previewKey: Key? = null
    private var previewText: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null
    private var longPressConsumed = false
    private var lastShiftTap = 0L

    // Glide typing: a single pointer dragged across letter keys.
    private var gliding = false
    private var glidePointer = -1
    private var glideStartX = 0f
    private var glideStartY = 0f
    private val glidePoints = ArrayList<Float>()      // x0, y0, x1, y1, ...
    private val glideKeys = ArrayList<Key>()
    private val glideKeyPointIndex = ArrayList<Int>()  // index into glidePoints/2 where each key was entered
    private val trailPath = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    // ---- layout ---------------------------------------------------------------------------

    private fun rowHeight(row: List<Key>): Float = keyHeight * (row.firstOrNull()?.heightScale ?: 1f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (padTop + padBottom + layout.rows.sumOf { rowHeight(it).toDouble() }).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        geometryDirty = true
    }

    private fun computeGeometry() {
        val unit = (width - 2 * padH) / 10f
        var y = padTop
        for (row in layout.rows) {
            val total = row.sumOf { it.width.toDouble() }.toFloat()
            val rh = rowHeight(row)
            var x = padH + (10f - total) / 2f * unit
            for (key in row) {
                key.x = x
                key.y = y
                key.w = key.width * unit
                key.h = rh
                x += key.w
            }
            y += rh
        }
        geometryDirty = false
    }

    private fun keyAt(x: Float, y: Float): Key? = layout.keys.firstOrNull { it.contains(x, y) }

    // ---- drawing --------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        if (geometryDirty) computeGeometry()
        canvas.drawColor(theme.background)
        val pressedKeys = pressed.values
        for (key in layout.keys) drawKey(canvas, key, key in pressedKeys)
        if (gliding) drawTrail(canvas)
        drawPreview(canvas)
    }

    private fun drawTrail(canvas: Canvas) {
        if (glidePoints.size < 4) return
        trailPath.rewind()
        trailPath.moveTo(glidePoints[0], glidePoints[1])
        var i = 2
        while (i < glidePoints.size) {
            trailPath.lineTo(glidePoints[i], glidePoints[i + 1])
            i += 2
        }
        trailPaint.color = (theme.accent and 0x00FFFFFF) or 0xB0000000.toInt()
        trailPaint.strokeWidth = 7f * density
        canvas.drawPath(trailPath, trailPaint)
    }

    private fun labelFor(key: Key): String = when (key.type) {
        KeyType.CHAR -> if (key.isLetter && shift != ShiftState.OFF) key.label.uppercase() else key.label
        KeyType.SHIFT -> when (shift) {
            ShiftState.OFF -> "⇧"
            ShiftState.ON -> "⬆"
            ShiftState.LOCKED -> "⇪"
        }
        KeyType.DELETE -> "⌫"
        KeyType.ENTER -> enterLabel
        KeyType.SPACE -> "English"
        KeyType.TO_SYMBOLS -> "?123"
        KeyType.TO_SYMBOLS2 -> "=\\<"
        KeyType.TO_LETTERS -> "ABC"
    }

    private fun drawKey(canvas: Canvas, key: Key, isPressed: Boolean) {
        rect.set(key.x + gapH / 2, key.y + gapV / 2, key.x + key.w - gapH / 2, key.y + key.h - gapV / 2)

        val shiftActive = key.type == KeyType.SHIFT && shift != ShiftState.OFF
        keyPaint.color = when {
            isPressed -> theme.keyPressed
            key.type == KeyType.ENTER -> theme.accent
            shiftActive -> theme.accent
            key.isFunction -> theme.functionKeyBackground
            else -> theme.keyBackground
        }
        canvas.drawRoundRect(rect, corner, corner, keyPaint)

        val label = labelFor(key)
        textPaint.color = if (key.type == KeyType.ENTER || shiftActive) theme.accentText else theme.keyText
        textPaint.textSize = when {
            key.type == KeyType.SPACE -> sp(12f)
            key.type == KeyType.TO_SYMBOLS || key.type == KeyType.TO_LETTERS || key.type == KeyType.TO_SYMBOLS2 -> sp(15f)
            key.isFunction -> sp(20f)
            else -> sp(22f)
        }
        if (key.type == KeyType.SPACE) textPaint.color = theme.hintText
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), baseline, textPaint)

        key.hint?.let { hint ->
            hintPaint.color = theme.hintText
            hintPaint.textSize = sp(11f)
            canvas.drawText(hint, rect.right - 5 * density, rect.top + hintPaint.textSize + 3 * density, hintPaint)
        }
    }

    private fun drawPreview(canvas: Canvas) {
        val key = previewKey ?: return
        val text = previewText ?: return
        val w = key.w * 1.3f
        val h = key.h * 1.15f
        val left = (key.x + key.w / 2 - w / 2).coerceIn(0f, width - w)
        val top = (key.y - h + gapV).coerceAtLeast(0f)
        rect.set(left, top, left + w, top + h)
        keyPaint.color = theme.functionKeyBackground
        canvas.drawRoundRect(rect, corner * 1.5f, corner * 1.5f, keyPaint)
        textPaint.color = theme.keyText
        textPaint.textSize = sp(30f)
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, rect.centerX(), baseline, textPaint)
    }

    // ---- touch ----------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        if (geometryDirty) computeGeometry()
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val key = keyAt(event.getX(index), event.getY(index)) ?: return true
                // Gboard behaviour: pressing a second key commits the first immediately.
                if (pressed.isNotEmpty()) {
                    for ((_, k) in pressed.entries.toList()) fire(k)
                    pressed.clear()
                    cancelTimers()
                }
                pressed[pointerId] = key
                longPressConsumed = false
                if (pressed.size == 1) {
                    glideStartX = event.getX(index)
                    glideStartY = event.getY(index)
                }
                feedback()
                showPreviewFor(key, labelFor(key))
                scheduleLongPress(key)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (gliding) {
                    val gi = event.findPointerIndex(glidePointer)
                    if (gi >= 0) addGlidePoint(event.getX(gi), event.getY(gi))
                    return true
                }
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val current = pressed[id] ?: continue
                    val x = event.getX(i)
                    val y = event.getY(i)
                    if (canStartGlide(id, current) && hypot(x - glideStartX, y - glideStartY) > GLIDE_START_DISTANCE * density) {
                        val k = keyAt(x, y)
                        if (k != null && k !== current && k.isLetter) {
                            beginGlide(id, current, x, y)
                            return true
                        }
                    }
                    val k = keyAt(x, y)
                    if (k != null && k !== current) {
                        pressed[id] = k
                        cancelTimers()
                        longPressConsumed = false
                        showPreviewFor(k, labelFor(k))
                        scheduleLongPress(k)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (gliding && pointerId == glidePointer) {
                    addGlidePoint(event.getX(index), event.getY(index))
                    finishGlide()
                    return true
                }
                val key = pressed.remove(pointerId)
                cancelTimers()
                previewKey = null
                if (key != null && !longPressConsumed) fire(key)
                longPressConsumed = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed.clear()
                cancelTimers()
                previewKey = null
                resetGlide()
                invalidate()
            }
        }
        return true
    }

    // ---- glide ----------------------------------------------------------------------------

    private fun canStartGlide(pointerId: Int, key: Key): Boolean =
        glideEnabled && layout.isLetters && key.isLetter && pressed.size == 1 && !longPressConsumed &&
            pressed.containsKey(pointerId)

    private fun beginGlide(pointerId: Int, startKey: Key, x: Float, y: Float) {
        gliding = true
        glidePointer = pointerId
        pressed.clear()
        cancelTimers()
        previewKey = null
        glidePoints.clear()
        glideKeys.clear()
        glideKeyPointIndex.clear()
        glidePoints.add(glideStartX); glidePoints.add(glideStartY)
        glideKeys.add(startKey)
        glideKeyPointIndex.add(0)
        addGlidePoint(x, y)
    }

    private fun addGlidePoint(x: Float, y: Float) {
        val n = glidePoints.size
        if (n >= 2) {
            val dx = x - glidePoints[n - 2]
            val dy = y - glidePoints[n - 1]
            if (hypot(dx, dy) < GLIDE_SAMPLE_DISTANCE * density) return
        }
        glidePoints.add(x); glidePoints.add(y)
        val k = keyAt(x, y)
        if (k != null && k.isLetter && k !== glideKeys.last()) {
            glideKeys.add(k)
            glideKeyPointIndex.add(glidePoints.size / 2 - 1)
        }
        invalidate()
    }

    private fun finishGlide() {
        val path = buildGlidePath()
        resetGlide()
        invalidate()
        if (path != null) {
            feedback()
            listener?.onGlide(path)
        }
    }

    private fun resetGlide() {
        gliding = false
        glidePointer = -1
        glidePoints.clear()
        glideKeys.clear()
        glideKeyPointIndex.clear()
    }

    /**
     * Collapses the recorded points into the letter sequence plus a corner flag per letter.
     * A corner is a sharp change of direction, which almost always means the finger
     * deliberately stopped on that key.
     */
    private fun buildGlidePath(): GlideDecoder.GlidePath? {
        if (glideKeys.size < 2) return null
        val letters = StringBuilder(glideKeys.size)
        for (k in glideKeys) letters.append(k.output[0].lowercaseChar())

        val pointCount = glidePoints.size / 2
        val cornerPoint = BooleanArray(pointCount)
        val window = 3
        for (i in window until pointCount - window) {
            val ax = glidePoints[2 * i] - glidePoints[2 * (i - window)]
            val ay = glidePoints[2 * i + 1] - glidePoints[2 * (i - window) + 1]
            val bx = glidePoints[2 * (i + window)] - glidePoints[2 * i]
            val by = glidePoints[2 * (i + window) + 1] - glidePoints[2 * i + 1]
            var angle = abs(Math.toDegrees((atan2(by, bx) - atan2(ay, ax)).toDouble()))
            if (angle > 180) angle = 360 - angle
            if (angle > GLIDE_CORNER_DEGREES) cornerPoint[i] = true
        }

        val corners = BooleanArray(glideKeys.size)
        corners[0] = true
        corners[corners.size - 1] = true
        for (ki in 1 until glideKeys.size - 1) {
            val from = glideKeyPointIndex[ki]
            val to = glideKeyPointIndex[ki + 1]
            for (pi in from until to) if (cornerPoint[pi]) { corners[ki] = true; break }
        }
        return GlideDecoder.GlidePath(letters.toString(), corners)
    }

    private fun showPreviewFor(key: Key, text: String) {
        if (showPreview && key.type == KeyType.CHAR) {
            previewKey = key
            previewText = text
        } else {
            previewKey = null
        }
    }

    private fun scheduleLongPress(key: Key) {
        val r = Runnable { onLongPress(key) }
        longPressRunnable = r
        handler.postDelayed(r, LONG_PRESS_MS)
    }

    private fun cancelTimers() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        repeatRunnable = null
    }

    private fun onLongPress(key: Key) {
        when {
            key.type == KeyType.DELETE -> {
                longPressConsumed = true
                fire(key)
                val r = object : Runnable {
                    override fun run() {
                        fire(key)
                        feedback()
                        handler.postDelayed(this, REPEAT_MS)
                    }
                }
                repeatRunnable = r
                handler.postDelayed(r, REPEAT_MS)
            }
            key.type == KeyType.SHIFT -> {
                longPressConsumed = true
                shift = ShiftState.LOCKED
            }
            key.type == KeyType.SPACE -> {
                longPressConsumed = true
                previewKey = null
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
            key.hint != null -> {
                longPressConsumed = true
                previewText = key.hint
                feedback()
                invalidate()
                listener?.onText(key.hint)
            }
        }
    }

    private fun fire(key: Key) {
        if (key.type == KeyType.SHIFT) {
            val now = SystemClock.uptimeMillis()
            shift = when {
                shift == ShiftState.LOCKED -> ShiftState.OFF
                now - lastShiftTap < DOUBLE_TAP_MS -> ShiftState.LOCKED
                shift == ShiftState.ON -> ShiftState.OFF
                else -> ShiftState.ON
            }
            lastShiftTap = now
            return
        }
        listener?.onKey(key)
    }

    private fun feedback() {
        if (hapticEnabled) {
            @Suppress("DEPRECATION")
            performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
        if (soundEnabled) {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.6f)
        }
    }

    companion object {
        private const val LONG_PRESS_MS = 350L
        private const val REPEAT_MS = 45L
        private const val DOUBLE_TAP_MS = 300L
        private const val GLIDE_START_DISTANCE = 22f   // dp moved before a press becomes a glide
        private const val GLIDE_SAMPLE_DISTANCE = 4f   // dp between recorded trail points
        private const val GLIDE_CORNER_DEGREES = 55.0
    }
}
