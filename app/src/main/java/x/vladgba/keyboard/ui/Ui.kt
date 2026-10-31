package x.vladgba.keyboard.ui

import x.vladgba.keyboard.pui.pColor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.app.AlertDialog

/*
 * Small helpers for the screens that are built in code (settings, key tester, macros, home).
 * Plain framework widgets only, no libraries.
 */

/**
 * Suite (pUI) colors for the screens built in code. Resolved from the p_* tokens by every
 * LocalizedActivity, so they follow light/dark mode like the XML-themed parts.
 */
internal object UiPalette {
    var accent = 0xff6d28d9.toInt(); private set
    var text = 0xff16181d.toInt(); private set
    var secondary = 0xff5f6370.toInt(); private set
    var ok = 0xff1e8e5a.toInt(); private set
    var warn = 0xffc62828.toInt(); private set
    var card = 0xffffffff.toInt(); private set
    var surface2 = 0xffececf1.toInt(); private set
    var line = 0xffe2e2e8.toInt(); private set

    fun load(ctx: Context) {
        accent = ctx.pColor(x.vladgba.keyboard.R.color.p_accent_text)
        text = ctx.pColor(x.vladgba.keyboard.R.color.p_text)
        secondary = ctx.pColor(x.vladgba.keyboard.R.color.p_text2)
        ok = ctx.pColor(x.vladgba.keyboard.R.color.p_success)
        warn = ctx.pColor(x.vladgba.keyboard.R.color.p_danger)
        card = ctx.pColor(x.vladgba.keyboard.R.color.p_surface)
        surface2 = ctx.pColor(x.vladgba.keyboard.R.color.p_surface2)
        line = ctx.pColor(x.vladgba.keyboard.R.color.p_line)
    }
}

internal val COLOR_ACCENT get() = UiPalette.accent
internal val COLOR_SECONDARY_TEXT get() = UiPalette.secondary
internal val COLOR_OK get() = UiPalette.ok
internal val COLOR_WARN get() = UiPalette.warn
internal val COLOR_CARD get() = UiPalette.card

internal fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

internal fun Context.copyToClipboard(text: String, toastMsg: String? = null) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("pKeyboard", text))
    if (toastMsg != null) Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
}

internal fun View.selectableBackground() {
    val tv = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
    if (tv.resourceId != 0) setBackgroundResource(tv.resourceId)
}

internal fun cardBackground(ctx: Context, color: Int = COLOR_CARD) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = ctx.dp(20).toFloat()
}

/** A list row: title, optional subtitle, optional view at the end. */
internal class Row(ctx: Context) : LinearLayout(ctx) {
    val title = TextView(ctx).apply { textSize = 16f; setTextColor(UiPalette.text) }
    val subtitle = TextView(ctx).apply { textSize = 13f; setTextColor(COLOR_SECONDARY_TEXT); visibility = GONE }
    val end = FrameLayout(ctx)
    private val texts = LinearLayout(ctx).apply { orientation = VERTICAL }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(56)
        setPadding(ctx.dp(4), ctx.dp(10), ctx.dp(4), ctx.dp(10))
        texts.addView(title)
        texts.addView(subtitle)
        addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(end, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = ctx.dp(12) })
    }

    fun setSubtitle(s: CharSequence?) {
        subtitle.text = s ?: ""
        subtitle.visibility = if (s.isNullOrEmpty()) GONE else VISIBLE
    }
}

/** A vertically scrolling page. */
internal class Page(private val ctx: Context) {
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(32))
    }
    val view = ScrollView(ctx).apply {
        isFillViewport = true
        addView(root)
    }

    fun <T : View> add(v: T, lp: LinearLayout.LayoutParams? = null): T {
        if (lp != null) root.addView(v, lp) else root.addView(v)
        return v
    }

    fun clear() = root.removeAllViews()

    fun header(text: CharSequence) = add(TextView(ctx).apply {
        this.text = text
        textSize = 12f
        isAllCaps = true
        letterSpacing = 0.08f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(COLOR_ACCENT)
        setPadding(ctx.dp(4), ctx.dp(20), ctx.dp(4), ctx.dp(4))
    })

    fun note(text: CharSequence, color: Int = COLOR_SECONDARY_TEXT) = add(TextView(ctx).apply {
        this.text = text
        textSize = 14f
        setTextColor(color)
        setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(8))
    })

    fun card(build: LinearLayout.() -> Unit): LinearLayout {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground(ctx)
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
            build()
        }
        return add(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(8) })
    }

    fun row(title: CharSequence, subtitle: CharSequence? = null, onClick: (() -> Unit)? = null): Row {
        val r = Row(ctx)
        r.title.text = title
        r.setSubtitle(subtitle)
        if (onClick != null) {
            r.selectableBackground()
            r.setOnClickListener { onClick() }
        }
        return add(r)
    }

    fun switchRow(title: CharSequence, subtitle: CharSequence?, checked: Boolean, onChange: (Boolean) -> Unit): Row {
        val sw = Switch(ctx).apply { isChecked = checked; isClickable = false; isFocusable = false }
        val r = row(title, subtitle) {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
        r.end.addView(sw)
        return r
    }

    /** Slider with the current value shown at the end; [onChange] fires when the finger is lifted. */
    fun sliderRow(
        title: CharSequence, subtitle: CharSequence?, min: Float, max: Float, step: Float, value: Float,
        format: (Float) -> String, onChange: (Float) -> Unit,
    ): Pair<Row, SeekBar> {
        val r = row(title, subtitle)
        val valueText = TextView(ctx).apply { textSize = 15f; setTextColor(COLOR_ACCENT); setTypeface(typeface, Typeface.BOLD) }
        r.end.addView(valueText)
        val steps = ((max - min) / step).toInt()
        fun valueAt(p: Int) = min + p * step
        val bar = SeekBar(ctx).apply {
            this.max = steps
            progress = ((value.coerceIn(min, max) - min) / step + 0.5f).toInt()
        }
        valueText.text = format(valueAt(bar.progress))
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                valueText.text = format(valueAt(p))
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) = onChange(valueAt(sb.progress))
        })
        add(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ctx.dp(8) })
        return r to bar
    }

    fun button(text: CharSequence, onClick: () -> Unit) = add(Button(ctx).apply {
        this.text = text
        setOnClickListener { onClick() }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(4) })
}

/** Simple dialogs used across screens. */
internal object Ask {
    fun text(
        ctx: Context, title: CharSequence, value: String, message: CharSequence? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT, onOk: (String) -> Unit,
    ) {
        val input = EditText(ctx).apply {
            setText(value)
            this.inputType = inputType
            setSelection(text.length)
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .apply { if (message != null) setMessage(message) }
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun choice(ctx: Context, title: CharSequence, items: List<CharSequence>, checked: Int = -1, onPick: (Int) -> Unit) {
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), checked) { d, which ->
                d.dismiss()
                onPick(which)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun info(ctx: Context, title: CharSequence, message: CharSequence) {
        AlertDialog.Builder(ctx).setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    }
}
