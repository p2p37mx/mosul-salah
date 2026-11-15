package com.mosul.salah

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * الشريط السفلي: الصفحة المختارة يرتفع فوگها قوس، وأيقونتها بدائرة ذهبية
 */
class NavBar(ctx: Context, private val onPick: (Int) -> Unit) : FrameLayout(ctx) {
    private val labels = arrayOf("الأوقات", "التقويم", "الأذكار", "الضبط")
    private val icons = intArrayOf(R.drawable.ic_nav_time, R.drawable.ic_nav_cal, R.drawable.ic_nav_dhikr, R.drawable.ic_nav_set)

    private val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        clipChildren = false
        clipToPadding = false
    }
    private val items = ArrayList<LinearLayout>()
    private val iconViews = ArrayList<ImageView>()
    private val textViews = ArrayList<TextView>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    private var selected = 0
    private var bumpX = -1f
    private var anim: ValueAnimator? = null

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    /** المسافة من فوگ لحد خط الشريط (القوس يطلع فوگها) */
    private val barTop get() = dp(22)

    init {
        setWillNotDraw(false)
        clipChildren = false
        for (i in labels.indices) {
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                clipChildren = false
                clipToPadding = false
                contentDescription = labels[i]
                setOnClickListener { select(i, true); onPick(i) }
            }
            val ic = ImageView(ctx).apply {
                setImageResource(icons[i])
                val pd = dp(10).toInt()
                setPadding(pd, pd, pd, pd)
            }
            val tv = TextView(ctx).apply {
                text = labels[i]
                textSize = 12f
                gravity = Gravity.CENTER
            }
            item.addView(ic, LinearLayout.LayoutParams(dp(46).toInt(), dp(46).toInt()))
            item.addView(tv)
            row.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
            items.add(item); iconViews.add(ic); textViews.add(tv)
        }
        clipToPadding = false
        addView(row, LayoutParams(-1, -1).apply { topMargin = dp(18).toInt() })
        applyColors()
    }

    fun applyColors() {
        fill.color = Ui.NAV
        stroke.color = Ui.LINE
        stroke.strokeWidth = dp(1)
        for (i in items.indices) styleItem(i)
        invalidate()
    }

    private fun styleItem(i: Int) {
        val on = i == selected
        val ic = iconViews[i]
        val tv = textViews[i]
        if (on) {
            ic.background = Ui.bg(context, Ui.ACCENT, 23)
            ic.setColorFilter(Ui.BG)
            ic.translationY = -dp(14)
            tv.setTextColor(Ui.ACCENT)
            tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            tv.translationY = dp(2)
        } else {
            ic.background = null
            ic.setColorFilter(Ui.MUTED)
            ic.translationY = 0f
            tv.setTextColor(Ui.MUTED)
            tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            tv.translationY = -dp(4)
        }
    }

    fun select(i: Int, animate: Boolean) {
        selected = i
        for (k in items.indices) styleItem(k)
        val item = items[i]
        if (item.width == 0) {
            post { select(i, false) }
            return
        }
        val target = item.left + item.width / 2f
        anim?.cancel()
        if (!animate || bumpX < 0) {
            bumpX = target; invalidate()
        } else {
            anim = ValueAnimator.ofFloat(bumpX, target).apply {
                duration = 260
                interpolator = DecelerateInterpolator()
                addUpdateListener { bumpX = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { select(selected, false) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val t = barTop
        val r = dp(36)      // نصف عرض القوس
        val lift = dp(18)   // ارتفاع القوس فوگ الخط
        val cx = if (bumpX < 0) w / 2 else bumpX

        path.reset()
        path.moveTo(0f, t)
        path.lineTo(cx - r - dp(14), t)
        path.cubicTo(cx - r, t, cx - r * 0.75f, t - lift, cx, t - lift)
        path.cubicTo(cx + r * 0.75f, t - lift, cx + r, t, cx + r + dp(14), t)
        path.lineTo(w, t)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, fill)

        // خط رفيع على الحافة العليا حتى يبين القوس
        path.reset()
        path.moveTo(0f, t)
        path.lineTo(cx - r - dp(14), t)
        path.cubicTo(cx - r, t, cx - r * 0.75f, t - lift, cx, t - lift)
        path.cubicTo(cx + r * 0.75f, t - lift, cx + r, t, cx + r + dp(14), t)
        path.lineTo(w, t)
        canvas.drawPath(path, stroke)
    }
}
