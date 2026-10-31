package x.vladgba.keyboard.keyboard

/** Shared dispatch for string actions (popup slots, hold, heavy press and modifier maps). */
internal object KeyValue {
    /** Minus look-alikes that phone keyboards and autocorrect type instead of '-'. */
    private const val MINUS_SIGNS = "−‒–—﹣－"

    /** Replaces a leading Unicode minus look-alike with ASCII '-', so "−61" still means "-61". */
    fun normalizeSign(value: String): String {
        val s = value.trim()
        return if (s.length > 1 && s[0] in MINUS_SIGNS) "-" + s.substring(1) else s
    }

    /** `"-61"` (also `" −61 "`) -> -61; null when [value] isn't a negative Android key code. */
    fun keyCode(value: String): Int? =
        normalizeSign(value).toIntOrNull()?.takeIf { it < 0 && it != Int.MIN_VALUE }

    fun dispatch(
        value: String,
        onKey: (Int) -> Unit,
        onText: (String) -> Unit,
        runSpecial: (String) -> Boolean,
        singleCharacterAsKey: Boolean = false,
    ) {
        if (value.isEmpty()) return
        val action = value.trim()
        val code = keyCode(action)
        when {
            code != null -> onKey(code)
            runSpecial(action) -> Unit
            singleCharacterAsKey && value.codePointCount(0, value.length) == 1 -> onKey(value.codePointAt(0))
            else -> onText(value)
        }
    }
}
