package x.vladgba.keyboard.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import x.vladgba.keyboard.R
import x.vladgba.keyboard.core.*
import x.vladgba.keyboard.voiceapi.VoiceProtocol as P
import java.util.Locale

data class TranslationSpec(val sourceLang: String, val targetLang: String)

/**
 * Voice typing through the optional **pKeyboard Voice** add-on (separate APK, see shared/voiceapi).
 * The add-on owns the microphone and internet permissions, runs the system speech recognizer and
 * the translation fallback; the keyboard itself has neither permission.
 *
 * - Partial results are shown as composing (underlined) text and in the overlay, the final result
 *   is committed with a space before it when needed and a capital where the field asks for one.
 * - The language follows the current layout ("voiceLang" in a layout overrides it).
 * - Without the add-on (or without a recognizer) it switches to a voice keyboard (input method with
 *   a "voice" subtype) if one is installed.
 */
class VoiceInput(private val kb: Keyboard) {
    enum class State { IDLE, STARTING, LISTENING, PROCESSING }

    var state = State.IDLE
        private set
    /** Text recognized so far (shown in the overlay). */
    var partial = ""
        private set
    /** Microphone level 0..1 for the overlay animation. */
    var level = 0f
        private set

    private val ctx get() = kb.ctx

    var translationSpec: TranslationSpec? = null
        private set
    val isTranslating get() = translationSpec != null

    val active get() = state != State.IDLE

    private var session = 0
    private var service: Messenger? = null
    private var bound = false
    private var pendingStart: Bundle? = null
    private val events = Messenger(Handler(Looper.getMainLooper(), ::onEvent))

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = Messenger(binder)
            pendingStart?.let { sendStart(it) }
            pendingStart = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            if (active) {
                clearComposing()
                finish()
                toast(R.string.vo_err_generic)
            }
        }
    }

    fun toggle(spec: TranslationSpec? = null) = if (active) stop() else start(spec)
    fun toggle(specStr: String?) = toggle(specStr?.let { parseTrVoiceSpec(it) })

    fun start(spec: TranslationSpec? = null) {
        if (kb.isEditor || active) return
        val voicePkg = P.voicePackage(ctx.packageName)
        if (!isInstalled(voicePkg)) {
            if (!switchToVoiceKeyboard()) {
                toast(R.string.vo_err_no_addon)
                openUrl(ADDON_URL)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 23 &&
            ctx.packageManager.checkPermission(android.Manifest.permission.RECORD_AUDIO, voicePkg) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophone()
            return
        }
        translationSpec = spec
        partial = ""
        level = 0f
        state = State.STARTING
        spaceBefore = null
        session++
        // Started from a long-press: that finger's "up" goes to the overlay, so release keys now.
        kb.resetTouches()
        val args = Bundle().apply {
            putString(P.KEY_LANGUAGE, language())
            putBoolean(P.KEY_OFFLINE, Settings.bool(SETTING_VOICE_OFFLINE))
            if (Settings.bool(SETTING_VOICE_PROVIDER_ENABLED, false)) {
                SpeechProviders.resolve(true, Settings.str(SETTING_VOICE_PROVIDER), SpeechProviders.available(ctx))
                    ?.let { putString(P.KEY_PROVIDER, it.flattenToString()) }
            }
            spec?.let {
                val src = if (it.sourceLang.isNotBlank() && it.sourceLang != "auto") it.sourceLang
                          else language().substringBefore("-").lowercase(Locale.ROOT)
                putString(P.KEY_TRANSLATE_FROM, src)
                putString(P.KEY_TRANSLATE_TO, it.targetLang.substringBefore("-").lowercase(Locale.ROOT))
            }
        }
        if (service != null) sendStart(args) else {
            pendingStart = args
            if (!bind()) {
                pendingStart = null
                finish()
                toast(R.string.vo_err_no_addon)
                return
            }
        }
        kb.invalidate()
    }

    /** Finish listening; the final result still arrives. */
    fun stop() {
        when (state) {
            State.STARTING, State.LISTENING -> {
                state = State.PROCESSING
                send(P.MSG_STOP)
                kb.invalidate()
            }
            State.PROCESSING -> cancel() // second tap while waiting: give up
            State.IDLE -> {}
        }
    }

    /** Abort without inserting anything (keyboard hidden, field changed). */
    fun cancel() {
        if (!active) return
        send(P.MSG_CANCEL)
        pendingStart = null
        clearComposing()
        finish()
    }

    fun release() {
        cancel()
        if (bound) {
            try { ctx.unbindService(connection) } catch (_: Exception) { }
            bound = false
            service = null
        }
    }

    private fun finish() {
        state = State.IDLE
        partial = ""
        level = 0f
        translationSpec = null
        session++ // late events of the finished session are ignored
        kb.invalidate()
    }

    private fun bind(): Boolean {
        if (bound) return true
        var flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        // Lets the add-on use the microphone while the keyboard is on screen (Android 11+).
        if (Build.VERSION.SDK_INT >= 30) flags = flags or Context.BIND_INCLUDE_CAPABILITIES
        bound = try {
            ctx.bindService(Intent().setComponent(P.service(ctx.packageName)), connection, flags)
        } catch (_: Exception) { false }
        return bound
    }

    private fun sendStart(args: Bundle) {
        val msg = Message.obtain(null, P.MSG_START, session, 0)
        msg.data = args
        msg.replyTo = events
        try { service?.send(msg) } catch (e: Exception) { kb.prStack(e); finish() }
    }

    private fun send(what: Int) {
        try { service?.send(Message.obtain(null, what, session, 0)) } catch (_: Exception) { }
    }

    private fun onEvent(msg: Message): Boolean {
        if (msg.arg1 != session || !active) return true
        when (msg.what) {
            P.EV_READY -> { state = State.LISTENING; kb.invalidate() }
            P.EV_LEVEL -> { level = msg.data.getFloat(P.KEY_LEVEL); kb.invalidate() }
            P.EV_END_OF_SPEECH, P.EV_TRANSLATING -> {
                if (state == State.LISTENING || state == State.STARTING) state = State.PROCESSING
                kb.invalidate()
            }
            P.EV_PARTIAL -> {
                val text = msg.data.getString(P.KEY_TEXT).orEmpty()
                if (text.isNotEmpty()) {
                    partial = text
                    kb.inputConnection?.setComposingText(prepare(text), 1)
                    kb.invalidate()
                }
            }
            P.EV_RESULT -> {
                commit(msg.data.getString(P.KEY_TEXT).orEmpty())
                val failed = msg.data.getBoolean(P.KEY_TRANSLATE_FAILED)
                finish()
                if (failed) toast(R.string.vo_err_translate)
            }
            P.EV_NEED_PERMISSION -> {
                clearComposing()
                finish()
                requestMicrophone()
            }
            P.EV_ERROR -> {
                clearComposing()
                finish()
                val res = when (msg.arg2) {
                    P.ERR_SILENT -> 0
                    P.ERR_NOTHING -> R.string.vo_err_nothing
                    P.ERR_NETWORK -> R.string.vo_err_network
                    P.ERR_PERMISSION -> R.string.vo_err_permission
                    P.ERR_BUSY -> R.string.vo_err_busy
                    P.ERR_LANGUAGE -> R.string.vo_err_language
                    P.ERR_UNAVAILABLE -> if (switchToVoiceKeyboard()) 0 else R.string.vo_err_unavailable
                    else -> R.string.vo_err_generic
                }
                if (res != 0) toast(res)
            }
        }
        return true
    }

    /** An input method can't show the permission dialog: the add-on's own screen asks for it. */
    private fun requestMicrophone() {
        try {
            ctx.startActivity(Intent().setComponent(P.permissionActivity(ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            kb.prStack(e)
            toast(R.string.vo_err_permission)
        }
    }

    private fun isInstalled(pkg: String) = try {
        @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun openUrl(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }

    /** BCP 47 tag: translation source > setting > layout's "voiceLang" > known layout > system language. */
    fun language(): String {
        translationSpec?.let { spec ->
            if (spec.sourceLang.isNotBlank() && spec.sourceLang != VOICE_LANG_AUTO && spec.sourceLang != "auto") {
                LAYOUT_LANGS[spec.sourceLang]?.let { return it }
                if (spec.sourceLang.contains("-")) return spec.sourceLang
                return "${spec.sourceLang}-${spec.sourceLang.uppercase(Locale.ROOT)}"
            }
        }
        Settings.str(SETTING_VOICE_LANG).trim().takeIf { it.isNotEmpty() && it != VOICE_LANG_AUTO }?.let { return it }
        kb.currentLayout.str(KEY_VOICE_LANG).trim().takeIf { it.isNotEmpty() }?.let { return it }
        LAYOUT_LANGS[kb.currentLayoutName]?.let { return it }
        // For the number/emoji layouts use the last typing layout's language.
        LAYOUT_LANGS[kb.lastTypingLayout]?.let { return it }
        return Locale.getDefault().toLanguageTag()
    }

    private fun clearComposing() {
        kb.inputConnection?.let {
            it.setComposingText("", 1)
            it.finishComposingText()
        }
    }

    /** Space before the text unless at the start or after a space; capital if the field wants one. */
    private fun prepare(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return text
        val ic = kb.inputConnection ?: return text
        if (kb.autoShift) text = text.substring(0, 1).uppercase(Locale.getDefault()) + text.substring(1)
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val composing = partial.isNotEmpty() && state != State.IDLE
        // While composing, the character before the cursor is our own composing text: decide once.
        if (!composing || spaceBefore == null) spaceBefore = before.isNotEmpty() && !before[0].isWhitespace()
        return if (spaceBefore == true) " $text" else text
    }

    private var spaceBefore: Boolean? = null

    private fun commit(raw: String) {
        val ic = kb.inputConnection ?: return
        if (raw.isBlank()) {
            clearComposing()
            return
        }
        ic.beginBatchEdit()
        ic.setComposingText(prepare(raw), 1)
        ic.finishComposingText()
        ic.endBatchEdit()
        spaceBefore = null
        kb.onSelectionChanged()
    }

    /** Fallback: hand over to a voice input method (e.g. a system voice keyboard). */
    private fun switchToVoiceKeyboard(): Boolean {
        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        for (imi in imm.enabledInputMethodList) {
            if (imi.packageName == ctx.packageName) continue
            for (i in 0 until imi.subtypeCount) {
                val subtype = imi.getSubtypeAt(i)
                if (subtype.mode == "voice") {
                    val ime = kb.ime ?: return false
                    if (Build.VERSION.SDK_INT >= 28) ime.switchInputMethod(imi.id, subtype)
                    else {
                        val token = ime.window?.window?.attributes?.token ?: return false
                        @Suppress("DEPRECATION") imm.setInputMethodAndSubtype(token, imi.id, subtype)
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun toast(res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()

    companion object {
        /** Where to get the pKeyboard Voice add-on. */
        const val ADDON_URL = "https://github.com/vladgba/pKeyboard#voice-input"

        /**
         * Parses voice translation specifier like `@tr_voice/ru-en`, `tr_voice:ru-en`,
         * `@tr_voice/en`, `@tr_voice`, etc.
         */
        fun parseTrVoiceSpec(value: String): TranslationSpec? {
            val clean = value.trim()
            val raw = when {
                clean.startsWith("@tr_voice/") -> clean.removePrefix("@tr_voice/")
                clean.startsWith("@tr_voice:") -> clean.removePrefix("@tr_voice:")
                clean.startsWith("@tr_voice") -> clean.removePrefix("@tr_voice").removePrefix("/").removePrefix(":")
                clean.startsWith("tr_voice:") -> clean.removePrefix("tr_voice:")
                clean.startsWith("tr_voice/") -> clean.removePrefix("tr_voice/")
                clean.startsWith("tr_voice") -> clean.removePrefix("tr_voice").removePrefix("/").removePrefix(":")
                else -> return null
            }.trim()

            if (raw.isEmpty()) return TranslationSpec("auto", "en")

            val parts = when {
                raw.contains("->") -> raw.split("->", limit = 2)
                raw.contains(">") -> raw.split(">", limit = 2)
                raw.contains("/") -> raw.split("/", limit = 2)
                raw.contains(":") -> raw.split(":", limit = 2)
                raw.contains("-") -> {
                    val p = raw.split("-")
                    if (p.size == 4) listOf("${p[0]}-${p[1]}", "${p[2]}-${p[3]}")
                    else if (p.size >= 2) listOf(p[0], p[1])
                    else listOf(raw)
                }
                raw.contains("_") -> raw.split("_", limit = 2)
                else -> listOf(raw)
            }.map { it.trim().lowercase(Locale.ROOT) }

            return if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                TranslationSpec(parts[0], parts[1])
            } else if (parts.size == 1 && parts[0].isNotEmpty()) {
                TranslationSpec("auto", parts[0])
            } else {
                TranslationSpec("auto", "en")
            }
        }

        /** Speech language for the bundled layouts. */
        val LAYOUT_LANGS = mapOf(
            "en" to "en-US", "enExt" to "en-US", "dvorak" to "en-US", "colemak" to "en-US", "code" to "en-US",
            "uk" to "uk-UA", "ru" to "ru-RU", "ruExt" to "ru-RU", "be" to "be-BY", "bg" to "bg-BG",
            "de" to "de-DE", "fr" to "fr-FR", "es" to "es-ES", "pl" to "pl-PL", "ro" to "ro-RO",
            "ar" to "ar-SA", "he" to "he-IL", "el" to "el-GR", "tr" to "tr-TR", "it" to "it-IT", "pt" to "pt-PT",
            "cs" to "cs-CZ", "hu" to "hu-HU", "sv" to "sv-SE", "fi" to "fi-FI", "nb" to "nb-NO", "da" to "da-DK",
        )
    }
}
