package x.vladgba.keyboard.keyboard

import x.vladgba.keyboard.core.*
import x.vladgba.keyboard.flex.FlexNode
import x.vladgba.keyboard.flex.FlexParser

/** A popup slot owns its action; it must never inherit the main key's code or text. */
internal data class PopupAction(
    val type: Type,
    val value: String,
    val meta: Int = 0,
    val cursorOffset: Int? = null,
    val caption: String? = null,
) {
    enum class Type { ACTION, LABEL, CODE, TEXT, COMBO }

    fun dispatch(
        onKey: (Int) -> Unit,
        onText: (String) -> Unit,
        runSpecial: (String) -> Boolean,
        sendCombo: (String) -> Boolean,
        resolveLabel: (String) -> String = { it },
    ) {
        when (type) {
            Type.ACTION -> KeyValue.dispatch(value, onKey, onText, runSpecial, singleCharacterAsKey = true)
            Type.LABEL -> if (!runSpecial(value.trim())) {
                val label = resolveLabel(value)
                if (label.isNotEmpty()) onKey(label.codePointAt(0))
            }
            Type.CODE -> onKey(number(value)!!)
            Type.COMBO -> sendCombo(value)
            Type.TEXT -> {
                onText(value)
                cursorOffset?.let { offset ->
                    val steps = if (offset < 0) -offset else value.length - offset
                    repeat(steps) { onKey(-21) } // Android KEYCODE_DPAD_LEFT, as for a main text key
                }
            }
        }
    }

    fun label(keyLabel: (Int) -> String, resolveLabel: (String) -> String = { it }): String =
        caption?.let(resolveLabel) ?: when (type) {
            Type.CODE -> number(value)!!.let { if (it < 0) keyLabel(-it) else if (it == 0) "" else String(Character.toChars(it)) }
            Type.ACTION -> value.trim().toIntOrNull()?.takeIf { it < 0 && it != Int.MIN_VALUE }?.let { keyLabel(-it) } ?: value
            else -> value
        }

    companion object {
        private val fields = setOf(KEY_KEY, KEY_CODE, KEY_TEXT, KEY_MOD_META, KEY_COMBO, KEY_MACRO, KEY_LAYOUT, KEY_TEXT_CURSOR_OFFSET)

        /** Decimal and hex integers, without FlexNode.parseInt's truncation of oversized hex values. */
        fun number(raw: String): Int? {
            val s = raw.trim()
            val negative = s.startsWith('-')
            val body = if (negative) s.drop(1) else s
            if (negative && (body.startsWith('-') || body.startsWith('+'))) return null
            val n = if (body.startsWith("0x", true)) body.drop(2).toLongOrNull(16) else body.toLongOrNull()
            if (n == null || n < 0) return null
            val signed = if (negative) -n else n
            return signed.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        }

        fun problems(node: FlexNode): List<String> {
            val errors = mutableListOf<String>()
            if (node.childCount() != 0) errors += "Popup actions cannot contain positional children"
            for ((name, value) in node.params) {
                if (name !in fields) errors += "Unsupported popup setting: $name"
                if (value !is String) errors += "Popup setting $name must be a string or number"
            }
            val own = FlexNode(node, null)
            if (own.params.containsKey(KEY_CODE)) {
                val code = number(own.str(KEY_CODE))
                if (code == null || code == Int.MIN_VALUE || code > 0 && (!Character.isValidCodePoint(code) || code in 0xD800..0xDFFF)) errors += "Invalid popup key code"
            }
            if (own.params.containsKey(KEY_MOD_META) && (number(own.str(KEY_MOD_META))?.let { it >= 0 } != true)) errors += "Invalid popup meta mask"
            if (own.params.containsKey(KEY_TEXT_CURSOR_OFFSET)) {
                val offset = number(own.str(KEY_TEXT_CURSOR_OFFSET))
                if (!own.params.containsKey(KEY_TEXT) || offset == null || offset.toLong() !in -own.str(KEY_TEXT).length.toLong()..own.str(KEY_TEXT).length.toLong()) errors += "Popup textCurOffset must be within the text length"
            }
            if (own.has(KEY_COMBO)) {
                val parts = own.str(KEY_COMBO).trim().split('+').map { it.trim() }
                val key = parts.last()
                val code = number(key)
                if (parts.any { it.isEmpty() } || parts.dropLast(1).any { !Combos.isModifierName(it) } ||
                    (code != null && (code >= 0 || code == Int.MIN_VALUE)) ||
                    (code == null && !key.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))) errors += "Invalid popup combination"
            }
            if (listOf(KEY_KEY, KEY_CODE, KEY_TEXT, KEY_COMBO, KEY_MACRO, KEY_LAYOUT).none { own.params.containsKey(it) }) errors += "Popup has no label or action"
            for (name in listOf(KEY_COMBO, KEY_MACRO, KEY_LAYOUT)) {
                if (own.params.containsKey(name) && own.str(name).isBlank()) errors += "Popup $name must not be empty"
            }
            return errors
        }

        fun from(slot: Any?): PopupAction? {
            if (slot is String) return PopupAction(Type.ACTION, slot)
            val node = slot as? FlexNode ?: return null
            if (problems(node).isNotEmpty()) return null
            val own = FlexNode(node, null)
            val caption = own.str(KEY_KEY).takeIf { own.params.containsKey(KEY_KEY) }
            val meta = number(own.str(KEY_MOD_META)) ?: 0
            fun action(type: Type, value: String) = PopupAction(type, value, meta, caption = caption)
            return when {
                own.has(KEY_MACRO) -> action(Type.ACTION, "macro:" + own.str(KEY_MACRO).trim())
                own.has(KEY_COMBO) -> action(Type.COMBO, own.str(KEY_COMBO).trim())
                own.has(KEY_LAYOUT) -> action(Type.ACTION, LAYOUT_ACTION_PREFIX + own.str(KEY_LAYOUT).trim())
                own.has(KEY_TEXT) -> PopupAction(Type.TEXT, own.str(KEY_TEXT), meta, number(own.str(KEY_TEXT_CURSOR_OFFSET)), caption)
                own.has(KEY_CODE) -> action(Type.CODE, own.str(KEY_CODE))
                else -> action(Type.LABEL, own.str(KEY_KEY))
            }
        }

        fun editorValue(slot: Any?): String = when (slot) {
            is FlexNode -> slot.toStr(0, false, true)
            is String -> slot
            else -> ""
        }

        /** Structured mode is explicit, so ordinary strings starting with '(' remain literal. */
        fun parseEditor(value: String): FlexNode {
            val parser = FlexParser(value) { }
            val tokens = parser.tokenize()
            require(tokens.firstOrNull()?.type == FlexParser.TokenType.BEGIN) { "Enter a popup settings node" }
            var depth = 0
            for ((index, token) in tokens.withIndex()) {
                if (token.type == FlexParser.TokenType.BEGIN) depth++
                if (token.type == FlexParser.TokenType.END) depth--
                require(depth >= 0 && (depth != 0 || index == tokens.lastIndex)) { "Enter one complete popup settings node" }
            }
            require(depth == 0) { "Unbalanced popup settings" }
            val node = parser.parse()
            require(parser.warnings.isEmpty()) { parser.warnings.joinToString() }
            require(problems(node).isEmpty()) { problems(node).joinToString("\n") }
            return node
        }
    }
}
