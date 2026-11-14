package com.mosul.salah

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private var tab = 0

    // التقويم
    private var calMonth: YearMonth = YearMonth.now(PrayerCalc.ZONE)
    private var calHijri = false
    private var calSel: LocalDate = LocalDate.now(PrayerCalc.ZONE)

    // الأذكار
    private var dhikrTab = 0
    private val counts = HashMap<String, Int>()

    // الساعة اليدوية (القيم المعروضة قبل الحفظ)
    private var editH = 12
    private var editM = 0
    private var editPm = false

    // عناصر شاشة الأوقات اللي تتحدث كل ثانية
    private var clockView: TextView? = null
    private var countView: TextView? = null
    private var nextTime = 0L
    private var scroll: ScrollView? = null

    private val REQ_AUDIO = 11
    private val REQ_VIDEO = 12
    private val REQ_IMAGE = 13

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.NAV

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = Ui.hbox(this).apply {
            setBackgroundColor(Ui.NAV)
            setPadding(0, Ui.dp(this@MainActivity, 6), 0, Ui.dp(this@MainActivity, 6))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        calSel = PrayerCalc.today(this)
        calMonth = YearMonth.from(calSel)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Scheduler.scheduleNext(this)
        show(0)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ───────────── التنقل ─────────────

    private fun buildNav() {
        nav.removeAllViews()
        val labels = arrayOf("الأوقات", "التقويم", "الأذكار", "الضبط")
        labels.forEachIndexed { i, s ->
            val on = i == tab
            nav.addView(TextView(this).apply {
                text = s
                textSize = 14f
                gravity = Gravity.CENTER
                minHeight = Ui.dp(this@MainActivity, 48)
                setTextColor(if (on) Ui.ACCENT else Ui.MUTED)
                if (on) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setOnClickListener { show(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun show(t: Int) {
        tab = t
        buildNav()
        render(false)
    }

    /** يعيد رسم الشاشة الحالية. keepScroll يحافظ على مكان التمرير */
    private fun render(keepScroll: Boolean) {
        val y = if (keepScroll) scroll?.scrollY ?: 0 else 0
        clockView = null
        countView = null
        content.removeAllViews()
        val body = when (tab) {
            1 -> calendarView()
            2 -> adhkarView()
            3 -> settingsView()
            else -> timesView()
        }
        val sv = ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }
        scroll = sv
        content.addView(sv)
        if (y > 0) sv.post { sv.scrollTo(0, y) }
    }

    private fun page(): LinearLayout = Ui.vbox(this, 12).apply {
        setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24))
    }

    private fun title(s: String): TextView = Ui.text(this, s, 24f, Ui.TEXT, true)

    // ───────────── شاشة الأوقات ─────────────

    private fun timesView(): View {
        val p = page()
        val now = AppClock.now(this)
        val today = PrayerCalc.today(this)

        val head = Ui.hbox(this)
        head.addView(Ui.text(this, "الموصل", 24f, Ui.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        val clock = Ui.text(this, "", 15f, Ui.MUTED)
        clockView = clock
        head.addView(clock)
        p.addView(head)
        p.addView(Ui.text(this, "${Hijri.dayName(today)} ${Hijri.gText(today)}", 15f))
        p.addView(Ui.text(this, Hijri.text(this, today), 15f, Ui.ACCENT))

        val (ni, nt) = PrayerCalc.next(this, now, false)
        nextTime = nt
        val hero = Ui.card(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        hero.addView(Ui.text(this, "الصلاة القادمة", 14f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        hero.addView(Ui.text(this, PrayerCalc.NAMES[ni], 30f, Ui.ACCENT, true).apply { gravity = Gravity.CENTER })
        val cd = Ui.text(this, "", 44f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
            fontFeatureSettings = "tnum"
        }
        countView = cd
        hero.addView(cd)
        hero.addView(Ui.text(this, "متبقي على الأذان · ${PrayerCalc.fmt(nt)}", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        p.addView(hero, LinearLayout.LayoutParams(-1, -2))

        val ts = PrayerCalc.times(this, today)
        val nextToday = ts.indexOfFirst { it == nt }
        for (i in 0 until 6) {
            val on = i == nextToday
            val row = Ui.hbox(this).apply {
                background = Ui.bg(this@MainActivity, if (on) Ui.ACCENT else Ui.ROW, 16)
                setPadding(Ui.dp(this@MainActivity, 18), 0, Ui.dp(this@MainActivity, 18), 0)
                minimumHeight = Ui.dp(this@MainActivity, 52)
            }
            val fg = if (on) Ui.BG else Ui.TEXT
            row.addView(Ui.text(this, PrayerCalc.NAMES[i], 17f, fg, true), LinearLayout.LayoutParams(0, -2, 1f))
            if (!prefs.adhanOn(i)) row.addView(Ui.text(this, "صامت  ", 12f, if (on) Ui.BG else Ui.MUTED))
            row.addView(Ui.text(this, PrayerCalc.fmt(ts[i]), 17f, fg, true).apply {
                textDirection = View.TEXT_DIRECTION_LTR
            })
            p.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        tick()
        return p
    }

    private fun tick() {
        val now = AppClock.now(this)
        clockView?.let { v ->
            val z = Instant.ofEpochMilli(now).atZone(PrayerCalc.ZONE)
            val h12 = if (z.hour % 12 == 0) 12 else z.hour % 12
            v.text = String.format(java.util.Locale.US, "%02d:%02d:%02d %s", h12, z.minute, z.second, if (z.hour < 12) "ص" else "م")
        }
        countView?.let { v ->
            val left = (nextTime - now) / 1000
            if (left < 0) { render(false); return }
            v.text = String.format(java.util.Locale.US, "%02d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60)
        }
    }

    // ───────────── التقويم ─────────────

    private fun calendarView(): View {
        val p = page()
        p.addView(title("التقويم"))
        p.addView(Ui.segment(this, arrayOf("ميلادي", "هجري"), if (calHijri) 1 else 0) {
            calHijri = it == 1; render(true)
        }, LinearLayout.LayoutParams(-1, -2))

        val first = calMonth.atDay(1)
        val last = calMonth.atEndOfMonth()
        val h1 = Hijri.of(this, first)
        val h2 = Hijri.of(this, last)
        val hTitle = if (h1[1] == h2[1]) "${Hijri.MONTHS[h1[1]]} ${h1[2]}"
        else "${Hijri.MONTHS[h1[1]]} – ${Hijri.MONTHS[h2[1]]} ${h2[2]}"
        val gTitle = "${Hijri.G_MONTHS[calMonth.monthValue - 1]} ${calMonth.year}"

        val head = Ui.hbox(this)
        head.addView(Ui.small(this, "›", "الشهر السابق") { calMonth = calMonth.minusMonths(1); render(true) })
        val mid = Ui.vbox(this).apply { gravity = Gravity.CENTER }
        mid.addView(Ui.text(this, if (calHijri) hTitle else gTitle, 18f, Ui.TEXT, true).apply { gravity = Gravity.CENTER })
        mid.addView(Ui.text(this, if (calHijri) gTitle else "$hTitle هـ", 13f, Ui.ACCENT).apply { gravity = Gravity.CENTER })
        head.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.small(this, "‹", "الشهر التالي") { calMonth = calMonth.plusMonths(1); render(true) })
        p.addView(head, LinearLayout.LayoutParams(-1, -2))

        val wk = Ui.hbox(this)
        arrayOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة").forEach {
            wk.addView(Ui.text(this, it, 11f, Ui.MUTED).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        p.addView(wk, LinearLayout.LayoutParams(-1, -2))

        val today = PrayerCalc.today(this)
        val lead = (first.dayOfWeek.value + 1) % 7 // السبت = 0
        val total = lead + calMonth.lengthOfMonth()
        val rows = (total + 6) / 7
        val grid = Ui.vbox(this, 4)
        var day = 1 - lead
        for (r in 0 until rows) {
            val row = Ui.hbox(this)
            for (c in 0 until 7) {
                val lp = LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f).apply {
                    marginStart = Ui.dp(this@MainActivity, 2); marginEnd = Ui.dp(this@MainActivity, 2)
                }
                if (day < 1 || day > calMonth.lengthOfMonth()) {
                    row.addView(View(this), lp)
                } else {
                    val d = calMonth.atDay(day)
                    val h = Hijri.of(this, d)
                    val sel = d == calSel
                    val isToday = d == today
                    val cell = Ui.vbox(this).apply {
                        gravity = Gravity.CENTER
                        background = when {
                            sel -> Ui.bg(this@MainActivity, Ui.ACCENT, 12)
                            isToday -> Ui.bg(this@MainActivity, Ui.ROW, 12, Ui.ACCENT)
                            else -> Ui.bg(this@MainActivity, Ui.ROW, 12)
                        }
                        setOnClickListener { calSel = d; render(true) }
                    }
                    val big = if (calHijri) h[0] else d.dayOfMonth
                    val small = if (calHijri) d.dayOfMonth else h[0]
                    cell.addView(Ui.text(this, big.toString(), 16f, if (sel) Ui.BG else Ui.TEXT, true).apply { gravity = Gravity.CENTER })
                    cell.addView(Ui.text(this, small.toString(), 10f, if (sel) Ui.BG else Ui.MUTED).apply { gravity = Gravity.CENTER })
                    row.addView(cell, lp)
                }
                day++
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        p.addView(grid, LinearLayout.LayoutParams(-1, -2))

        val info = Ui.card(this)
        info.addView(Ui.text(this, Hijri.dayName(calSel) + if (calSel == today) " · اليوم" else "", 13f, Ui.MUTED))
        info.addView(pair("ميلادي", Hijri.gText(calSel), Ui.TEXT))
        info.addView(pair("هجري", Hijri.text(this, calSel), Ui.ACCENT))
        val ts = PrayerCalc.times(this, calSel)
        val line = StringBuilder()
        for (i in 0 until 6) {
            if (i == 1) continue
            if (line.isNotEmpty()) line.append("   ")
            line.append(PrayerCalc.NAMES[i]).append(" ").append(PrayerCalc.fmt(ts[i]).substringBefore(" "))
        }
        info.addView(Ui.text(this, line.toString(), 12f, Ui.MUTED))
        p.addView(info, LinearLayout.LayoutParams(-1, -2))

        p.addView(Ui.button(this, "رجوع لليوم", false) {
            calSel = today; calMonth = YearMonth.from(today); render(false)
        }, LinearLayout.LayoutParams(-1, -2))
        return p
    }

    private fun pair(label: String, value: String, color: Int): View {
        val r = Ui.hbox(this)
        r.addView(Ui.text(this, label, 13f, Ui.MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(Ui.text(this, value, 16f, color, true))
        return r
    }

    // ───────────── الأذكار ─────────────

    private fun adhkarView(): View {
        val p = page()
        p.addView(title("الأذكار"))
        p.addView(Ui.segment(this, Adhkar.TABS, dhikrTab) { dhikrTab = it; render(false) }, LinearLayout.LayoutParams(-1, -2))
        p.addView(Ui.text(this, "دوس على الذكر حتى ينقص العدد", 12f, Ui.MUTED))

        Adhkar.list(dhikrTab).forEachIndexed { i, d ->
            val key = "$dhikrTab-$i"
            val left = counts[key] ?: d.count
            val done = left == 0
            val card = Ui.card(this).apply {
                alpha = if (done) 0.45f else 1f
                setOnClickListener {
                    val cur = counts[key] ?: d.count
                    if (cur > 0) { counts[key] = cur - 1; render(true) }
                }
            }
            if (d.note.isNotEmpty()) card.addView(Ui.text(this, d.note, 12f, Ui.ACCENT, true))
            card.addView(Ui.text(this, d.text, 18f).apply { setLineSpacing(0f, 1.35f) })
            val foot = Ui.hbox(this)
            foot.addView(Ui.text(this, "التكرار: ${d.count}", 12f, Ui.MUTED), LinearLayout.LayoutParams(0, -2, 1f))
            foot.addView(TextView(this).apply {
                text = if (done) "تم ✓" else left.toString()
                textSize = 16f
                gravity = Gravity.CENTER
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Ui.BG)
                background = Ui.bg(this@MainActivity, Ui.ACCENT, 20)
                minWidth = Ui.dp(this@MainActivity, 56)
                minHeight = Ui.dp(this@MainActivity, 36)
                setPadding(Ui.dp(this@MainActivity, 10), 0, Ui.dp(this@MainActivity, 10), 0)
            })
            card.addView(foot, LinearLayout.LayoutParams(-1, -2))
            p.addView(card, LinearLayout.LayoutParams(-1, -2))
        }
        p.addView(Ui.button(this, "إعادة العداد", false) {
            counts.keys.filter { it.startsWith("$dhikrTab-") }.forEach { counts.remove(it) }
            render(false)
        }, LinearLayout.LayoutParams(-1, -2))
        return p
    }

    // ───────────── الضبط ─────────────

    private fun settingsView(): View {
        val p = page()
        p.addView(title("الضبط"))

        // ١) عداد الساعة
        val clock = Ui.card(this)
        val ch = Ui.hbox(this)
        ch.addView(Ui.text(this, "عداد الساعة", 16f, Ui.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        ch.addView(Ui.segment(this, arrayOf("تلقائي", "يدوي"), if (prefs.manualClock) 1 else 0) {
            if (it == 0) {
                prefs.manualClock = false
                Scheduler.scheduleNext(this); toast("رجعت ساعة الجهاز")
            } else {
                loadEditClock(); prefs.manualClock = true
                Scheduler.scheduleNext(this)
            }
            render(true)
        }, LinearLayout.LayoutParams(Ui.dp(this, 170), -2))
        clock.addView(ch)
        if (prefs.manualClock) {
            val r = Ui.hbox(this).apply { gravity = Gravity.CENTER }
            r.layoutDirection = View.LAYOUT_DIRECTION_LTR
            r.addView(spin(String.format("%02d", editH), "الساعة",
                { editH = editH % 12 + 1; render(true) }, { editH = if (editH == 1) 12 else editH - 1; render(true) }))
            r.addView(Ui.text(this, " : ", 34f, Ui.MUTED, true))
            r.addView(spin(String.format("%02d", editM), "الدقائق",
                { editM = (editM + 1) % 60; render(true) }, { editM = (editM + 59) % 60; render(true) }))
            r.addView(Ui.button(this, if (editPm) "م" else "ص", false) { editPm = !editPm; render(true) },
                LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 48)).apply { marginStart = Ui.dp(this@MainActivity, 14) })
            clock.addView(r, LinearLayout.LayoutParams(-1, -2))
            clock.addView(Ui.button(this, "حفظ الوقت", true) { saveManualClock() }, LinearLayout.LayoutParams(-1, -2))
            clock.addView(Ui.text(this, "العداد يمشي من الوقت اللي تضبطه أنت", 12f, Ui.MUTED))
        } else {
            clock.addView(Ui.text(this, "الوقت مأخوذ من ساعة الجهاز", 12f, Ui.MUTED))
        }
        p.addView(clock, LinearLayout.LayoutParams(-1, -2))

        // ٢) صوت الأذان
        val snd = Ui.card(this)
        snd.addView(Ui.text(this, "صوت الأذان", 16f, Ui.TEXT, true))
        val cur = if (prefs.mediaPath.isEmpty()) "الحالي: صوت المنبه الافتراضي"
        else "الحالي: ${prefs.mediaName}" + if (prefs.mediaIsVideo) " (فيديو ← صوت بس)" else ""
        snd.addView(Ui.text(this, cur, 13f, Ui.ACCENT))
        val sb = Ui.hbox(this)
        sb.addView(Ui.button(this, "ملف صوت", false) { pick("audio/*", REQ_AUDIO) }, LinearLayout.LayoutParams(0, -2, 1f))
        sb.addView(View(this), LinearLayout.LayoutParams(Ui.dp(this, 8), 1))
        sb.addView(Ui.button(this, "ملف فيديو", false) { pick("video/*", REQ_VIDEO) }, LinearLayout.LayoutParams(0, -2, 1f))
        snd.addView(sb, LinearLayout.LayoutParams(-1, -2))
        snd.addView(Ui.text(this, "إذا اخترت فيديو، يشتغل صوته بس بدون صورة", 12f, Ui.MUTED))
        if (prefs.mediaPath.isNotEmpty()) {
            snd.addView(Ui.button(this, "رجوع للصوت الافتراضي", false) {
                File(prefs.mediaPath).delete(); prefs.mediaPath = ""; prefs.mediaName = ""; prefs.mediaIsVideo = false
                render(true)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        p.addView(snd, LinearLayout.LayoutParams(-1, -2))

        // ٣) صورة الأذان
        val im = Ui.card(this)
        im.addView(Ui.text(this, "صورة شاشة الأذان", 16f, Ui.TEXT, true))
        im.addView(Ui.text(this, "ثابتة أو متحركة (GIF) — تطلع على واجهة الجوال ويا عبارة «حان الآن موعد أذان …»", 12f, Ui.MUTED))
        if (prefs.imagePath.isNotEmpty() && File(prefs.imagePath).exists()) {
            val bmp = AdhanService.loadBitmap(prefs.imagePath, 600)
            if (bmp != null) {
                im.addView(ImageView(this).apply {
                    setImageBitmap(bmp); adjustViewBounds = true; scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "صورة الأذان"
                }, LinearLayout.LayoutParams(-1, Ui.dp(this, 160)))
            }
            im.addView(Ui.text(this, prefs.imageName, 12f, Ui.ACCENT))
        }
        val ib = Ui.hbox(this)
        ib.addView(Ui.button(this, "اختيار صورة", false) { pick("image/*", REQ_IMAGE) }, LinearLayout.LayoutParams(0, -2, 1f))
        if (prefs.imagePath.isNotEmpty()) {
            ib.addView(View(this), LinearLayout.LayoutParams(Ui.dp(this, 8), 1))
            ib.addView(Ui.button(this, "حذف الصورة", false) {
                File(prefs.imagePath).delete(); prefs.imagePath = ""; prefs.imageName = ""; render(true)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        im.addView(ib, LinearLayout.LayoutParams(-1, -2))
        p.addView(im, LinearLayout.LayoutParams(-1, -2))

        p.addView(Ui.button(this, "تجربة الأذان (العصر)", true) {
            val s = Intent(this, AdhanService::class.java).putExtra(Scheduler.EXTRA_PRAYER, 3)
            startForegroundService(s)
            startActivity(Intent(this, AdhanActivity::class.java).putExtra(Scheduler.EXTRA_PRAYER, 3))
        }, LinearLayout.LayoutParams(-1, -2))

        // ٤) تعديل الأوقات
        val off = Ui.card(this)
        val oh = Ui.hbox(this)
        oh.addView(Ui.text(this, "تعديل أوقات الصلاة", 16f, Ui.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        oh.addView(Ui.text(this, "بالدقائق · والأذان", 12f, Ui.MUTED))
        off.addView(oh)
        val ts = PrayerCalc.times(this, PrayerCalc.today(this))
        for (i in 0 until 6) {
            val r = Ui.hbox(this)
            val col = Ui.vbox(this)
            col.addView(Ui.text(this, PrayerCalc.NAMES[i], 15f, Ui.TEXT, true))
            col.addView(Ui.text(this, PrayerCalc.fmt(ts[i]), 12f, Ui.MUTED))
            r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            val on = prefs.adhanOn(i)
            r.addView(TextView(this).apply {
                text = if (on) "أذان" else "صامت"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (on) Ui.BG else Ui.MUTED)
                background = if (on) Ui.bg(this@MainActivity, Ui.ACCENT, 10) else Ui.bg(this@MainActivity, Ui.ROW, 10, Ui.LINE)
                minWidth = Ui.dp(this@MainActivity, 52); minHeight = Ui.dp(this@MainActivity, 36)
                setOnClickListener { prefs.setAdhanOn(i, !on); Scheduler.scheduleNext(this@MainActivity); render(true) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Ui.dp(this@MainActivity, 10) })
            val v = prefs.offset(i)
            r.addView(Ui.small(this, "+", "زيادة دقيقة") { setOff(i, v + 1) })
            r.addView(Ui.text(this, (if (v > 0) "+" else "") + v, 16f, if (v == 0) Ui.TEXT else Ui.ACCENT, true).apply {
                gravity = Gravity.CENTER; minWidth = Ui.dp(this@MainActivity, 40)
            })
            r.addView(Ui.small(this, "−", "إنقاص دقيقة") { setOff(i, v - 1) })
            off.addView(r, LinearLayout.LayoutParams(-1, -2))
        }
        p.addView(off, LinearLayout.LayoutParams(-1, -2))

        // ٥) طريقة الحساب
        val m = Ui.card(this)
        m.addView(Ui.text(this, "طريقة الحساب", 16f, Ui.TEXT, true))
        m.addView(Ui.segment(this, arrayOf("الرابطة", "أم القرى", "المصرية"), prefs.method) {
            prefs.method = it; Scheduler.scheduleNext(this); render(true)
        }, LinearLayout.LayoutParams(-1, -2))
        m.addView(Ui.text(this, PrayerCalc.METHODS[prefs.method], 12f, Ui.MUTED))
        m.addView(Ui.text(this, "وقت العصر", 14f, Ui.TEXT, true))
        m.addView(Ui.segment(this, arrayOf("شافعي", "حنفي"), prefs.asrFactor - 1) {
            prefs.asrFactor = it + 1; Scheduler.scheduleNext(this); render(true)
        }, LinearLayout.LayoutParams(-1, -2))
        p.addView(m, LinearLayout.LayoutParams(-1, -2))

        // ٦) تصحيح الهجري
        val hj = Ui.card(this)
        val hr = Ui.hbox(this)
        val hc = Ui.vbox(this)
        hc.addView(Ui.text(this, "تصحيح التاريخ الهجري", 15f, Ui.TEXT, true))
        hc.addView(Ui.text(this, Hijri.text(this, PrayerCalc.today(this)), 12f, Ui.ACCENT))
        hr.addView(hc, LinearLayout.LayoutParams(0, -2, 1f))
        val a = prefs.hijriAdjust
        hr.addView(Ui.small(this, "+", "زيادة يوم") { prefs.hijriAdjust = minOf(2, a + 1); render(true) })
        hr.addView(Ui.text(this, if (a == 0) "0" else (if (a > 0) "+" else "") + a, 15f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER; minWidth = Ui.dp(this@MainActivity, 40)
        })
        hr.addView(Ui.small(this, "−", "إنقاص يوم") { prefs.hijriAdjust = maxOf(-2, a - 1); render(true) })
        hj.addView(hr, LinearLayout.LayoutParams(-1, -2))
        p.addView(hj, LinearLayout.LayoutParams(-1, -2))

        // ٧) أذونات حتى تطلع شاشة الأذان
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                p.addView(Ui.button(this, "اسمح بظهور شاشة الأذان على الواجهة", false) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                    } catch (e: Exception) { openAppSettings() }
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        p.addView(Ui.button(this, "إعدادات التطبيق (الإشعارات والبطارية)", false) { openAppSettings() },
            LinearLayout.LayoutParams(-1, -2))
        p.addView(Ui.text(this, "مهم: خلي البطارية «بدون قيود» لهذا التطبيق حتى الأذان يشتغل بوقته", 12f, Ui.MUTED))
        return p
    }

    private fun spin(value: String, desc: String, up: () -> Unit, down: () -> Unit): View {
        val c = Ui.vbox(this, 4).apply { gravity = Gravity.CENTER_HORIZONTAL }
        c.addView(Ui.small(this, "+", "زيادة $desc", up))
        c.addView(Ui.text(this, value, 38f, Ui.TEXT, true).apply { gravity = Gravity.CENTER })
        c.addView(Ui.small(this, "−", "إنقاص $desc", down))
        return c
    }

    private fun setOff(i: Int, v: Int) {
        prefs.setOffset(i, v.coerceIn(-30, 30))
        Scheduler.scheduleNext(this)
        render(true)
    }

    private fun loadEditClock() {
        val z = Instant.ofEpochMilli(AppClock.now(this)).atZone(PrayerCalc.ZONE)
        editH = if (z.hour % 12 == 0) 12 else z.hour % 12
        editM = z.minute
        editPm = z.hour >= 12
    }

    private fun saveManualClock() {
        val sys = System.currentTimeMillis()
        val zs = Instant.ofEpochMilli(sys).atZone(PrayerCalc.ZONE)
        val h24 = (editH % 12) + if (editPm) 12 else 0
        val target = zs.withHour(h24).withMinute(editM).toInstant().toEpochMilli()
        var diff = target - sys
        val day = 86_400_000L
        if (diff > day / 2) diff -= day
        if (diff < -day / 2) diff += day
        prefs.clockOffset = diff
        prefs.manualClock = true
        Scheduler.scheduleNext(this)
        toast("انحفظ الوقت")
        render(true)
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ───────────── اختيار الملفات ─────────────

    private fun pick(mime: String, req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, req)
        } catch (e: Exception) {
            toast("ما لكيت تطبيق ملفات")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != Activity.RESULT_OK) return
        val name = displayName(uri)
        toast("جاري النسخ…")
        Thread {
            val target = File(filesDir, if (requestCode == REQ_IMAGE) "adhan_image_${System.currentTimeMillis()}" else "adhan_media_${System.currentTimeMillis()}")
            val ok = try {
                contentResolver.openInputStream(uri)?.use { inp ->
                    target.outputStream().use { out -> inp.copyTo(out) }
                }
                target.length() > 0
            } catch (e: Exception) { false }
            runOnUiThread {
                if (!ok) { target.delete(); toast("ما گدرت أقرا الملف"); return@runOnUiThread }
                if (requestCode == REQ_IMAGE) {
                    if (prefs.imagePath.isNotEmpty()) File(prefs.imagePath).delete()
                    prefs.imagePath = target.absolutePath
                    prefs.imageName = name
                } else {
                    if (prefs.mediaPath.isNotEmpty()) File(prefs.mediaPath).delete()
                    prefs.mediaPath = target.absolutePath
                    prefs.mediaName = name
                    prefs.mediaIsVideo = requestCode == REQ_VIDEO
                }
                toast("تم ✓")
                if (tab == 3) render(true)
            }
        }.start()
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "ملف"
            }
        } catch (_: Exception) {}
        return "ملف"
    }
}
