package com.mosul.salah

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * التحديث من داخل التطبيق: يشوف آخر نسخة بـ Releases على GitHub،
 * وإذا أحدث من المنصّبة ينزّلها ويثبتها (أندرويد يرفضها إذا مو موقّعة بنفس المفتاح).
 */
object Updater {
    private const val API = "https://api.github.com/repos/p2p37mx/mosul-salah/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    class Release(val build: Long, val tag: String, val url: String, val size: Long)

    /** رقم البناء المنصّب (يساوي رقم بناء GitHub) */
    fun installedBuild(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    /** يفحص آخر نسخة. يرجع النتيجة على الواجهة */
    fun check(onResult: (Release?, String?) -> Unit) {
        Thread {
            try {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 15_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val code = c.responseCode
                if (code != 200) {
                    c.disconnect()
                    main.post { onResult(null, "ما گدرت أوصل لـ GitHub (رمز $code)") }
                    return@Thread
                }
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val j = JSONObject(body)
                val tag = j.optString("tag_name")
                val build = tag.substringAfter("build-", "").toLongOrNull() ?: 0L
                val assets = j.optJSONArray("assets")
                var url = ""
                var size = 0L
                if (assets != null) {
                    for (k in 0 until assets.length()) {
                        val a = assets.getJSONObject(k)
                        if (a.optString("name").endsWith(".apk")) {
                            url = a.optString("browser_download_url"); size = a.optLong("size"); break
                        }
                    }
                }
                // ننزّل بس من GitHub
                if (url.isEmpty() || !url.startsWith("https://github.com/p2p37mx/mosul-salah/")) {
                    main.post { onResult(null, "ما لكيت ملف التحديث") }
                    return@Thread
                }
                main.post { onResult(Release(build, tag, url, size), null) }
            } catch (e: Exception) {
                main.post { onResult(null, "تأكد من الإنترنت وحاول مرة ثانية") }
            }
        }.start()
    }

    /** ينزّل ويثبت. onProgress بالنسبة المئوية */
    fun install(ctx: Context, r: Release, onProgress: (Int) -> Unit, onError: (String) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            var session: PackageInstaller.Session? = null
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(app.packageName)
                val id = installer.createSession(params)
                val s = installer.openSession(id)
                session = s

                val c = URL(r.url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.connectTimeout = 20_000
                c.readTimeout = 30_000
                val total = if (c.contentLengthLong > 0) c.contentLengthLong else r.size
                c.inputStream.use { input ->
                    s.openWrite("MosulSalah.apk", 0, if (total > 0) total else -1).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) { last = pct; main.post { onProgress(pct) } }
                            }
                        }
                        s.fsync(out)
                    }
                }
                c.disconnect()

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(app, 700, Intent(app, InstallReceiver::class.java), flags)
                s.commit(pi.intentSender)
                s.close()
            } catch (e: Exception) {
                try { session?.abandon() } catch (_: Exception) {}
                main.post { onError("فشل التنزيل، حاول مرة ثانية") }
            }
        }.start()
    }
}

/** يستلم نتيجة التثبيت: يطلب موافقتك، أو يبين الخطأ */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { ctx.startActivity(confirm) } catch (_: Exception) {}
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(ctx, "تم التحديث ✓", Toast.LENGTH_SHORT).show()
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(ctx, "ما تم التحديث $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
