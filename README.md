# pKeyboard

A customizable, lightweight keyboard for Android. Every key can do something on tap, swipe, hold or circle, layouts and color themes are plain text files you can edit on the phone, and it learns your words without sending anything anywhere.

## Features

- **Gestures on every key.** Swipe in eight directions, hold, press hard or draw a circle; each can type a character, run an action, a key combination or a macro.
- **Your layouts and themes.** Edit layouts and colors in a built-in editor with live preview, or start from the bundled ones. Different layouts per app.
- **Word suggestions** from words you type, with typo correction and optional emoji suggestions. Import your Gboard or AOSP dictionary.
- **Text tools.** Clipboard history with pinned clips, text expansion from abbreviations, dead keys and a compose key for accented and special characters, and a cursor slider on the space bar.
- **Split keyboard** on wide screens, an optional number row and a configurable height.
- **Physical keys.** Remap volume and other hardware buttons while you type.
- **Voice input** through your installed speech service.
- **Backup and restore** of settings, layouts, themes, dictionary and macros.
- **10 languages** for the app itself.
- **No ads, no data collection.**

## Permissions

| Permission | Why | Required |
|---|---|---|
| Microphone | Voice input (🎤) | Optional |
| Vibrate | Haptic feedback on key presses | Optional |
| Internet | Translating voice input when on-device translation is unavailable | Optional |

pKeyboard never learns from password fields or fields that ask not to.

### Notes

- Every setting in `settings.txt`, the layout format and the key options are described in the [guide](docs/GUIDE.md).

## Build

Requires Android 5.0+ (API 21). Built with Kotlin on the plain Android framework, without libraries.

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. `./gradlew :app:check` also runs the layout, dictionary and keyboard-logic checks.

## License

See [LICENSE](LICENSE).
