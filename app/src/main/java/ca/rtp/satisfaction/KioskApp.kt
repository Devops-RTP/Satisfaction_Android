package ca.rtp.satisfaction

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class KioskApp : Application() {

    lateinit var queue: LocalQueue
        private set
    lateinit var uploader: Uploader
        private set
    @Volatile var config: KioskConfig? = null
        private set

    override fun onCreate() {
        super.onCreate()
        queue = LocalQueue(this)
        val cfg = reloadConfig()
        uploader = Uploader(queue, cfg).also { it.start() }
    }

    fun reloadConfig(): KioskConfig {
        val cfg = KioskConfig.load(this)
        config = cfg
        if (::uploader.isInitialized) {
            uploader.config = cfg
            uploader.poke()
        }
        return cfg
    }

    /** Called on the UI thread when a face is tapped. Fast local write; upload happens in background. */
    fun record(rating: Int) {
        val cfg = config ?: reloadConfig()
        val now = Date()
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val local = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val r = SurveyResponse(
            responseId = UUID.randomUUID().toString(),
            rating = rating,
            createdUtc = utc.format(now),
            createdLocal = local.format(now),
            siteId = cfg.siteId,
            deviceId = cfg.deviceId,
        )
        Thread {
            try {
                queue.add(r)
                if (cfg.csvBackup) appendCsv(r)
            } catch (e: Exception) {
                Log.e("KioskApp", "Failed to store response", e)
            }
            uploader.poke()
        }.start()
    }

    @Synchronized
    private fun appendCsv(r: SurveyResponse) {
        val f = File(KioskConfig.configDir(this), "responses.csv")
        val header = !f.exists()
        f.appendText(buildString {
            if (header) append("response_id,rating,created_utc,created_local,site_id,device_id\n")
            append("${r.responseId},${r.rating},${r.createdUtc},${r.createdLocal},")
            append("${csv(r.siteId)},${csv(r.deviceId)}\n")
        })
    }

    private fun csv(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' })
        "\"" + s.replace("\"", "\"\"") + "\"" else s
}
