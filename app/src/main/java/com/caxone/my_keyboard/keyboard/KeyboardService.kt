package com.caxone.my_keyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.caxone.my_keyboard.R
import com.caxone.my_keyboard.media.EmojiPanel
import com.caxone.my_keyboard.media.GifPanel
import com.caxone.my_keyboard.media.MediaBar
import com.caxone.my_keyboard.media.StickerPanel
import com.caxone.my_keyboard.media.StickerSender
import com.caxone.my_keyboard.media.StickerStore
import com.caxone.my_keyboard.prediction.Dictionary
import com.caxone.my_keyboard.prediction.EditDistance
import com.caxone.my_keyboard.prediction.GlideDecoder
import com.caxone.my_keyboard.prediction.Predictor
import com.caxone.my_keyboard.prediction.UserModel
import com.caxone.my_keyboard.settings.Prefs
import com.caxone.my_keyboard.theme.ThemeStore

class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var dictionary: Dictionary
    private lateinit var userModel: UserModel
    private lateinit var predictor: Predictor
    private lateinit var glideDecoder: GlideDecoder

    private var root: LinearLayout? = null
    private lateinit var strip: SuggestionStrip
    private lateinit var keyboard: KeyboardView
    private lateinit var panelHost: FrameLayout
    private lateinit var mediaBar: MediaBar
    private lateinit var emojiPanel: EmojiPanel
    private lateinit var gifPanel: GifPanel
    private lateinit var stickerPanel: StickerPanel

    /** The word currently being typed (underlined in the editor). */
    private val composing = StringBuilder()
    private var prev1: String? = null
    private var prev2: String? = null

    private var suggestionsOn = true
    private var capSentences = false
    private var result: Predictor.Result? = null
    private var nextWords: List<String> = emptyList()

    /** Set right after an autocorrect so a single backspace can undo it. */
    private class AutoCorrectRecord(val typed: String, val committed: String, val prev2: String?, val prev1: String?)
    private var lastAutoCorrect: AutoCorrectRecord? = null

    private var lastKeyWasSpace = false
    private var lastSpaceTime = 0L

    /** Alternatives for the word that was just glided, shown until the user moves on. */
    private var glideResult: Predictor.Result? = null
    private var composingFromGlide = false

    companion object {
        private val SENTENCE_END = charArrayOf('.', '!', '?')
        private val WORD_PATTERN = Regex("[a-z][a-z']*")
        private const val DOUBLE_SPACE_MS = 600L
        private val PUNCTUATION_AUTOCORRECT = setOf(".", ",", "!", "?", ";", ":")
    }

    // ---- lifecycle --------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        dictionary = Dictionary(this)
        userModel = UserModel.get(this)
        predictor = Predictor(dictionary, userModel)
        glideDecoder = GlideDecoder(dictionary, userModel)
        Thread {
            dictionary.load()
            userModel.load()
        }.start()
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        strip = SuggestionStrip(this).also { it.onSuggestionClick = { i -> onSuggestionPicked(i) } }
        keyboard = KeyboardView(this).also { it.listener = this }
        panelHost = FrameLayout(this).apply { visibility = View.GONE }
        mediaBar = MediaBar(this).also { it.onTabSelected = { tab -> showPanel(tab) } }

        emojiPanel = EmojiPanel(this).apply {
            onEmoji = { insertEmoji(it) }
            onBack = { mediaBar.select(null) }
            onBackspace = { handleDelete() }
        }
        gifPanel = GifPanel(this).apply {
            onBack = { mediaBar.select(null) }
            onBackspace = { handleDelete() }
        }
        stickerPanel = StickerPanel(this).apply {
            onSticker = { sendSticker(it) }
            onCreate = { openStickerMaker() }
            onDeleteRequest = { confirmDeleteSticker(it) }
            onBack = { mediaBar.select(null) }
            onBackspace = { handleDelete() }
        }

        val wrap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        container.addView(strip, wrap)
        container.addView(keyboard, wrap)
        container.addView(panelHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        container.addView(mediaBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (MediaBar.HEIGHT_DP * resources.displayMetrics.density).toInt()))
        root = container
        applyTheme()
        return container
    }

    // ---- media panels -----------------------------------------------------------------------

    /** Swaps the keys for the chosen panel (or back to the keys when [tab] is null). */
    private fun showPanel(tab: MediaBar.Tab?) {
        if (composing.isNotEmpty()) commitComposing(false)
        panelHost.removeAllViews()
        if (tab == null) {
            panelHost.visibility = View.GONE
            keyboard.visibility = View.VISIBLE
            strip.visibility = if (suggestionsOn) View.VISIBLE else View.GONE
            refreshSuggestions()
            return
        }
        val panel = when (tab) {
            MediaBar.Tab.EMOJI -> emojiPanel.also { it.refresh() }
            MediaBar.Tab.GIF -> gifPanel
            MediaBar.Tab.STICKERS -> stickerPanel.also { it.refresh() }
        }
        val height = keyboard.height + (if (strip.visibility == View.VISIBLE) strip.height else 0)
        val fallback = (300 * resources.displayMetrics.density).toInt()
        panelHost.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, if (height > 0) height else fallback))
        keyboard.visibility = View.GONE
        strip.visibility = View.GONE
        panelHost.visibility = View.VISIBLE
    }

    private fun closePanel() {
        if (mediaBar.selected != null) {
            mediaBar.reset()
            showPanel(null)
        }
    }

    private fun insertEmoji(emoji: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) commitComposing(false)
        ic.commitText(emoji, 1)
        lastAutoCorrect = null
        lastKeyWasSpace = false
    }

    private fun sendSticker(file: java.io.File) {
        val uri = StickerStore.uri(this, file)
        val inline = StickerSender.send(this, currentInputConnection, currentInputEditorInfo, uri)
        if (!inline) Toast.makeText(this, R.string.sticker_shared_fallback, Toast.LENGTH_SHORT).show()
    }

    private fun openStickerMaker() {
        val intent = Intent(this, com.caxone.my_keyboard.ui.StickerMakerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun confirmDeleteSticker(file: java.io.File) {
        val themed = ContextThemeWrapper(this, R.style.Theme_Mykeyboard)
        val dialog = AlertDialog.Builder(themed)
            .setTitle(R.string.sticker_delete)
            .setMessage(R.string.sticker_delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                StickerStore.delete(file)
                stickerPanel.refresh()
            }
            .create()
        // Dialogs from an IME must be attached to the keyboard window.
        dialog.window?.let { w ->
            w.attributes.token = keyboard.windowToken
            @Suppress("DEPRECATION")
            w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            w.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        dialog.show()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun applyTheme() {
        val theme = ThemeStore.current(this)
        keyboard.theme = theme
        strip.applyTheme(theme)
        mediaBar.applyTheme(theme)
        emojiPanel.applyTheme(theme)
        gifPanel.applyTheme(theme)
        stickerPanel.applyTheme(theme)
        root?.setBackgroundColor(theme.background)
        keyboard.showPreview = Prefs.keyPopup(this)
        keyboard.hapticEnabled = Prefs.vibrate(this)
        keyboard.soundEnabled = Prefs.sound(this)
        keyboard.glideEnabled = Prefs.glide(this)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyTheme()
        composing.setLength(0)
        lastAutoCorrect = null
        lastKeyWasSpace = false
        clearGlide()
        prev1 = null
        prev2 = null

        val inputType = info.inputType
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        val password = (cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val noSuggest = (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 ||
                variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        suggestionsOn = !numeric && !password && !noSuggest
        capSentences = Prefs.autoCap(this) && cls == InputType.TYPE_CLASS_TEXT &&
            (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0

        keyboard.layout = if (numeric) Layouts.symbols() else lettersLayout()
        keyboard.shift = KeyboardView.ShiftState.OFF
        keyboard.enterLabel = enterLabelFor(info)
        strip.visibility = if (suggestionsOn) View.VISIBLE else View.GONE
        closePanel()

        seedContextFromEditor()
        updateShift()
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.finishComposingText()
        composing.setLength(0)
        closePanel()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // The user moved the cursor away from the word we were composing.
            composing.setLength(0)
            currentInputConnection?.finishComposingText()
            lastAutoCorrect = null
            clearGlide()
            seedContextFromEditor()
            updateShift()
            refreshSuggestions()
        } else if (composing.isEmpty() && newSelStart != oldSelStart) {
            seedContextFromEditor()
            updateShift()
            refreshSuggestions()
        }
    }

    /** Builds the letter layout the user picked and points autocorrect's adjacency map at it. */
    private fun lettersLayout(): Layout {
        val layout = Prefs.layout(this)
        EditDistance.useLayout(Layouts.letterPositions(layout))
        return Layouts.letters(layout, Prefs.numberRow(this))
    }

    private fun enterLabelFor(info: EditorInfo): String {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> "→"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "↵"
        }
    }

    // ---- context ----------------------------------------------------------------------------

    /** Looks at the text before the cursor to recover the previous words and sentence position. */
    private fun seedContextFromEditor() {
        prev1 = null
        prev2 = null
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        if (before.isEmpty()) return
        val trimmed = before.trimEnd(' ')
        if (trimmed.isEmpty() || trimmed.last() in SENTENCE_END || trimmed.last() == '\n') return
        if (!before.endsWith(" ")) return // cursor is mid-word; leave context empty
        val tokens = before.split(Regex("[^A-Za-z']+")).filter { it.isNotEmpty() }
        prev1 = tokens.lastOrNull()?.lowercase()
        prev2 = tokens.dropLast(1).lastOrNull()?.lowercase()
    }

    /** Auto-capitalises at the start of a sentence when the field asks for it. */
    private fun updateShift() {
        if (!capSentences || keyboard.shift == KeyboardView.ShiftState.LOCKED || composing.isNotEmpty()) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(4, 0)?.toString() ?: ""
        val trimmed = before.trimEnd(' ')
        val shouldCap = trimmed.isEmpty() || trimmed.last() in SENTENCE_END || trimmed.last() == '\n'
        keyboard.shift = if (shouldCap) KeyboardView.ShiftState.ON else KeyboardView.ShiftState.OFF
    }

    // ---- suggestions ------------------------------------------------------------------------

    private fun refreshSuggestions() {
        if (!suggestionsOn) return
        if (!dictionary.isLoaded) {
            strip.clear()
            return
        }
        if (composing.isEmpty()) {
            result = null
            nextWords = if (Prefs.prediction(this)) predictor.nextWords(prev2, prev1) else emptyList()
            strip.setSuggestions(nextWords.map { caseForNext(it) }, -1)
        } else {
            val typed = composing.toString()
            val g = glideResult
            val r = if (g != null && composingFromGlide) g else predictor.forComposing(typed, prev2, prev1, Prefs.autocorrect(this))
            result = r
            strip.setSuggestions(r.words.map { applyCase(it, typed) }, if (r.autoCorrect) r.primary else -1)
        }
    }

    /** Matches a suggestion's case to what the user typed (e.g. "Hel" -> "Hello", "HEL" -> "HELLO"). */
    private fun applyCase(word: String, typed: String): String {
        if (word.isEmpty()) return word
        if (word.equals(typed, ignoreCase = true)) return typed
        val fixed = fixPronounI(word)
        if (typed.isEmpty() || !typed[0].isUpperCase()) return fixed
        val allCaps = typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() }
        return if (allCaps) fixed.uppercase() else fixed.replaceFirstChar { it.uppercase() }
    }

    private fun caseForNext(word: String): String {
        val fixed = fixPronounI(word)
        return when (keyboard.shift) {
            KeyboardView.ShiftState.LOCKED -> fixed.uppercase()
            KeyboardView.ShiftState.ON -> fixed.replaceFirstChar { it.uppercase() }
            KeyboardView.ShiftState.OFF -> fixed
        }
    }

    private fun fixPronounI(word: String): String =
        if (word == "i" || word.startsWith("i'")) "I" + word.substring(1) else word

    // ---- key handling -----------------------------------------------------------------------

    override fun onKey(key: Key) {
        when (key.type) {
            KeyType.CHAR -> handleChar(key)
            KeyType.SPACE -> handleSpace()
            KeyType.DELETE -> handleDelete()
            KeyType.ENTER -> handleEnter()
            KeyType.TO_SYMBOLS -> keyboard.layout = Layouts.symbols()
            KeyType.TO_SYMBOLS2 -> keyboard.layout = Layouts.symbols2()
            KeyType.TO_LETTERS -> keyboard.layout = lettersLayout()
            KeyType.SHIFT -> Unit
        }
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) commitComposing(false)
        clearGlide()
        ic.commitText(text, 1)
        lastAutoCorrect = null
        lastKeyWasSpace = false
        updateShift()
        refreshSuggestions()
    }

    override fun onGlide(path: GlideDecoder.GlidePath) {
        val ic = currentInputConnection ?: return
        if (!dictionary.isLoaded) return
        val words = glideDecoder.decode(path)
        if (words.isEmpty()) return

        // A glided word always stands alone: finish whatever came before and separate with a space.
        if (composing.isNotEmpty()) {
            commitComposing(!composingFromGlide)
            ic.commitText(" ", 1)
        } else if (needsSpaceBeforeWord(ic)) {
            ic.commitText(" ", 1)
        }

        val shiftWas = keyboard.shift
        val word = when (shiftWas) {
            KeyboardView.ShiftState.LOCKED -> words[0].uppercase()
            KeyboardView.ShiftState.ON -> words[0].replaceFirstChar { it.uppercase() }
            KeyboardView.ShiftState.OFF -> words[0]
        }
        if (shiftWas == KeyboardView.ShiftState.ON) keyboard.shift = KeyboardView.ShiftState.OFF

        composing.setLength(0)
        composing.append(word)
        ic.setComposingText(composing, 1)
        val alternatives = ArrayList<String>(3)
        alternatives.add(word)
        for (w in words.drop(1)) alternatives.add(w)
        while (alternatives.size < 3) alternatives.add("")
        glideResult = Predictor.Result(alternatives, 0, false)
        composingFromGlide = true
        lastAutoCorrect = null
        lastKeyWasSpace = false
        refreshSuggestions()
    }

    /** After a glided word the cursor sits right after it; typing must not run into it. */
    private fun needsSpaceBeforeWord(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: return false
        return before.isNotEmpty() && before[0].isLetterOrDigit()
    }

    private fun clearGlide() {
        glideResult = null
        composingFromGlide = false
    }

    private fun handleChar(key: Key) {
        val ic = currentInputConnection ?: return
        var text = key.output
        if (key.isLetter && keyboard.shift != KeyboardView.ShiftState.OFF) text = text.uppercase()
        if (keyboard.shift == KeyboardView.ShiftState.ON) keyboard.shift = KeyboardView.ShiftState.OFF

        val isWordChar = text.length == 1 && (text[0].isLetter() || text[0] == '\'')
        if (suggestionsOn && isWordChar) {
            if (composingFromGlide) {
                // Typing after a glide starts a fresh word, like Gboard's automatic space.
                commitComposing(false)
                ic.commitText(" ", 1)
            }
            composing.append(text)
            ic.setComposingText(composing, 1)
            lastAutoCorrect = null
            refreshSuggestions()
        } else {
            if (composing.isNotEmpty()) commitComposing(text in PUNCTUATION_AUTOCORRECT)
            ic.commitText(text, 1)
            if (text.length == 1 && text[0] in SENTENCE_END) {
                prev1 = null
                prev2 = null
            }
            lastAutoCorrect = null
            updateShift()
            refreshSuggestions()
        }
        lastKeyWasSpace = false
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (composing.isNotEmpty()) {
            commitComposing(!composingFromGlide)
            ic.commitText(" ", 1)
            lastKeyWasSpace = true
            lastSpaceTime = now
        } else if (Prefs.doubleSpacePeriod(this) && lastKeyWasSpace && now - lastSpaceTime < DOUBLE_SPACE_MS && canInsertPeriod(ic)) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            prev1 = null
            prev2 = null
            lastAutoCorrect = null
            lastKeyWasSpace = false
        } else {
            ic.commitText(" ", 1)
            lastAutoCorrect = null
            lastKeyWasSpace = true
            lastSpaceTime = now
        }
        updateShift()
        refreshSuggestions()
    }

    private fun canInsertPeriod(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: return false
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    private fun handleDelete() {
        val ic = currentInputConnection ?: return
        val record = lastAutoCorrect
        if (record != null && composing.isEmpty()) {
            // Undo the autocorrect and put the original word back into composition.
            ic.deleteSurroundingText(record.committed.length + 1, 0)
            composing.setLength(0)
            composing.append(record.typed)
            ic.setComposingText(composing, 1)
            prev2 = record.prev2
            prev1 = record.prev1
            learn(record.typed) // the user insists on this spelling; remember it
            lastAutoCorrect = null
        } else if (composing.isNotEmpty()) {
            if (composingFromGlide) {
                // Backspace after a glide removes the whole word, since it was one gesture.
                composing.setLength(0)
                ic.setComposingText("", 1)
                ic.finishComposingText()
                clearGlide()
            } else {
                composing.setLength(composing.length - 1)
                ic.setComposingText(composing, 1)
                if (composing.isEmpty()) ic.finishComposingText()
            }
        } else {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                val before = ic.getTextBeforeCursor(2, 0)
                val count = if (before != null && before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
                ic.deleteSurroundingText(count, 0)
            }
            seedContextFromEditor()
        }
        lastKeyWasSpace = false
        updateShift()
        refreshSuggestions()
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) commitComposing(false)
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val hasAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && action in setOf(
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_PREVIOUS
        )
        if (hasAction) ic.performEditorAction(action) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        prev1 = null
        prev2 = null
        lastAutoCorrect = null
        lastKeyWasSpace = false
        updateShift()
        refreshSuggestions()
    }

    private fun onSuggestionPicked(index: Int) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            val r = result ?: return
            val word = r.words.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: return
            val typed = composing.toString()
            val out = applyCase(word, typed)
            ic.commitText("$out ", 1)
            composing.setLength(0)
            clearGlide()
            lastAutoCorrect = null
            learnAndPush(out)
        } else {
            val word = nextWords.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: return
            val out = caseForNext(word)
            ic.commitText("$out ", 1)
            learnAndPush(out)
        }
        if (keyboard.shift == KeyboardView.ShiftState.ON) keyboard.shift = KeyboardView.ShiftState.OFF
        lastKeyWasSpace = false
        updateShift()
        refreshSuggestions()
    }

    // ---- committing & learning ----------------------------------------------------------------

    private fun commitComposing(allowAutoCorrect: Boolean) {
        val ic = currentInputConnection ?: return
        val typed = composing.toString()
        val r = result
        var out = typed
        if (allowAutoCorrect && r != null && r.autoCorrect && Prefs.autocorrect(this)) {
            out = applyCase(r.words[r.primary], typed)
            lastAutoCorrect = AutoCorrectRecord(typed, out, prev2, prev1)
        } else {
            lastAutoCorrect = null
        }
        ic.commitText(out, 1)
        composing.setLength(0)
        clearGlide()
        learnAndPush(out)
    }

    private fun learn(word: String) {
        val w = word.lowercase()
        if (suggestionsOn && WORD_PATTERN.matches(w)) {
            userModel.learn(prev2, prev1 ?: Predictor.SENTENCE_START, w)
        }
    }

    private fun learnAndPush(word: String) {
        learn(word)
        prev2 = prev1
        prev1 = word.lowercase()
    }
}
