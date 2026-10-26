package x.vladgba.keyboard.keyboard

/** Shared dispatch for string actions (popup slots, hold, heavy press and modifier maps). */
internal object KeyValue {
    fun dispatch(
        value: String,
        onKey: (Int) -> Unit,
        onText: (String) -> Unit,
        runSpecial: (String) -> Boolean,
        singleCharacterAsKey: Boolean = false,
    ) {
        if (value.isEmpty()) return
        val action = value.trim()
        val code = action.toIntOrNull()?.takeIf { it < 0 }
        when {
            code != null -> onKey(code)
            runSpecial(action) -> Unit
            singleCharacterAsKey && value.codePointCount(0, value.length) == 1 -> onKey(value.codePointAt(0))
            else -> onText(value)
        }
    }
}
