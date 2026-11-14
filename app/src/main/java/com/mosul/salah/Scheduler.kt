package com.mosul.salah

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** يجدول منبه الأذان الجاي */
object Scheduler {
    const val EXTRA_PRAYER = "prayer"

    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val offset = AppClock.offset(ctx)
        val appNow = System.currentTimeMillis() + offset
        val (idx, appTime) = PrayerCalc.next(ctx, appNow + 30_000L, true)
        // وقت الأذان حسب ساعة التطبيق، نحوله لساعة الجهاز
        val trigger = appTime - offset

        val i = Intent(ctx, AdhanReceiver::class.java).putExtra(EXTRA_PRAYER, idx)
        val pi = PendingIntent.getBroadcast(
            ctx, 100, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            ctx, 101, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactOk) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
        PrayerWidget.updateAll(ctx)
        PrayerWidget.scheduleRefresh(ctx)
    }
}

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val idx = intent.getIntExtra(Scheduler.EXTRA_PRAYER, 0)
        val s = Intent(ctx, AdhanService::class.java).putExtra(Scheduler.EXTRA_PRAYER, idx)
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(s) else ctx.startService(s)
        } catch (e: Exception) {
            AdhanService.plainNotify(ctx, idx)
        }
        Scheduler.scheduleNext(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleNext(ctx)
    }
}
