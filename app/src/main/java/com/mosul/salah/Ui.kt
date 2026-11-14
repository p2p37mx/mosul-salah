package com.mosul.salah

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** ألوان وأدوات بناء الواجهة */
object Ui {
    var dark = true
    var BG = Color.parseColor("#0D1B1E")
    var CARD = Color.parseColor("#15282C")
    var ROW = Color.parseColor("#132427")
    var LINE = Color.parseColor("#22393E")
    var NAV = Color.parseColor("#102326")
    var TEXT = Color.parseColor("#F2EEE3")
    var MUTED = Color.parseColor("#9FB1AE")
    var ACCENT = Color.parseColor("#E0B25A")
    var DOT = Color.parseColor("#3C5A5F")

    /** الوضع الليلي (القمر) أو النهاري (الشمس) */
    fun applyTheme(isDark: Boolean) {
        dark = isDark
        if (isDark) {
            BG = Color.parseColor("#0D1B1E"); CARD = Color.parseColor("#15282C"); ROW = Color.parseColor("#132427")
            LINE = Color.parseColor("#22393E"); NAV = Color.parseColor("#102326"); TEXT = Color.parseColor("#F2EEE3")
            MUTED = Color.parseColor("#9FB1AE"); ACCENT = Color.parseColor("#E0B25A"); DOT = Color.parseColor("#3C5A5F")
        } else {
            BG = Color.parseColor("#F6F3EC"); CARD = Color.parseColor("#FFFFFF"); ROW = Color.parseColor("#EFEAE0")
            LINE = Color.parseColor("#E2DACB"); NAV = Color.parseColor("#FFFFFF"); TEXT = Color.parseColor("#1B2A2C")
            MUTED = Color.parseColor("#5E6B69"); ACCENT = Color.parseColor("#8C5E0E"); DOT = Color.parseColor("#C9BFA8")
        }
    }

    /** نفس اللون بشفافية (0-255) */
    fun alpha(color: Int, a: Int): Int = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun bg(c: Context, color: Int, radius: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radius).toFloat()
            if (stroke != 0) setStroke(dp(c, 1), stroke)
        }

    fun text(c: Context, s: String, size: Float, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    fun vbox(c: Context, gapDp: Int = 0): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        if (gapDp > 0) {
            dividerDrawable = GradientDrawable().apply { setSize(1, dp(c, gapDp)); setColor(Color.TRANSPARENT) }
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }
    }

    fun hbox(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(c: Context): LinearLayout = vbox(c, 8).apply {
        background = bg(c, CARD, 20, LINE)
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
    }

    fun button(c: Context, label: String, primary: Boolean, onClick: () -> Unit): TextView =
        TextView(c).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            minHeight = dp(c, 44)
            setPadding(dp(c, 14), 0, dp(c, 14), 0)
            if (primary) {
                setTextColor(BG); background = bg(c, ACCENT, 12)
            } else {
                setTextColor(TEXT); background = bg(c, ROW, 12, LINE)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** زر صغير مربع (+ / −) */
    fun small(c: Context, label: String, desc: String, onClick: () -> Unit): TextView =
        TextView(c).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            background = bg(c, ROW, 10, LINE)
            contentDescription = desc
            layoutParams = LinearLayout.LayoutParams(dp(c, 40), dp(c, 40))
            setOnClickListener { onClick() }
        }

    /** زرين متبادلين (مثل ميلادي/هجري) */
    fun segment(c: Context, labels: Array<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val box = hbox(c).apply {
            background = bg(c, BG, 12, LINE)
            setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4))
        }
        labels.forEachIndexed { i, s ->
            val on = i == selected
            box.addView(TextView(c).apply {
                text = s
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                minHeight = dp(c, 38)
                setPadding(dp(c, 12), 0, dp(c, 12), 0)
                setTextColor(if (on) BG else MUTED)
                background = if (on) bg(c, ACCENT, 9) else null
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return box
    }

    fun spacer(c: Context): View = View(c)
}
