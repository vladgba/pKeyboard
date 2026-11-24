# pKeyboard publication review

Reviewed 2026-11-16. Source commit: unavailable (no readable Git metadata).

- Target SDK 35: below the current phone-app submission minimum of API 36, effective August 31, 2026 (extension ended November 1, 2026). See the official target API reference. Do not change a deliberate runtime target without redesign.
- Owner must confirm developer identity/contact, production support and privacy URLs, countries, pricing, ads, target age and content rating.
- Audit runtime traffic, dependencies and final merged release manifest before signing off Data safety. Source manifest alone is not a complete data audit.
- Create a signed release AAB, test the release on a device and complete the Play Console testing/review requirements applicable to this account. No release binary was built by this documentation task.

## Source evidence

Manifest: `app/src/main/AndroidManifest.xml`. Build: `app/build.gradle.kts`.

- network: `app/src/main/java/x/vladgba/keyboard/core/AospDictionary.kt`, `app/src/main/java/x/vladgba/keyboard/keyboard/VoiceInput.kt`, `app/src/main/java/x/vladgba/keyboard/pui/PAbout.kt`
- storage: `app/src/main/java/x/vladgba/keyboard/core/AppLocale.kt`, `app/src/main/java/x/vladgba/keyboard/core/DirectBoot.kt`, `app/src/main/java/x/vladgba/keyboard/core/PFile.kt`, `app/src/main/java/x/vladgba/keyboard/ui/RawEditor.kt`
- sharing: `app/src/main/java/x/vladgba/keyboard/ui/BackupActivity.kt`, `app/src/main/java/x/vladgba/keyboard/ui/FieldTestActivity.kt`, `app/src/main/java/x/vladgba/keyboard/ui/FileExport.kt`, `app/src/main/java/x/vladgba/keyboard/ui/WordsActivity.kt`
- learning_clipboard: `app/src/main/java/x/vladgba/keyboard/KeybWrapper.kt`, `app/src/main/java/x/vladgba/keyboard/core/ClipHistory.kt`, `app/src/main/java/x/vladgba/keyboard/core/UserWords.kt`, `app/src/main/java/x/vladgba/keyboard/core/WordIndex.kt`, `app/src/main/java/x/vladgba/keyboard/keyboard/ClipDrawer.kt`, `app/src/main/java/x/vladgba/keyboard/keyboard/Keyboard.kt`, `app/src/main/java/x/vladgba/keyboard/ui/WordsActivity.kt`
- speech_translation: No matches in scanned module sources; absence is not proof.
