# my-keyboard

A small, fully offline Android keyboard (IME) with on-device learning, autocorrect, next-word prediction, and custom themes.

It requests exactly one permission — `VIBRATE`. There is no `INTERNET` permission, so nothing you type can ever leave the device.

## Features

- **QWERTY, symbols and secondary-symbols layouts** with long-press hints and key preview popups
- **Autocorrect** using a damerau-style edit distance with an adaptive threshold by word length; skips words that look like names and anything already in your personal vocabulary
- **Backspace undoes an autocorrect** and restores exactly what you typed
- **Next-word prediction** blending a personal trigram/bigram model with a seeded dictionary (30k words, ~700 bigrams)
- **On-device personal language model** stored in a plain SQLite file (`user_model.db`) — inspectable, backup-able, and wipeable from settings
- **8 preset themes** (Light, Dark, Midnight, Ocean, Sunset, Forest, Rose, Lavender) plus a **custom theme** derived from four colours you pick
- Toggles for autocorrect, prediction, key popups, vibration and sound
- Smart shift (auto-capitalisation at sentence start), double-space period, `i` → `I`
- No accounts, no Play Services, no background process — works on de-Googled ROMs

## Project layout

```
app/src/main/java/com/caxone/my_keyboard/
├── keyboard/
│   ├── KeyboardService.kt   # InputMethodService: composing, commit, learn, undo
│   ├── KeyboardView.kt      # Custom View that draws and handles touch for a Layout
│   ├── Layouts.kt           # QWERTY / symbols key definitions
│   └── SuggestionStrip.kt   # Three-slot suggestion bar
├── prediction/
│   ├── Predictor.kt         # Scores completions, corrections and next words
│   ├── Dictionary.kt        # Loads words.txt / bigrams.txt from assets
│   ├── UserModel.kt         # Personal unigram/bigram/trigram model backed by SQLite
│   └── EditDistance.kt      # Bounded edit distance with keyboard-adjacency awareness
├── theme/
│   ├── KeyboardTheme.kt     # Presets + custom theme derivation
│   └── ThemeStore.kt        # Persists the chosen theme
├── settings/Prefs.kt        # SharedPreferences wrapper
└── ui/                      # Settings activity, theme picker
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

## How prediction works

`Predictor.forComposing()` builds a candidate set from:

1. Completions from the user model and dictionary that start with the typed prefix
2. Corrections — dictionary and user words within an edit-distance bound that passes a cheap first/second-letter prefilter

Each candidate is scored as `base(frequency, personal count) + context(trigram, bigram, seed) − penalties(length, edit distance)`. Autocorrect only fires when the typed word is unknown, the best candidate is strong, and the word doesn't look like a proper noun. All weights are plain constants in `Predictor.kt`, so tuning is a one-line change.

## License

MIT
