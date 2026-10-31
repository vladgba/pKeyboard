package x.vladgba.keyboard.core

import x.vladgba.keyboard.flex.FlexNode
import x.vladgba.keyboard.flex.FlexParser
import x.vladgba.keyboard.keyboard.KeyValue

fun checkKeyValues() {
    fun dispatch(value: String, popup: Boolean = true): List<String> {
        val events = mutableListOf<String>()
        KeyValue.dispatch(value,
            onKey = { events += "key:$it" },
            onText = { events += "text:$it" },
            runSpecial = {
                // The Android keyboard handles these through runSpecial, before literal text.
                if (it in setOf("ctrl+c", "shift+-61", "macro:copy", "layout:en", "@DELETE_WORD")) {
                    events += "special:$it"
                    true
                } else false
            },
            singleCharacterAsKey = popup,
        )
        return events
    }

    for (value in listOf("-61", " -61 ", "\t-61\n")) {
        check(dispatch(value) == listOf("key:-61")) { "Popup Tab became literal text: $value" }
        check(dispatch(value, popup = false) == listOf("key:-61"))
        // Popup editor saves the string unchanged; both serializer modes must retain its meaning.
        val key = FlexNode().apply { this["key"] = "a"; this[0] = value }
        for (minify in listOf(false, true)) {
            val parser = FlexParser(key.toStr(0, false, minify)) { }
            val restored = parser.parse()
            check(parser.warnings.isEmpty())
            check(restored.str(0) == value)
            check(dispatch(restored.str(0)) == listOf("key:-61"))
        }
    }
    for (value in listOf("ctrl+c", "shift+-61", "macro:copy", "layout:en", "@DELETE_WORD")) {
        check(dispatch(" $value ") == listOf("special:$value"))
        check(dispatch(value, popup = false) == listOf("special:$value"))
    }
    for (value in listOf("61", "123", "-", "-61x", "-2147483649", " padded ", "\t\n", "e\u0301", "👨‍👩‍👧")) {
        val expected = if (value.codePointCount(0, value.length) == 1) "key:${value.codePointAt(0)}" else "text:$value"
        check(dispatch(value) == listOf(expected)) { "Literal popup changed: $value" }
        check(dispatch(value, popup = false) == listOf("text:$value"))
    }
    for (value in listOf("7", "a", "é", "😀", " ")) {
        check(dispatch(value) == listOf("key:${value.codePointAt(0)}"))
        check(dispatch(value, popup = false) == listOf("text:$value"))
    }
    check(dispatch("").isEmpty())
    check(dispatch("", popup = false).isEmpty())
}
