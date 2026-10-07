package com.kwan.gpsratemonitor

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int, val versionName: String, val apkUrl: String, val sha256: String,
    val mandatory: Boolean, val releaseNotes: List<String>, val isUpdateAvailable: Boolean, val error: String? = null
)

object UpdateManager {
    private const val UPDATE_URL = "https://raw.githubusercontent.com/kwanene08-ux/GPSRateMonitor/main/latest.json"
    private const val CURRENT_VERSION_CODE = 11
    private const val PACKAGE_NAME = "com.kwan.gpsratemonitor"

    suspend fun check(): UpdateInfo = try {
        val o = JSONObject(fetch(UPDATE_URL))
        val code = o.getInt("versionCode")
        UpdateInfo(code, o.getString("versionName"), o.getString("apkUrl"), o.getString("sha256").lowercase(),
            o.optBoolean("mandatory"), buildList {
                o.optJSONArray("releaseNotes")?.let { a -> for (i in 0 until a.length()) add(a.getString(i)) }
            }, code > CURRENT_VERSION_CODE)
    } catch (e: Exception) {
        UpdateInfo(0,"","","",false,emptyList(),false,e.message ?: "ตรวจสอบไม่สำเร็จ")
    }

    suspend fun downloadAndVerify(context: Context, info: UpdateInfo, progress: (Int)->Unit): Result =
        try {
            require(info.versionCode > CURRENT_VERSION_CODE) { "เวอร์ชันใหม่ไม่สูงกว่าเวอร์ชันปัจจุบัน" }
            require(info.apkUrl.startsWith("https://")) { "ต้องใช้ HTTPS" }
            val part = File(context.cacheDir, "gps-update-${info.versionCode}.apk.part")
            val apk = File(context.cacheDir, "gps-update-${info.versionCode}.apk")
            part.delete(); apk.delete()
            val c = URL(info.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.connect()
            require(c.responseCode in 200..299) { "HTTP ${c.responseCode}" }
            val total = c.contentLengthLong
            c.inputStream.use { input -> part.outputStream().use { out ->
                val b = ByteArray(65536); var done=0L
                while (true) { val n=input.read(b); if(n<0) break; out.write(b,0,n); done+=n; if(total>0) progress((done*100/total).toInt()) }
            }}
            c.disconnect()
            require(sha256(part).equals(info.sha256, true)) { "SHA-256 ไม่ตรง" }
            require(part.renameTo(apk)) { "เตรียม APK ไม่สำเร็จ" }
            Result(true, null)
        } catch(e: Exception) { Result(false, e.message ?: "ดาวน์โหลดล้มเหลว") }

    fun install(context: Context) {
        val f = context.cacheDir.listFiles()?.firstOrNull { it.name.startsWith("gps-update-") && it.name.endsWith(".apk") }
            ?: return
        val uri = FileProvider.getUriForFile(context, "$PACKAGE_NAME.fileprovider", f)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun fetch(url:String):String {
        require(url.startsWith("https://"))
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=10000;c.readTimeout=15000;c.connect()
        require(c.responseCode in 200..299){"HTTP ${c.responseCode}"}
        return c.inputStream.bufferedReader().use{it.readText()}.also{c.disconnect()}
    }
    private fun sha256(f:File):String {
        val md=MessageDigest.getInstance("SHA-256")
        f.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}}
        return md.digest().joinToString(""){"%02x".format(it)}
    }
    data class Result(val ok:Boolean,val error:String?)
}
