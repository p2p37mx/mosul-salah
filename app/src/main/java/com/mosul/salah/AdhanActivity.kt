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
        Ui.applyTheme(Prefs(this).darkTheme)
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
        val ctx = this
        val white = android.graphics.Color.WHITE
        val gold = android.graphics.Color.parseColor("#F0C979")

        // الصورة بملء الشاشة
        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0D1B1E"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val path = Prefs(this).imagePath
        var hasImage = false
        if (path.isNotEmpty() && File(path).exists()) {
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    val src = ImageDecoder.createSource(File(path))
                    val d = ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                        val side = maxOf(info.size.width, info.size.height)
                        if (side > 2000) decoder.setTargetSampleSize(side / 2000 + 1)
                    }
                    img.setImageDrawable(d)
                    if (d is AnimatedImageDrawable) {
                        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        d.start()
                    }
                    hasImage = true
                } else {
                    val bmp = AdhanService.loadBitmap(path, 2000)
                    if (bmp != null) { img.setImageBitmap(bmp); hasImage = true }
                }
            } catch (e: Throwable) {
                hasImage = false
            }
        }
        if (!hasImage) img.setImageResource(R.drawable.bg_hadba)
        root.addView(img, android.widget.FrameLayout.LayoutParams(-1, -1))

        // تظليل تحت حتى الكتابة تبين
        root.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Color.argb(40, 0, 0, 0), android.graphics.Color.argb(215, 0, 0, 0))
            )
        }, android.widget.FrameLayout.LayoutParams(-1, -1))

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(ctx, 24), 0, Ui.dp(ctx, 24), Ui.dp(ctx, 48))
        }
        fun shade(t: TextView): TextView { t.setShadowLayer(10f, 0f, 2f, android.graphics.Color.argb(220, 0, 0, 0)); return t }
        box.addView(shade(TextView(this).apply {
            text = AdhanService.title(idx)
            textSize = 30f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }))
        box.addView(shade(TextView(this).apply {
            text = "${PrayerCalc.fmt(AppClock.now(ctx))} · الموصل"
            textSize = 17f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8))
        }))
        box.addView(shade(TextView(this).apply {
            text = "زر الصوت أو زر القفل يوقف الأذان"
            textSize = 13f
            setTextColor(android.graphics.Color.argb(220, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, Ui.dp(ctx, 22))
        }))
        box.addView(TextView(this).apply {
            text = "إيقاف الأذان"
            textSize = 17f
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#0D1B1E"))
            background = Ui.bg(ctx, gold, 28)
            setOnClickListener { stopAdhan() }
        }, LinearLayout.LayoutParams(-1, Ui.dp(this, 56)))
        root.addView(box, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        hideBars()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun stopAdhan() {
        startService(Intent(this, AdhanService::class.java).setAction(AdhanService.ACTION_STOP))
        finish()
    }

    /** أزرار الصوت توقف الأذان وهاي الشاشة مفتوحة */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            stopAdhan()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        try { unregisterReceiver(closer) } catch (_: Exception) {}
        super.onDestroy()
    }
}
