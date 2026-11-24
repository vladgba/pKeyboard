# pKeyboard Voice publication review

Reviewed 2026-11-16. Source commit: unavailable (no readable Git metadata).

- Target SDK 35: below the current phone-app submission minimum of API 36, effective August 31, 2026 (extension ended November 1, 2026). See the official target API reference. Do not change a deliberate runtime target without redesign.
- Owner must confirm developer identity/contact, production support and privacy URLs, countries, pricing, ads, target age and content rating.
- Audit runtime traffic, dependencies and final merged release manifest before signing off Data safety. Source manifest alone is not a complete data audit.
- Create a signed release AAB, test the release on a device and complete the Play Console testing/review requirements applicable to this account. No release binary was built by this documentation task.

## Source evidence

Manifest: `../voice/src/main/AndroidManifest.xml`. Build: `../voice/build.gradle.kts`.

- network: `../voice/src/main/java/x/vladgba/keyboard/voice/Translator.kt`
- storage: No matches in scanned module sources; absence is not proof.
- sharing: No matches in scanned module sources; absence is not proof.
- learning_clipboard: No matches in scanned module sources; absence is not proof.
- speech_translation: `../voice/src/main/java/x/vladgba/keyboard/voice/Translator.kt`, `../voice/src/main/java/x/vladgba/keyboard/voice/VoiceService.kt`
