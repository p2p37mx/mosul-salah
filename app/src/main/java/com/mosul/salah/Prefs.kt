package com.mosul.salah

import android.content.Context
import android.content.SharedPreferences

/** كل إعدادات التطبيق محفوظة هنا */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("salah", Context.MODE_PRIVATE)

    /** فرق الساعة اليدوية عن ساعة الجهاز (ملي ثانية). 0 = تلقائي */
    var clockOffset: Long
        get() = sp.getLong("clockOffset", 0L)
        set(v) = sp.edit().putLong("clockOffset", v).apply()

    var manualClock: Boolean
        get() = sp.getBoolean("manualClock", false)
        set(v) = sp.edit().putBoolean("manualClock", v).apply()

    /** 0 = رابطة العالم الإسلامي، 1 = أم القرى، 2 = الهيئة المصرية */
    var method: Int
        get() = sp.getInt("method", 0)
        set(v) = sp.edit().putInt("method", v).apply()

    /** 1 = شافعي، 2 = حنفي */
    var asrFactor: Int
        get() = sp.getInt("asrFactor", 1)
        set(v) = sp.edit().putInt("asrFactor", v).apply()

    /** true = ليلي (قمر)، false = نهاري (شمس) */
    var darkTheme: Boolean
        get() = sp.getBoolean("darkTheme", true)
        set(v) = sp.edit().putBoolean("darkTheme", v).apply()

    /** رقم صورة الخلفية الحالية (تتبدل كل فتح) */
    var bgIndex: Int
        get() = sp.getInt("bgIndex", 0)
        set(v) = sp.edit().putInt("bgIndex", v).apply()

    var hijriAdjust: Int
        get() = sp.getInt("hijriAdjust", 0)
        set(v) = sp.edit().putInt("hijriAdjust", v).apply()

    fun offset(i: Int): Int = sp.getInt("off$i", 0)
    fun setOffset(i: Int, v: Int) = sp.edit().putInt("off$i", v).apply()

    /** هل الأذان مفعل لهاي الصلاة (الشروق مطفي افتراضياً) */
    fun adhanOn(i: Int): Boolean = sp.getBoolean("on$i", i != 1)
    fun setAdhanOn(i: Int, v: Boolean) = sp.edit().putBoolean("on$i", v).apply()

    /** صوت الأذان: مسار ملف داخلي (صوت أو فيديو). فارغ = صوت المنبه الافتراضي */
    var mediaPath: String
        get() = sp.getString("mediaPath", "") ?: ""
        set(v) = sp.edit().putString("mediaPath", v).apply()

    var mediaName: String
        get() = sp.getString("mediaName", "") ?: ""
        set(v) = sp.edit().putString("mediaName", v).apply()

    var mediaIsVideo: Boolean
        get() = sp.getBoolean("mediaIsVideo", false)
        set(v) = sp.edit().putBoolean("mediaIsVideo", v).apply()

    /** صورة الأذان (ثابتة أو متحركة GIF) */
    var imagePath: String
        get() = sp.getString("imagePath", "") ?: ""
        set(v) = sp.edit().putString("imagePath", v).apply()

    var imageName: String
        get() = sp.getString("imageName", "") ?: ""
        set(v) = sp.edit().putString("imageName", v).apply()
}

/** ساعة التطبيق: ساعة الجهاز + الفرق اليدوي */
object AppClock {
    fun now(ctx: Context): Long {
        val p = Prefs(ctx)
        return System.currentTimeMillis() + (if (p.manualClock) p.clockOffset else 0L)
    }

    fun offset(ctx: Context): Long {
        val p = Prefs(ctx)
        return if (p.manualClock) p.clockOffset else 0L
    }
}
