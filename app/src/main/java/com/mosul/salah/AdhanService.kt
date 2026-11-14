package com.mosul.salah

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.io.File

/** يشغل صوت الأذان (من ملف صوت أو صوت فيديو) ويطلع الإشعار */
class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL = "adhan_channel"
        const val NOTIF_ID = 77
        const val ACTION_STOP = "com.mosul.salah.STOP"
        const val ACTION_TEST = "com.mosul.salah.TEST"
        var running = false

        fun title(idx: Int): String =
            if (idx == 1) "حان الآن وقت الشروق" else "حان الآن موعد أذان ${PrayerCalc.NAMES[idx]}"

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "إشعار وقت الأذان"
                ch.setSound(null, null)
                ch.enableVibration(true)
                ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                nm.createNotificationChannel(ch)
            }
        }

        fun loadBitmap(path: String, maxSide: Int): Bitmap? {
            if (path.isEmpty() || !File(path).exists()) return null
            return try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var s = 1
                while (o.outWidth / s > maxSide || o.outHeight / s > maxSide) s *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (e: Throwable) { null }
        }

        fun build(ctx: Context, idx: Int, withStop: Boolean): Notification {
            ensureChannel(ctx)
            val full = Intent(ctx, AdhanActivity::class.java)
                .putExtra(Scheduler.EXTRA_PRAYER, idx)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fullPi = PendingIntent.getActivity(
                ctx, 200 + idx, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val b = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title(idx))
                .setContentText("الله أكبر الله أكبر · ${PrayerCalc.fmt(AppClock.now(ctx))}")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .setAutoCancel(true)
            val bmp = loadBitmap(Prefs(ctx).imagePath, 1024)
            if (bmp != null) {
                b.setLargeIcon(bmp)
                b.setStyle(Notification.BigPictureStyle().bigPicture(bmp).setSummaryText(title(idx)))
            }
            if (withStop) {
                val stop = Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP)
                val stopPi = PendingIntent.getService(
                    ctx, 300, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                b.addAction(Notification.Action.Builder(null, "إيقاف الأذان", stopPi).build())
                b.setDeleteIntent(stopPi)
            }
            return b.build()
        }

        /** احتياط: إشعار بدون خدمة */
        fun plainNotify(ctx: Context, idx: Int) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID + 1, build(ctx, idx, false))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return Service.START_NOT_STICKY
        }
        val idx = intent?.getIntExtra(Scheduler.EXTRA_PRAYER, 0) ?: 0
        startForeground(NOTIF_ID, build(this, idx, true))
        running = true

        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "salah:adhan").apply { acquire(15 * 60_000L) }

        play()
        // أمان: يوقف بعد 15 دقيقة كحد أقصى
        handler.postDelayed({ stopAll() }, 15 * 60_000L)
        return Service.START_NOT_STICKY
    }

    private fun play() {
        player?.release()
        val p = Prefs(this)
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        try {
            if (p.mediaPath.isNotEmpty() && File(p.mediaPath).exists()) {
                // إذا الملف فيديو: ما نعطيه شاشة، فيشتغل الصوت بس
                mp.setDataSource(p.mediaPath)
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mp.setDataSource(this, uri)
            }
            mp.setOnCompletionListener { stopAll() }
            mp.setOnErrorListener { _, _, _ -> stopAll(); true }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            mp.release()
            player = null
        }
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) {}
        running = false
        sendBroadcast(Intent(AdhanActivity.ACTION_CLOSE).setPackage(packageName))
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        running = false
        super.onDestroy()
    }
}
