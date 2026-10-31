package x.vladgba.keyboard.core

import x.vladgba.keyboard.flex.FlexNode
import x.vladgba.keyboard.flex.FlexParser
import x.vladgba.keyboard.keyboard.PopupAction

fun checkPopupActions() {
    fun node(source: String) = PopupAction.parseEditor(source)
    fun events(slot: Any): List<String> {
        val output = mutableListOf<String>()
        PopupAction.from(slot)!!.dispatch(
            onKey = { output += "key:$it" }, onText = { output += "text:$it" },
            runSpecial = { if ((it.startsWith('@') && !it.startsWith("@string/")) || it.startsWith("macro:") || it.startsWith("layout:")) { output += "action:$it"; true } else false },
            sendCombo = { output += "combo:$it"; true }, resolveLabel = { if (it == "@string/test") "é" else it },
        )
        return output
    }
    val cases = listOf(
        "(key: Tab, code: -61, meta: 1)" to listOf("key:-61"),
        "(code: -0x3d)" to listOf("key:-61"),
        "(code: -0X3_d)" to listOf("key:-61"),
        "(code: 0x1f600)" to listOf("key:128512"),
        "(text: \"-61\")" to listOf("text:-61"),
        "(text: \"@VOICE\")" to listOf("text:@VOICE"),
        "(text: \"ctrl+c\")" to listOf("text:ctrl+c"),
        "(text: \" (x) \")" to listOf("text: (x) "),
        "(text: \"\")" to listOf("text:"),
        "(text: \"é😀\")" to listOf("text:é😀"),
        "(text: abc, textCurOffset: -2)" to listOf("text:abc", "key:-21", "key:-21"),
        "(text: abc, textCurOffset: 1)" to listOf("text:abc", "key:-21", "key:-21"),
        "(key: A, code: -61, text: literal)" to listOf("text:literal"),
        "(combo: \"ctrl+-61\", text: ignored)" to listOf("combo:ctrl+-61"),
        "(macro: copy, combo: \"ctrl+-61\")" to listOf("action:macro:copy"),
        "(layout: en, text: ignored)" to listOf("action:layout:en"),
        "(key: \"@VOICE\")" to listOf("action:@VOICE"),
        "(key: \"@string/test\")" to listOf("key:233"),
        "(key: abc)" to listOf("key:97"),
        "(key: \"-61\")" to listOf("key:-61"),
        "(key: \"\u221261\")" to listOf("key:-61"),
        "(code: \"\u221261\")" to listOf("key:-61"),
        "(key: \"-\")" to listOf("key:45"),
    )
    for ((source, expected) in cases) {
        val slot = node(source)
        check(events(slot) == expected) { source }
        check(events(PopupAction.parseEditor(PopupAction.editorValue(slot))) == expected)
        val key = FlexNode().apply { this[KEY_KEY] = "main"; this[0] = "old"; this[7] = slot }
        for (minify in listOf(false, true)) {
            val restored = FlexParser(key.toStr(0, false, minify)) { }.parse()
            check(restored.str(0) == "old" && restored.childCount() == 8)
            check(events(restored[7]) == expected)
        }
    }
    check(PopupAction.from(node("(code: -61, meta: 0x1001)"))!!.meta == 0x1001)
    check(PopupAction.from(node("(key: Tab, code: -61)"))!!.label({ "code:$it" }) == "Tab")
    check(PopupAction.from(node("(code: -61)"))!!.label({ "code:$it" }) == "code:61")
    check(PopupAction.from(node("(text: \"-61\")"))!!.label({ error("Literal text interpreted as code") }) == "-61")
    check(PopupAction.from(" -61 ")!!.label({ "code:$it" }) == "code:61")
    check(events("-61") == listOf("key:-61") && events("61") == listOf("text:61"))
    check(events("(x)") == listOf("text:(x)"))
    // Minus look-alikes from phone keyboards still mean a key code; a lone dash stays a character.
    for (minus in listOf("\u2212", "\u2013", "\u2014", "\uFF0D")) check(events(" ${minus}61 ") == listOf("key:-61")) { minus }
    // Escaped non-ASCII followed by hex-digit characters must survive saving and reloading.
    for (raw in listOf("\u221261", "é1", "éa", "😀f", "\u0001F", "ж")) {
        val saved = FlexNode().apply { this[0] = raw }
        check(FlexParser(saved.toStr(0, false, true)) { }.parse().str(0) == raw) { raw }
    }
    check(events("-") == listOf("key:45") && events("\u2212") == listOf("key:8722"))
    check(PopupAction.from(node("(key: \"-61\")"))!!.label({ "code:$it" }) == "code:61")
    check(PopupAction.from(node("(key: Tab, code: -61)"))!!.label({ "code:$it" }) == "Tab")
    val inherited = FlexNode().apply { this[KEY_CODE] = "-67"; this[KEY_TEXT] = "wrong" }
    val independent = node("(code: -61)").apply { parent = inherited }
    check(events(independent) == listOf("key:-61"))
    for (bad in listOf("", "text: a", "(code: -61", "(code: -61))", "(code: -61)(text: x)",
        "()", "(code: 0x)", "(meta: 0x, code: -61)", "(code: nope)", "(code: 0x100000000)", "(code: --61)", "(code: -2147483648)",
        "(code: 0xD800)", "(code: 0x110000)", "(meta: -1, code: -61)", "(meta: nope, text: x)",
        "(text: (a))", "(code: -61, x)", "(hold: x)", "(combo: \"ctrl+\")", "(combo: \"oops+-61\")",
        "(text: x, textCurOffset: -2)", "(textCurOffset: 0, code: -61)")) {
        check(runCatching { node(bad) }.isFailure) { "Invalid settings accepted: $bad" }
    }
    check(PopupAction.from(FlexNode()) == null)
    for ((raw, expected) in listOf("-61" to -61, "0x1001" to 4097, "-0x3d" to -61, " 1 " to 1)) check(PopupAction.number(raw) == expected)
    for (raw in listOf("0x100000000", "-0x8000000000000000", "--61", "-+61", "2147483648")) check(PopupAction.number(raw) == null)
}
