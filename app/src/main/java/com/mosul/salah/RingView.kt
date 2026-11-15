package com.mosul.salah

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** عداد دائري: الحلقة تمتلي كل ما تسبّح، وبالنص العدد الباقي */
class RingView(ctx: Context) : View(ctx) {
    var total = 1
    var left = 1

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val box = RectF()

    fun set(total: Int, left: Int) {
        this.total = maxOf(1, total)
        this.left = left
        contentDescription = if (left == 0) "تم" else "باقي $left"
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val sw = 6 * d
        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f - sw
        box.set(cx - r, cy - r, cx + r, cy + r)

        track.strokeWidth = sw; track.color = Ui.LINE
        arc.strokeWidth = sw; arc.color = Ui.ACCENT
        val done = left == 0
        inner.color = if (done) Ui.ACCENT else Ui.ROW
        c.drawCircle(cx, cy, r - sw / 2f, inner)
        c.drawArc(box, 0f, 360f, false, track)
        val sweep = 360f * (total - left) / total
        if (sweep > 0) c.drawArc(box, -90f, sweep, false, arc)

        txt.color = if (done) Ui.BG else Ui.TEXT
        txt.textSize = if (done) 26 * d else if (left >= 100) 16 * d else 20 * d
        val label = if (done) "✓" else left.toString()
        val y = cy - (txt.descent() + txt.ascent()) / 2f
        c.drawText(label, cx, y, txt)
    }
}
