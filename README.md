# pKeyboard

A customizable, lightweight keyboard for Android. Every key can do something on tap, swipe, hold or circle, layouts and color themes are plain text files you can edit on the phone, and it learns your words without sending anything anywhere.

## Features

- **Gestures on every key.** Swipe in eight directions, hold, press hard or draw a circle; each can type a character, run an action, a key combination or a macro.
- **Your layouts and themes.** Edit layouts and colors in a built-in editor with live preview, or start from the bundled ones. Different layouts per app.
- **Word suggestions** from words you type, with typo correction and optional emoji suggestions. Import your Gboard or AOSP dictionary.
- **Text tools.** Clipboard history with pinned clips, text expansion from abbreviations, dead keys and a compose key for accented and special characters, and a cursor slider on the space bar.
- **Split keyboard** on wide screens, an optional number row and a configurable height.
- **Physical keys.** Remap volume and other hardware buttons while you type.
- **Voice input** with the optional **pKeyboard Voice** add-on (see below).
- **Backup and restore** of settings, layouts, themes, dictionary and macros.
- **10 languages** for the app itself.
- **No ads, no data collection.**

## Permissions

| Permission | Why | Required |
|---|---|---|
| Vibrate | Haptic feedback on key presses | Optional |

pKeyboard has **no microphone and no internet permission**. It never learns from password fields or fields that ask not to.

## Voice input

Voice typing (🎤 and `@tr_voice` translation) lives in a separate, optional add-on, **pKeyboard Voice** (`x.vladgba.keyboard.voice`), so the keyboard itself can stay offline and never touch the microphone. Install it next to pKeyboard; without it, the 🎤 key switches to another voice keyboard if you have one.

| Add-on permission | Why |
|---|---|
| Microphone | Listening while the voice overlay is shown |
| Internet | Translating voice input when on-device translation is unavailable |

The add-on has no screen of its own. Only apps signed with the same key as pKeyboard can use it, and the first time you tap 🎤 it asks for the microphone. Get it from the same place as pKeyboard: the `pKeyboardVoice-*.apk` next to `pKeyboard-*.apk` in the [Build artifacts](https://github.com/vladgba/pKeyboard/actions/workflows/build.yml?query=branch%3Amain) or releases.

### Notes

- Every setting in `settings.txt`, the layout format and the key options are described in the [guide](docs/GUIDE.md).

## Build

Requires Android 5.0+ (API 21). Built with Kotlin on the plain Android framework, without libraries.

```bash
./gradlew assembleRelease
```

The keyboard APK is written to `app/build/outputs/apk/release/`, the add-on to `voice/build/outputs/apk/release/`. Both must be signed with the same key. `./gradlew :app:check` also runs the layout, dictionary and keyboard-logic checks.

## License

See [LICENSE](LICENSE).
