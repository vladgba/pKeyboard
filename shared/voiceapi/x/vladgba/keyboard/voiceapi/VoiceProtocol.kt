package x.vladgba.keyboard.voiceapi

import android.content.ComponentName

/**
 * Messenger protocol between pKeyboard (client) and the pKeyboard Voice add-on (service).
 * Compiled into both APKs from shared/voiceapi, so the two sides can't drift apart.
 *
 * The add-on holds RECORD_AUDIO and INTERNET; the keyboard itself has neither. Binding is
 * restricted by a signature permission ("<voice package>.permission.VOICE") that only apps
 * signed with the same key get.
 */
object VoiceProtocol {
    const val BASE_PACKAGE = "x.vladgba.keyboard"
    const val VOICE_BASE_PACKAGE = "x.vladgba.keyboard.voice"
    const val SERVICE_CLASS = "x.vladgba.keyboard.voice.VoiceService"
    const val PERMISSION_ACTIVITY_CLASS = "x.vladgba.keyboard.voice.MicPermissionActivity"

    /** Add-on package matching the keyboard's build ("x.vladgba.keyboard.debug" -> "...voice.debug"). */
    fun voicePackage(keyboardPackage: String) = VOICE_BASE_PACKAGE + keyboardPackage.removePrefix(BASE_PACKAGE)

    fun service(keyboardPackage: String) = ComponentName(voicePackage(keyboardPackage), SERVICE_CLASS)
    fun permissionActivity(keyboardPackage: String) = ComponentName(voicePackage(keyboardPackage), PERMISSION_ACTIVITY_CLASS)

    // ---- client -> service (Message.what); Message.arg1 = session id; replyTo = client Messenger
    const val MSG_START = 1
    const val MSG_STOP = 2
    const val MSG_CANCEL = 3

    // MSG_START data keys
    const val KEY_LANGUAGE = "language"          // BCP 47 tag
    const val KEY_OFFLINE = "offline"            // prefer on-device recognition
    const val KEY_PROVIDER = "provider"          // flattened ComponentName of a RecognitionService, or absent
    const val KEY_TRANSLATE_FROM = "tr_from"     // present = translate the result
    const val KEY_TRANSLATE_TO = "tr_to"

    // ---- service -> client (Message.what); Message.arg1 = session id
    const val EV_READY = 10
    const val EV_LEVEL = 11        // data KEY_LEVEL: Float 0..1
    const val EV_PARTIAL = 12      // data KEY_TEXT
    const val EV_END_OF_SPEECH = 13
    const val EV_TRANSLATING = 14
    const val EV_RESULT = 15       // data KEY_TEXT, KEY_TRANSLATE_FAILED
    const val EV_ERROR = 16        // arg2 = ERR_*
    const val EV_NEED_PERMISSION = 17

    const val KEY_LEVEL = "level"
    const val KEY_TEXT = "text"
    const val KEY_TRANSLATE_FAILED = "tr_failed"

    // EV_ERROR codes
    const val ERR_SILENT = 0          // cancelled by the client: no message
    const val ERR_NOTHING = 1
    const val ERR_NETWORK = 2
    const val ERR_PERMISSION = 3
    const val ERR_BUSY = 4
    const val ERR_LANGUAGE = 5
    const val ERR_UNAVAILABLE = 6     // no speech recognizer on the device
    const val ERR_GENERIC = 7
}
