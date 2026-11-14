package com.mosul.salah

import android.content.Context
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import java.time.LocalDate

/** التاريخ الهجري (أم القرى) مع تصحيح يدوي بالأيام */
object Hijri {
    val MONTHS = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    val G_MONTHS = arrayOf(
        "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
        "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"
    )
    /** أيام الأسبوع بترتيب java.time (الاثنين = 1) */
    private val DAYS = arrayOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

    fun dayName(d: LocalDate): String = DAYS[d.dayOfWeek.value - 1]

    /** يرجع (اليوم، الشهر 0-11، السنة) */
    fun of(ctx: Context, d: LocalDate): IntArray {
        val adj = Prefs(ctx).hijriAdjust
        val cal = IslamicCalendar(TimeZone.getTimeZone("Asia/Baghdad"))
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        val noon = d.plusDays(adj.toLong()).atTime(12, 0).atZone(PrayerCalc.ZONE).toInstant().toEpochMilli()
        cal.timeInMillis = noon
        return intArrayOf(
            cal.get(android.icu.util.Calendar.DAY_OF_MONTH),
            cal.get(android.icu.util.Calendar.MONTH),
            cal.get(android.icu.util.Calendar.YEAR)
        )
    }

    fun text(ctx: Context, d: LocalDate): String {
        val h = of(ctx, d)
        return "${h[0]} ${MONTHS[h[1]]} ${h[2]} هـ"
    }

    fun gText(d: LocalDate): String = "${d.dayOfMonth} ${G_MONTHS[d.monthValue - 1]} ${d.year}"
}
