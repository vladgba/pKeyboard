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
import x.vladgba.keyboard.keyboard.PopupAction

/** Explicit modes keep literal strings and settings nodes distinct, including strings like "(x)". */
internal object PopupActionPrompt {
    fun show(ctx: Context, slot: Any?, onOk: (Any) -> Unit) {
        val title = ctx.getString(R.string.ui_popup_char)
        val options = arrayOf(ctx.getString(R.string.ui_popup_action), ctx.getString(R.string.ui_popup_literal), ctx.getString(R.string.ui_popup_settings))
        AlertDialog.Builder(ctx).setTitle(title).setItems(options) { _, mode ->
            val action = PopupAction.from(slot)
            if (mode == 0) {
                ActionPrompt.show(ctx, title, if (slot is String) slot else action?.value.orEmpty()) { onOk(it) }
                return@setItems
            }
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

    private fun settings(slot: Any?): FlexNode {
        if (slot is FlexNode) return slot
        val value = slot as? String ?: ""
        return FlexNode().apply {
            when {
                value.trim().toIntOrNull()?.let { it < 0 } == true -> this[KEY_CODE] = value.trim()
                Combos.parse(value) != null -> this[KEY_COMBO] = value
                value.startsWith("macro:") -> this[KEY_MACRO] = value.removePrefix("macro:")
                value.startsWith(LAYOUT_ACTION_PREFIX) -> this[KEY_LAYOUT] = value.removePrefix(LAYOUT_ACTION_PREFIX)
                value.startsWith("@") || value.codePointCount(0, value.length) == 1 -> this[KEY_KEY] = value
                else -> this[KEY_TEXT] = value
            }
        }
    }
}
