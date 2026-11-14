package com.mosul.salah

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** حساب أوقات الصلاة فلكياً لمدينة الموصل */
object PrayerCalc {
    const val LAT = 36.34
    const val LNG = 43.13
    const val TZ = 3.0
    val ZONE: ZoneId = ZoneId.of("Asia/Baghdad")

    val NAMES = arrayOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
    val METHODS = arrayOf("رابطة العالم الإسلامي", "أم القرى", "الهيئة المصرية")

    private const val D2R = Math.PI / 180.0

    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0
        var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** يرجع (الميل، معادلة الوقت) */
    private fun sunPos(jd: Double): DoubleArray {
        val dd = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * dd, 360.0)
        val q = fix(280.459 + 0.98564736 * dd, 360.0)
        val l = fix(q + 1.915 * sin(g * D2R) + 0.020 * sin(2 * g * D2R), 360.0)
        val e = 23.439 - 0.00000036 * dd
        val ra = atan2(cos(e * D2R) * sin(l * D2R), cos(l * D2R)) / D2R / 15.0
        val eqt = q / 15.0 - fix(ra, 24.0)
        val decl = asin(sin(e * D2R) * sin(l * D2R)) / D2R
        return doubleArrayOf(decl, eqt)
    }

    /** أوقات اليوم بالساعات (توقيت بغداد) بدون التعديلات اليدوية */
    fun hours(date: LocalDate, method: Int, asrFactor: Int): DoubleArray {
        val fajrAngle = when (method) { 1 -> 18.5; 2 -> 19.5; else -> 18.0 }
        val ishaAngle = when (method) { 2 -> 17.5; else -> 17.0 }
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - LNG / (15.0 * 24.0)

        fun mid(t: Double): Double = fix(12 - sunPos(jd + t)[1], 24.0)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPos(jd + t)[0]
            val noon = mid(t)
            var x = (-sin(angle * D2R) - sin(decl * D2R) * sin(LAT * D2R)) /
                (cos(decl * D2R) * cos(LAT * D2R))
            if (x > 1) x = 1.0
            if (x < -1) x = -1.0
            val tt = acos(x) / D2R / 15.0
            return if (ccw) noon - tt else noon + tt
        }
        fun asr(t: Double): Double {
            val decl = sunPos(jd + t)[0]
            val a = -atan(1.0 / (asrFactor + tan(abs(LAT - decl) * D2R))) / D2R
            return angleTime(a, t, false)
        }

        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val p = DoubleArray(6) { t[it] / 24.0 }
            t = doubleArrayOf(
                angleTime(fajrAngle, p[0], true),
                angleTime(0.833, p[1], true),
                mid(p[2]),
                asr(p[3]),
                angleTime(0.833, p[4], false),
                angleTime(ishaAngle, p[5], false)
            )
        }
        val adj = TZ - LNG / 15.0
        val out = DoubleArray(6) { t[it] + adj }
        if (method == 1) out[5] = out[4] + 1.5 // أم القرى: العشاء بعد المغرب بساعة ونص
        return out
    }

    /** أوقات اليوم كملي ثانية (بساعة التطبيق) مع التعديلات اليدوية */
    fun times(ctx: Context, date: LocalDate): LongArray {
        val p = Prefs(ctx)
        val h = hours(date, p.method, p.asrFactor)
        val base = date.atStartOfDay(ZONE).toInstant().toEpochMilli()
        return LongArray(6) {
            val minutes = (h[it] * 60.0).roundToLong() + p.offset(it)
            base + minutes * 60_000L
        }
    }

    fun today(ctx: Context): LocalDate =
        java.time.Instant.ofEpochMilli(AppClock.now(ctx)).atZone(ZONE).toLocalDate()

    /** الصلاة الجاية: (رقمها، وقتها). إذا onlyEnabled يتخطى الصلوات المطفية */
    fun next(ctx: Context, from: Long, onlyEnabled: Boolean): Pair<Int, Long> {
        val p = Prefs(ctx)
        var date = java.time.Instant.ofEpochMilli(from).atZone(ZONE).toLocalDate()
        repeat(3) {
            val ts = times(ctx, date)
            for (i in 0 until 6) {
                if (onlyEnabled && !p.adhanOn(i)) continue
                if (!onlyEnabled && i == 1) continue
                if (ts[i] > from) return Pair(i, ts[i])
            }
            date = date.plusDays(1)
        }
        return Pair(0, from + 86_400_000L)
    }

    fun fmt(ms: Long): String {
        val z = java.time.Instant.ofEpochMilli(ms).atZone(ZONE)
        val h = z.hour
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(java.util.Locale.US, "%02d:%02d %s", h12, z.minute, if (h < 12) "ص" else "م")
    }
}
