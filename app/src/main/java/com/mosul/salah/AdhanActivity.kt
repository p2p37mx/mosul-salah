package com.mosul.salah

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** شاشة الأذان: تطلع على واجهة الجوال حتى لو مقفول، بيها الصورة والعبارة */
class AdhanActivity : Activity() {
    companion object {
        const val ACTION_CLOSE = "com.mosul.salah.CLOSE_ADHAN"
    }

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 27) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        render(intent)

        val f = IntentFilter(ACTION_CLOSE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(closer, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(closer, f)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val idx = intent.getIntExtra(Scheduler.EXTRA_PRAYER, 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Ui.BG)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(Ui.dp(this@AdhanActivity, 24), Ui.dp(this@AdhanActivity, 40), Ui.dp(this@AdhanActivity, 24), Ui.dp(this@AdhanActivity, 40))
        }

        val img = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val path = Prefs(this).imagePath
        var hasImage = false
        if (path.isNotEmpty() && File(path).exists()) {
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    val src = ImageDecoder.createSource(File(path))
                    val d = ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                        val side = maxOf(info.size.width, info.size.height)
                        if (side > 1600) decoder.setTargetSampleSize(side / 1600 + 1)
                    }
                    img.setImageDrawable(d)
                    if (d is AnimatedImageDrawable) {
                        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        d.start()
                    }
                    hasImage = true
                } else {
                    val bmp = AdhanService.loadBitmap(path, 1600)
                    if (bmp != null) { img.setImageBitmap(bmp); hasImage = true }
                }
            } catch (e: Throwable) {
                hasImage = false
            }
        }
        if (hasImage) {
            root.addView(img, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = Ui.dp(this@AdhanActivity, 24)
            })
        } else {
            root.addView(TextView(this).apply {
                text = "☾"
                textSize = 96f
                setTextColor(Ui.ACCENT)
                gravity = Gravity.CENTER
            })
        }

        root.addView(TextView(this).apply {
            text = AdhanService.title(idx)
            textSize = 28f
            setTextColor(Ui.ACCENT)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "${PrayerCalc.fmt(AppClock.now(this@AdhanActivity))} · الموصل"
            textSize = 16f
            setTextColor(Ui.MUTED)
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@AdhanActivity, 8), 0, Ui.dp(this@AdhanActivity, 28))
        })
        root.addView(Ui.button(this, "إيقاف الأذان", true) {
            startService(Intent(this, AdhanService::class.java).setAction(AdhanService.ACTION_STOP))
            finish()
        }, LinearLayout.LayoutParams(-1, Ui.dp(this, 56)))
        setContentView(root)
    }

    override fun onDestroy() {
        try { unregisterReceiver(closer) } catch (_: Exception) {}
        super.onDestroy()
    }
}
