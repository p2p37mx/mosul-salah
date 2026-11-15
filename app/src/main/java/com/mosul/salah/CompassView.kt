package com.mosul.salah

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** بوصلة القبلة: القرص يدور حسب اتجاه الجوال، والسهم الذهبي يأشّر للكعبة */
class CompassView(ctx: Context) : View(ctx) {
    companion object {
        private const val KAABA_LAT = 21.4225
        private const val KAABA_LNG = 39.8262

        /** اتجاه القبلة من الموصل بالدرجات من الشمال الحقيقي */
        val QIBLA: Double by lazy {
            val la1 = Math.toRadians(PrayerCalc.LAT)
            val la2 = Math.toRadians(KAABA_LAT)
            val d = Math.toRadians(KAABA_LNG - PrayerCalc.LNG)
            val b = Math.toDegrees(atan2(sin(d) * cos(la2), cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(d)))
            (b + 360.0) % 360.0
        }

        /** الفرق بين زاويتين (-180..180) */
        fun diff(a: Double, b: Double): Double {
            var x = (a - b) % 360.0
            if (x > 180) x -= 360.0
            if (x < -180) x += 360.0
            return x
        }
    }

    /** اتجاه الجوال من الشمال الحقيقي */
    var azimuth = 0.0
        set(v) { field = v; invalidate() }

    val aligned: Boolean get() = abs(diff(QIBLA, azimuth)) < 4.0

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 18 * d
        val ok = aligned

        // القرص
        fill.color = Ui.CARD
        c.drawCircle(cx, cy, r, fill)
        ring.color = if (ok) Ui.ACCENT else Ui.LINE
        ring.strokeWidth = if (ok) 6 * d else 2 * d
        c.drawCircle(cx, cy, r, ring)

        // المؤشر الثابت فوگ (اتجاه الجوال)
        fill.color = Ui.ACCENT
        path.reset()
        path.moveTo(cx, cy - r - 14 * d)
        path.lineTo(cx - 8 * d, cy - r - 2 * d)
        path.lineTo(cx + 8 * d, cy - r - 2 * d)
        path.close()
        c.drawPath(path, fill)

        // القرص يدور عكس اتجاه الجوال
        c.save()
        c.rotate((-azimuth).toFloat(), cx, cy)
        for (deg in 0 until 360 step 10) {
            val major = deg % 90 == 0
            tick.color = if (major) Ui.TEXT else Ui.MUTED
            tick.strokeWidth = if (major) 3 * d else 1.5f * d
            val len = if (major) 14 * d else if (deg % 30 == 0) 10 * d else 6 * d
            val a = Math.toRadians(deg.toDouble())
            val sx = (cx + sin(a) * (r - 6 * d)).toFloat()
            val sy = (cy - cos(a) * (r - 6 * d)).toFloat()
            val ex = (cx + sin(a) * (r - 6 * d - len)).toFloat()
            val ey = (cy - cos(a) * (r - 6 * d - len)).toFloat()
            c.drawLine(sx, sy, ex, ey, tick)
        }
        val labels = arrayOf("ش", "ق", "ج", "غ")
        txt.textSize = 18 * d
        for (k in 0 until 4) {
            val a = Math.toRadians(k * 90.0)
            val lx = (cx + sin(a) * (r - 36 * d)).toFloat()
            val ly = (cy - cos(a) * (r - 36 * d)).toFloat() - (txt.descent() + txt.ascent()) / 2f
            txt.color = if (k == 0) Ui.ACCENT else Ui.TEXT
            c.drawText(labels[k], lx, ly, txt)
        }

        // سهم القبلة
        c.rotate(QIBLA.toFloat(), cx, cy)
        fill.color = Ui.ACCENT
        path.reset()
        path.moveTo(cx, cy - r + 50 * d)
        path.lineTo(cx - 16 * d, cy - r + 82 * d)
        path.lineTo(cx - 5 * d, cy - r + 78 * d)
        path.lineTo(cx - 5 * d, cy + 18 * d)
        path.lineTo(cx + 5 * d, cy + 18 * d)
        path.lineTo(cx + 5 * d, cy - r + 78 * d)
        path.lineTo(cx + 16 * d, cy - r + 82 * d)
        path.close()
        c.drawPath(path, fill)
        // رمز الكعبة على الحافة
        val ks = 13 * d
        val ky = cy - r + 26 * d
        fill.color = Ui.TEXT
        c.drawRoundRect(cx - ks, ky - ks, cx + ks, ky + ks, 3 * d, 3 * d, fill)
        fill.color = Ui.ACCENT
        c.drawRect(cx - ks, ky - ks * 0.45f, cx + ks, ky - ks * 0.2f, fill)
        c.restore()

        // المركز
        fill.color = if (ok) Ui.ACCENT else Ui.TEXT
        c.drawCircle(cx, cy, 9 * d, fill)
    }
}
