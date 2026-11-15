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
        scheduleReminder(ctx)
    }

    /** إشعار «لا تنسى الأذكار» بعد كل صلاة بكم دقيقة */
    fun scheduleReminder(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = Prefs(ctx)
        val i = Intent(ctx, ReminderReceiver::class.java)
        val pi0 = PendingIntent.getBroadcast(
            ctx, 500, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!p.reminderOn) { am.cancel(pi0); return }
        val offset = AppClock.offset(ctx)
        val appNow = System.currentTimeMillis() + offset
        val delay = p.reminderMin * 60_000L
        // الصلاة اللي تذكيرها بعده ما صار (الشروق متخطّى)
        val (idx, t) = PrayerCalc.next(ctx, appNow - delay + 30_000L, false)
        val trigger = t + delay - offset
        i.putExtra(EXTRA_PRAYER, idx)
        val pi = PendingIntent.getBroadcast(
            ctx, 500, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
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

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val idx = intent.getIntExtra(Scheduler.EXTRA_PRAYER, 0)
        try {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            if (nm.getNotificationChannel("adhkar_channel") == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(
                        "adhkar_channel", "تذكير الأذكار", android.app.NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "تذكير أذكار بعد الصلاة" }
                )
            }
            val open = Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, 2)
                .putExtra(MainActivity.EXTRA_DHIKR_TAB, 2)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                ctx, 501, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = android.app.Notification.Builder(ctx, "adhkar_channel")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("لا تنسى أذكار بعد الصلاة")
                .setContentText("تقبّل الله صلاة ${PrayerCalc.NAMES[idx]} · دوس حتى تفتح الأذكار")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(600, n)
        } catch (_: Exception) {}
        Scheduler.scheduleReminder(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleNext(ctx)
    }
}
