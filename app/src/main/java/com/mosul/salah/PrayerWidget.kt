package com.mosul.salah

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** الويدجت (التطبيق المصغر) على الشاشة الرئيسية: خمس صلوات والجاية مأشّرة */
class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        draw(ctx, mgr, ids)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAll(ctx)
            scheduleRefresh(ctx)
        }
    }

    override fun onEnabled(ctx: Context) {
        scheduleRefresh(ctx)
    }

    companion object {
        const val ACTION_REFRESH = "com.mosul.salah.WIDGET_REFRESH"
        private val PRAYERS = intArrayOf(0, 2, 3, 4, 5)
        private val SHORT = arrayOf("فجر", "ظهر", "عصر", "مغرب", "عشاء")
        private val COLS = intArrayOf(R.id.w_col0, R.id.w_col1, R.id.w_col2, R.id.w_col3, R.id.w_col4)
        private val NAMES = intArrayOf(R.id.w_name0, R.id.w_name1, R.id.w_name2, R.id.w_name3, R.id.w_name4)
        private val ICONS = intArrayOf(R.id.w_icon0, R.id.w_icon1, R.id.w_icon2, R.id.w_icon3, R.id.w_icon4)
        private val TIMES = intArrayOf(R.id.w_time0, R.id.w_time1, R.id.w_time2, R.id.w_time3, R.id.w_time4)

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
                if (ids.isNotEmpty()) draw(ctx, mgr, ids)
            } catch (_: Exception) {}
        }

        private fun short(ms: Long): String {
            val s = PrayerCalc.fmt(ms)
            return if (s.startsWith("0")) s.substring(1) else s
        }

        private fun draw(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val p = Prefs(ctx)
            Ui.applyTheme(p.darkTheme)
            val now = AppClock.now(ctx)
            val (_, nt) = PrayerCalc.next(ctx, now, false)
            val day = java.time.Instant.ofEpochMilli(nt).atZone(PrayerCalc.ZONE).toLocalDate()
            val ts = PrayerCalc.times(ctx, day)

            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            rv.setInt(R.id.w_root, "setBackgroundResource",
                if (Ui.dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
            for (c in 0 until 5) {
                val i = PRAYERS[c]
                val isNext = ts[i] == nt
                val color = if (isNext) Ui.ACCENT else Ui.TEXT
                rv.setInt(COLS[c], "setBackgroundResource",
                    if (!isNext) 0 else if (Ui.dark) R.drawable.widget_sel_dark else R.drawable.widget_sel_light)
                rv.setTextViewText(NAMES[c], SHORT[c])
                rv.setTextColor(NAMES[c], color)
                rv.setTextViewText(TIMES[c], short(ts[i]))
                rv.setTextColor(TIMES[c], color)
                rv.setImageViewResource(ICONS[c], if (p.adhanOn(i)) R.drawable.ic_spk else R.drawable.ic_mute)
                rv.setInt(ICONS[c], "setColorFilter", if (isNext) Ui.ACCENT else Ui.MUTED)
            }
            val open = PendingIntent.getActivity(
                ctx, 400, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            for (id in ids) mgr.updateAppWidget(id, rv)
        }

        /** تحديث الويدجت عند وقت الصلاة الجاية حتى يتأشّر اللي بعدها */
        fun scheduleRefresh(ctx: Context) {
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val offset = AppClock.offset(ctx)
                val (_, nt) = PrayerCalc.next(ctx, System.currentTimeMillis() + offset + 5_000L, false)
                val pi = PendingIntent.getBroadcast(
                    ctx, 401,
                    Intent(ctx, PrayerWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAndAllowWhileIdle(android.app.AlarmManager.RTC, nt - offset + 1_000L, pi)
            } catch (_: Exception) {}
        }
    }
}
