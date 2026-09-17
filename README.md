# my-keyboard

A small, fully offline Android keyboard (IME) with on-device learning, autocorrect, next-word prediction, and custom themes.

It requests exactly one permission — `VIBRATE`. There is no `INTERNET` permission, so nothing you type can ever leave the device.

## Features

- **QWERTY, symbols and secondary-symbols layouts** with long-press hints and key preview popups
- **Optional number row** above the letters; symbol pages carry long-press alternates (superscripts, currency, quotes, brackets)
- **Glide typing** — slide across letters to spell a word; decoded on-device from the path's corners against the dictionary and your own vocabulary
- **Media bar** under the keys with **Emoji** (built-in categorised set with recents), **GIF** (placeholder — no network by design) and **Stickers** tabs
- **Custom stickers** made right from the keyboard (photo, emoji and/or caption), stored locally, and sent inline to any app that accepts images via `commitContent`, with a share-sheet fallback
- **Autocorrect** using a damerau-style edit distance with an adaptive threshold by word length; skips words that look like names and anything already in your personal vocabulary
- **Backspace undoes an autocorrect** and restores exactly what you typed
- **Next-word prediction** blending a personal trigram/bigram model with a seeded dictionary (30k words, ~700 bigrams)
- **On-device personal language model** stored in a plain SQLite file (`user_model.db`) — inspectable, backup-able, and wipeable from settings
- **8 preset themes** (Light, Dark, Midnight, Ocean, Sunset, Forest, Rose, Lavender) plus a **custom theme** derived from four colours you pick
- **Multi-screen settings app**: Set up · Themes · Typing · Key press · Stickers · Learned words · About
- **Learned words screen** — see every word the model has learned with its count, forget words one at a time, or reset everything
- Toggles for autocorrect, prediction, glide, number row, auto-capitalisation, double-space period, key popups, vibration and sound
- Smart shift (auto-capitalisation at sentence start), double-space period, `i` → `I`
- No accounts, no Play Services, no background process — works on de-Googled ROMs

## Project layout

```
app/src/main/java/com/caxone/my_keyboard/
├── keyboard/
│   ├── KeyboardService.kt   # InputMethodService: composing, commit, learn, undo, glide, panels
│   ├── KeyboardView.kt      # Draws keys, handles touch, captures glide paths
│   ├── Layouts.kt           # QWERTY (± number row) / symbols key definitions
│   └── SuggestionStrip.kt   # Three-slot suggestion bar
├── prediction/
│   ├── Predictor.kt         # Scores completions, corrections and next words
│   ├── GlideDecoder.kt      # Turns a glide path into word candidates
│   ├── Dictionary.kt        # Loads words.txt / bigrams.txt from assets
│   ├── UserModel.kt         # Personal unigram/bigram/trigram model backed by SQLite
│   └── EditDistance.kt      # Bounded edit distance with keyboard-adjacency awareness
├── media/
│   ├── MediaBar.kt          # Emoji / GIF / Stickers tab bar under the keys
│   ├── EmojiPanel.kt, EmojiData.kt
│   ├── GifPanel.kt          # Placeholder
│   ├── StickerPanel.kt      # Sticker grid + "+" tile
│   ├── StickerStore.kt      # PNG files in private storage, FileProvider URIs
│   ├── StickerRenderer.kt   # Photo / colour + emoji + outlined caption → bitmap
│   └── StickerSender.kt     # commitContent with share-sheet fallback
├── theme/
│   ├── KeyboardTheme.kt     # Presets + custom theme derivation
│   └── ThemeStore.kt        # Persists the chosen theme
├── settings/Prefs.kt        # SharedPreferences wrapper
└── ui/                      # Home + Setup, Themes, Typing, Feedback, Stickers, StickerMaker, LearnedWords, About
app/src/main/assets/
├── words.txt                # 30,000 words with frequencies
└── bigrams.txt              # Seed next-word pairs
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

## How prediction works

`Predictor.forComposing()` builds a candidate set from:

1. Completions from the user model and dictionary that start with the typed prefix
2. Corrections — dictionary and user words within an edit-distance bound that passes a cheap first/second-letter prefilter

Each candidate is scored as `base(frequency, personal count) + context(trigram, bigram, seed) − penalties(length, edit distance)`. Autocorrect only fires when the typed word is unknown, the best candidate is strong, and the word doesn't look like a proper noun. All weights are plain constants in `Predictor.kt`, so tuning is a one-line change.

## License

MIT
