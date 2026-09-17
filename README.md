# my-keyboard

A small, fully offline Android keyboard (IME) with on-device learning, autocorrect, next-word prediction, and custom themes.

It requests exactly one permission — `VIBRATE`. There is no `INTERNET` permission, so nothing you type can ever leave the device.

## Features

- **Six letter layouts** — QWERTY, QWERTZ, AZERTY, Dvorak, Colemak and Workman — chosen from a Layout screen with a live preview; autocorrect and glide adjacency follow whichever is active
- **Symbols and secondary-symbols pages** with long-press hints and key preview popups
- **Optional number row** above the letters; symbol pages carry long-press alternates (superscripts, currency, quotes, brackets)
- **Glide typing** — slide across letters to spell a word; decoded on-device from the path's corners against the dictionary and your own vocabulary
- **Media bar** under the keys with **Emoji** (built-in categorised set with recents), **GIF** (placeholder — no network by design) and **Stickers** tabs
- **Custom stickers** made right from the keyboard (photo, emoji and/or caption), stored locally, and sent inline to any app that accepts images via `commitContent`, with a share-sheet fallback
- **Sticker editor** with on-device **AI background removal** (MediaPipe image segmenter, bundled DeepLab v3 model — nothing is uploaded), erase / restore brushes, drag-and-pinch framing, rotate, flip, brightness / contrast / saturation, seven filters, a white or black die-cut outline and transparent backgrounds
- **Autocorrect** using a damerau-style edit distance with an adaptive threshold by word length; skips words that look like names and anything already in your personal vocabulary
- **Backspace undoes an autocorrect** and restores exactly what you typed
- **Intelligent language layer** — knows **English** and **Tenglish** (Telugu in Latin letters, ~770 everyday words and ~450 phrase seeds), detects which one the current sentence is in from the words before the cursor, and leans its completions, next words and phrases to match; never autocorrects a Tenglish sentence into English. Pick *English*, *Telugu* or *Both* under Languages (asked once on first launch)
- **Next-word prediction** blending a personal trigram/bigram model with the seeded lexicons (30k English words, ~700 bigrams)
- **Phrase suggestions** — each suggestion grows into several words ("you doing today") whenever your own typing history makes the continuation clear; one tap commits the whole phrase
- **On-device personal language model** stored in a plain SQLite file (`user_model.db`) — every word you type is tagged with its language so your own Tenglish and English vocabulary feeds the layer; inspectable, backup-able, and wipeable from settings
- **8 preset themes** (Light, Dark, Midnight, Ocean, Sunset, Forest, Rose, Lavender) plus a **custom theme** derived from four colours you pick
- **Multi-screen settings app**: Set up · Themes · Layout · Languages · Typing · Key press · Stickers · Learned words · About
- **Learned words screen** — see every word the model has learned with its count, forget words one at a time, or reset everything
- Toggles for autocorrect, prediction, phrase suggestions, glide, number row, auto-capitalisation, double-space period, key popups, vibration and sound
- Smart shift (auto-capitalisation at sentence start), double-space period, `i` → `I`
- No accounts, no Play Services, no background process — works on de-Googled ROMs

## Project layout

```
app/src/main/java/com/caxone/my_keyboard/
├── keyboard/
│   ├── KeyboardService.kt   # InputMethodService: composing, commit, learn, undo, glide, panels
│   ├── KeyboardView.kt      # Draws keys, handles touch, captures glide paths
│   ├── LetterLayout.kt      # QWERTY / QWERTZ / AZERTY / Dvorak / Colemak / Workman rows
│   ├── Layouts.kt           # Builds letter (± number row) and symbol key grids
│   └── SuggestionStrip.kt   # Three-slot suggestion bar
├── prediction/
│   ├── LanguageEngine.kt    # Intelligent layer: per-language lexicons, sentence-language detection, biased lookups
│   ├── Language.kt          # ENGLISH / TELUGU and their asset files
│   ├── Predictor.kt         # Scores completions, corrections and next words
│   ├── PhraseBuilder.kt     # Extends a suggestion into a phrase while the user model is confident
│   ├── GlideDecoder.kt      # Turns a glide path into word candidates
│   ├── Dictionary.kt        # One built-in lexicon (words + seed bigrams) per language
│   ├── UserModel.kt         # Personal unigram/bigram/trigram model backed by SQLite
│   └── EditDistance.kt      # Bounded edit distance; adjacency map rebuilt for the chosen layout
├── media/
│   ├── MediaBar.kt          # Emoji / GIF / Stickers tab bar under the keys
│   ├── EmojiPanel.kt, EmojiData.kt
│   ├── GifPanel.kt          # Placeholder
│   ├── StickerPanel.kt      # Sticker grid + "+" tile
│   ├── StickerStore.kt      # PNG files in private storage, FileProvider URIs
│   ├── StickerRenderer.kt   # Photo (zoom / pan / filter / outline) + colour + emoji + caption → bitmap
│   ├── PhotoEditor.kt       # Editable photo state: cut-out, brushes, rotate / flip, colour matrix
│   ├── BackgroundRemover.kt # MediaPipe ImageSegmenter → soft alpha mask
│   └── StickerSender.kt     # commitContent with share-sheet fallback
├── theme/
│   ├── KeyboardTheme.kt     # Presets + custom theme derivation
│   └── ThemeStore.kt        # Persists the chosen theme
├── settings/Prefs.kt        # SharedPreferences wrapper
└── ui/                      # Home + Setup, Themes, Layout, Languages, Typing, Feedback, Stickers, StickerMaker (+ StickerCanvasView), LearnedWords, About
app/src/main/assets/
├── words.txt                # 30,000 English words with frequencies
├── bigrams.txt              # English seed next-word pairs
├── tenglish_words.txt       # ~770 Tenglish words with frequencies
├── tenglish_bigrams.txt     # ~450 Tenglish seed pairs and phrase starts
└── deeplab_v3.tflite        # Segmentation model for sticker background removal (2.7 MB, stored uncompressed)
```

## Requirements

- Android Studio (Ladybug or newer) with Android SDK 36
- JDK 17+
- Min SDK 24 (Android 7.0), target SDK 36

## Build & run

```bash
# Build a debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Or install straight to a connected device / running emulator
./gradlew installDebug
```

### Enable the keyboard

Installing is not enough — Android must be told it's an input method:

```bash
adb shell ime enable com.caxone.my_keyboard/.keyboard.KeyboardService
adb shell ime set    com.caxone.my_keyboard/.keyboard.KeyboardService
```

Or on the device: **Settings → System → Keyboard → On-screen keyboard → Manage keyboards**, toggle on *my-keyboard*, then pick it from the keyboard switcher in any text field.

The app's launcher activity opens a settings screen where you can choose a theme, toggle typing options, and clear the learned data.

## Running tests

```bash
./gradlew test          # unit tests (EditDistance etc.)
./gradlew connectedAndroidTest
```

## How glide typing works

`KeyboardView` records the finger path once a press drags more than ~22dp onto another letter, and draws a trail in the theme's accent colour. On release it collapses the path to the sequence of letter keys crossed, flagging the first key, the last key and every sharp direction change (>55°) as a *corner*. `GlideDecoder` then scores every dictionary and learned word that starts at the first key (or a neighbour), ends at the last, and whose letters are a subsequence of the path — rewarding frequency and personal use, and penalising each missed corner heavily. The best match becomes composing text with the runners-up in the suggestion strip; a space is inserted automatically before the next word, and backspace removes the whole glided word.

## How the language layer works

`LanguageEngine` sits between the predictor and the data. It loads a `Dictionary` per `Language` and, before every lookup, `setContext(prev2, prev1)` votes on the sentence language: a word only in the Tenglish lexicon (or tagged Telugu in the user model) votes Telugu, a word only in the English lexicon votes English, loanwords in both abstain. The winning language's lexicon is weighted `WEIGHT_MATCH`, the other `WEIGHT_OTHER`; with no clue the mode's languages are weighted evenly (*Both*) or with a slight preference (*Telugu*). Every merged lookup — `frequency`, `completions`, `seedNext`, glide candidates — applies that weight, so the same code path serves both languages. Autocorrect is switched off inside a Telugu sentence (Tenglish spelling is free-form) and limited to near-certain slips when the sentence language is unknown. When a word is learned it is tagged with `guessLanguage()` (its lexicon, else the sentence language) in `user_model.db`, so words the lexicon has never seen still vote correctly next time. Phrase suggestions additionally consult the seed bigrams when one follower has a clear majority (`PHRASE_SEED_DOMINANCE`), e.g. "thank → you", "happy → birthday", "vastanu → ra".

## How prediction works

`Predictor.forComposing()` builds a candidate set from:

1. Completions from the user model and dictionary that start with the typed prefix
2. Corrections — dictionary and user words within an edit-distance bound that passes a cheap first/second-letter prefilter

Each candidate is scored as `base(frequency, personal count) + context(trigram, bigram, seed) − penalties(length, edit distance)`. Autocorrect only fires when the typed word is unknown, the best candidate is strong, and the word doesn't look like a proper noun. All weights are plain constants in `Predictor.kt`, so tuning is a one-line change.

## How sticker background removal works

`BackgroundRemover` runs MediaPipe's `ImageSegmenter` with the bundled DeepLab v3 model and asks for confidence masks. The background class's confidence is inverted and passed through a soft threshold (`EDGE_LOW`/`EDGE_HIGH`) to make an `ALPHA_8` mask with feathered edges; `PhotoEditor.applyMask` multiplies it into the photo. If the model keeps under 1 % of the pixels the result is rejected rather than saving an empty sticker. The erase / restore brushes then edit the cut-out directly (clear pixels, or copy them back from the untouched source), with strokes mapped from the preview through the same matrix the renderer uses. Colour edits are a `ColorMatrix` applied at draw time, so they are always reversible.

MediaPipe's library manifest requests `INTERNET` and `ACCESS_NETWORK_STATE`; the app manifest strips both with `tools:node="remove"`, so the merged APK still declares only `VIBRATE`. The native library adds roughly 10 MB per ABI; use ABI splits or an App Bundle for a slim release build.

## How phrase suggestions work

After the next-word candidates are ranked, `PhraseBuilder` asks the personal model what usually follows each one — first by trigram (the two words before), then by bigram. A follower is appended only when it has been typed at least `PHRASE_MIN_COUNT` times in that context *and* accounts for at least `PHRASE_DOMINANCE` of everything typed there; the walk stops at the first uncertain step, at `PHRASE_MAX_EXTRA` words, or at `PHRASE_MAX_CHARS`. The seed dictionary is deliberately not used, so phrases only ever reflect what you actually write. The strip widens a slot to fit a phrase, and tapping it commits every word (each learned in context). Autocorrect on space still commits a single word.

## License

MIT
