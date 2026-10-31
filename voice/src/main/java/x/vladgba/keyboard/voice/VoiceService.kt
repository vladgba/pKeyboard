package x.vladgba.keyboard.voice

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import x.vladgba.keyboard.voiceapi.VoiceProtocol as P

/**
 * Speech recognition (and optional translation) for pKeyboard, as a bound service.
 *
 * pKeyboard binds with BIND_INCLUDE_CAPABILITIES, so while the keyboard is on screen this process
 * may use the microphone. Binding requires the signature permission declared in the manifest.
 * One session at a time; every event carries the client's session id so stale events are ignored.
 * Everything runs on the main thread, as SpeechRecognizer requires.
 */
class VoiceService : Service() {
    private val main = Handler(Looper.getMainLooper(), ::handle)
    private val messenger = Messenger(main)

    private var recognizer: SpeechRecognizer? = null
    private var client: Messenger? = null
    private var session = 0
    private var translateFrom: String? = null
    private var translateTo: String? = null
    /** Bumped on every new session/cancel: a translation finishing late is dropped. */
    private var generation = 0

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        cancel()
        return false
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    private fun handle(msg: Message): Boolean {
        when (msg.what) {
            P.MSG_START -> start(msg.arg1, msg.replyTo, msg.data)
            P.MSG_STOP -> if (msg.arg1 == session) recognizer?.stopListening()
            P.MSG_CANCEL -> if (msg.arg1 == session) cancel()
            else -> return false
        }
        return true
    }

    private fun start(id: Int, replyTo: Messenger?, data: Bundle) {
        cancel()
        session = id
        client = replyTo
        generation++
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            send(P.EV_NEED_PERMISSION)
            return
        }
        translateFrom = data.getString(P.KEY_TRANSLATE_FROM)
        translateTo = data.getString(P.KEY_TRANSLATE_TO)
        val offline = data.getBoolean(P.KEY_OFFLINE)
        val rec = createRecognizer(data.getString(P.KEY_PROVIDER), offline)
        if (rec == null) {
            send(P.EV_ERROR, P.ERR_UNAVAILABLE)
            return
        }
        recognizer = rec
        rec.setRecognitionListener(listener)
        val lang = data.getString(P.KEY_LANGUAGE).orEmpty()
        try {
            rec.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                if (lang.isNotEmpty()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                }
                if (offline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (e: Exception) {
            release()
            send(P.EV_ERROR, P.ERR_GENERIC)
        }
    }

    private fun createRecognizer(provider: String?, offline: Boolean): SpeechRecognizer? {
        provider?.let(ComponentName::unflattenFromString)?.let { component ->
            try { return SpeechRecognizer.createSpeechRecognizer(this, component) } catch (_: Exception) { }
        }
        if (offline && Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            return try { SpeechRecognizer.createOnDeviceSpeechRecognizer(this) } catch (_: Exception) { null }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return null
        return try { SpeechRecognizer.createSpeechRecognizer(this) } catch (_: Exception) { null }
    }

    private fun cancel() {
        generation++
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun send(what: Int, arg2: Int = 0, data: Bundle? = null) {
        val target = client ?: return
        val msg = Message.obtain(null, what, session, arg2)
        if (data != null) msg.data = data
        try { target.send(msg) } catch (_: RemoteException) { client = null; cancel() }
    }

    private fun text(s: String) = Bundle().apply { putString(P.KEY_TEXT, s) }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = send(P.EV_READY)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            // Typical range is about -2..10 dB.
            send(P.EV_LEVEL, data = Bundle().apply { putFloat(P.KEY_LEVEL, ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) })
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = send(P.EV_END_OF_SPEECH)
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            val code = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> P.ERR_NOTHING
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> P.ERR_NETWORK
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> P.ERR_PERMISSION
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> P.ERR_BUSY
                SpeechRecognizer.ERROR_CLIENT -> P.ERR_SILENT // our own cancel/stop
                else -> if (Build.VERSION.SDK_INT >= 31 && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) P.ERR_LANGUAGE else P.ERR_GENERIC
            }
            release()
            send(P.EV_ERROR, code)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val s = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (s.isNotEmpty()) send(P.EV_PARTIAL, data = text(s))
        }

        override fun onResults(results: Bundle?) {
            val s = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            release()
            val to = translateTo
            if (s.isBlank() || to.isNullOrBlank()) {
                send(P.EV_RESULT, data = text(s))
                return
            }
            send(P.EV_TRANSLATING)
            val gen = generation
            Translator.translate(this@VoiceService, s, translateFrom.orEmpty(), to, onSuccess = { translated ->
                if (gen == generation) send(P.EV_RESULT, data = text(translated))
            }, onError = {
                if (gen == generation) send(P.EV_RESULT, data = text(s).apply { putBoolean(P.KEY_TRANSLATE_FAILED, true) })
            })
        }
    }
}
