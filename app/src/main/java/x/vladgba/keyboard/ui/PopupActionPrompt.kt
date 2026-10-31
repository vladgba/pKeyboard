package x.vladgba.keyboard.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import x.vladgba.keyboard.R
import x.vladgba.keyboard.core.*
import x.vladgba.keyboard.flex.FlexNode
import x.vladgba.keyboard.keyboard.Combos
import x.vladgba.keyboard.keyboard.KeyValue
import x.vladgba.keyboard.keyboard.PopupAction

/** Explicit modes keep literal strings and settings nodes distinct, including strings like "(x)". */
internal object PopupActionPrompt {
    fun show(ctx: Context, slot: Any?, onOk: (Any) -> Unit) {
        val title = ctx.getString(R.string.ui_popup_char)
        val options = arrayOf(ctx.getString(R.string.ui_popup_action), ctx.getString(R.string.ui_popup_keycode),
            ctx.getString(R.string.ui_popup_literal), ctx.getString(R.string.ui_popup_settings))
        AlertDialog.Builder(ctx).setTitle(title).setItems(options) { _, choice ->
            val action = PopupAction.from(slot)
            if (choice == 0) {
                ActionPrompt.show(ctx, title, if (slot is String) slot else action?.value.orEmpty()) { onOk(it) }
                return@setItems
            }
            if (choice == 1) {
                showKeyCode(ctx, options[1], settings(slot), onOk)
                return@setItems
            }
            val mode = choice - 1 // 1: literal text, 2: key settings
            val initial = if (mode == 1) action?.value.orEmpty() else PopupAction.editorValue(settings(slot))
            val input = EditText(ctx).apply {
                setText(initial)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                minLines = 3
            }
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0)
                addView(TextView(ctx).apply { text = ctx.getString(if (mode == 1) R.string.ui_popup_literal_help else R.string.ui_popup_settings_help) })
                addView(input)
            }
            val dialog = AlertDialog.Builder(ctx).setTitle(options[mode]).setView(ScrollView(ctx).apply { addView(box) })
                .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    try {
                        val value = input.text.toString()
                        val node = if (mode == 1) FlexNode().apply { this[KEY_TEXT] = value } else PopupAction.parseEditor(value)
                        if (node.has(KEY_COMBO)) require(Combos.parse(node.str(KEY_COMBO), requireModifier = false) != null) { ctx.getString(R.string.ui_popup_bad_combo) }
                        onOk(node)
                        dialog.dismiss()
                    } catch (e: IllegalArgumentException) {
                        input.error = e.message
                    }
                }
            }
            dialog.show()
        }.show()
    }

    /**
     * An explicit Android key code: `-61`, `61`, `tab` or a pick from the list all store
     * `(code: "-61")`, so the slot can never be typed as text. A label and meta mask are kept.
     */
    private fun showKeyCode(ctx: Context, title: String, current: FlexNode, onOk: (Any) -> Unit) {
        val own = FlexNode(current, null)
        val code = PopupAction.number(own.str(KEY_CODE))?.takeIf { it < 0 }
        val input = EditText(ctx).apply {
            setText(code?.toString().orEmpty())
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val meaning = TextView(ctx).apply { setPadding(0, ctx.dp(4), 0, ctx.dp(8)) }
        fun refresh() {
            meaning.text = keyCode(input.text.toString())?.let { "${Combos.keyLabel(it)}  ·  -$it" }
                ?: ctx.getString(R.string.ui_popup_keycode_help)
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = refresh()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        refresh()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0)
            addView(input)
            addView(meaning)
        }
        val dialog = AlertDialog.Builder(ctx).setTitle(title).setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.ui_keycodes_title, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                KeyCodePicker.show(ctx) { input.setText("-${it.code}") }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val picked = keyCode(input.text.toString())
                if (picked == null) {
                    input.error = ctx.getString(R.string.ui_popup_keycode_help)
                    return@setOnClickListener
                }
                onOk(FlexNode().apply {
                    // A key label that is itself a code would hide the picked one; drop it.
                    if (own.has(KEY_KEY) && KeyValue.keyCode(own.str(KEY_KEY)) == null) this[KEY_KEY] = own.str(KEY_KEY)
                    this[KEY_CODE] = "-$picked"
                    if (own.has(KEY_MOD_META)) this[KEY_MOD_META] = own.str(KEY_MOD_META)
                })
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Android key code (positive) for `-61`, `61`, `0x3d` or a key name like `tab`. */
    internal fun keyCode(value: String): Int? {
        val v = value.trim()
        if (v.isEmpty()) return null
        val n = PopupAction.number(v)
        val code = if (n != null) (if (n < 0) -n.toLong() else n.toLong()) else Combos.keyCode(v)?.toLong()
        return code?.takeIf { it in 1..android.view.KeyEvent.getMaxKeyCode() }?.toInt()
    }

    private fun settings(slot: Any?): FlexNode {
        if (slot is FlexNode) return slot
        val value = slot as? String ?: ""
        return FlexNode().apply {
            when {
                KeyValue.keyCode(value) != null -> this[KEY_CODE] = KeyValue.keyCode(value).toString()
                Combos.parse(value) != null -> this[KEY_COMBO] = value
                value.startsWith("macro:") -> this[KEY_MACRO] = value.removePrefix("macro:")
                value.startsWith(LAYOUT_ACTION_PREFIX) -> this[KEY_LAYOUT] = value.removePrefix(LAYOUT_ACTION_PREFIX)
                value.startsWith("@") || value.codePointCount(0, value.length) == 1 -> this[KEY_KEY] = value
                else -> this[KEY_TEXT] = value
            }
        }
    }
}
