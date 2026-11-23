package ca.rtp.satisfaction

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.util.Log
import java.io.File

/** Typed view of kiosk.ini. Reloaded on every resume and from the admin menu. */
data class KioskConfig(
    val ini: IniFile,
    val siteId: String,
    val deviceId: String,
    val adminPin: String,
    val orientation: String,
    val thankYouSeconds: Int,
    val question: String,
    val questionSecondary: String,
    val thankYou: String,
    val thankYouSecondary: String,
    val showLabels: Boolean,
    val labels: List<String>,
    val backgroundColor: Int,
    val textColor: Int,
    val dbType: String,
    val host: String,
    val port: Int,
    val database: String,
    val instance: String,
    val username: String,
    val password: String,
    val table: String,
    val useSsl: Boolean,
    val url: String,
    val apiKeyHeader: String,
    val apiKey: String,
    val retrySeconds: Int,
    val batchSize: Int,
    val csvBackup: Boolean,
    val lockDevice: Boolean,
    val screenPinning: Boolean,
    val keepAwake: Boolean,
    val loadError: String?,
) {
    companion object {
        private const val TAG = "KioskConfig"
        const val FILE_NAME = "kiosk.ini"

        fun configDir(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir

        fun configFile(ctx: Context): File = File(configDir(ctx), FILE_NAME)

        /** Copies the bundled default INI out on first run, then parses whatever is on disk. */
        fun load(ctx: Context): KioskConfig {
            val file = configFile(ctx)
            var error: String? = null
            val ini = try {
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    ctx.assets.open(FILE_NAME).use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                IniFile.load(file)
            } catch (e: Exception) {
                Log.e(TAG, "Could not read $file, using built-in defaults", e)
                error = "Could not read kiosk.ini: ${e.message}"
                ctx.assets.open(FILE_NAME).use { IniFile.parse(it.readBytes().toString(Charsets.UTF_8)) }
            }
            return from(ini, androidId(ctx), error)
        }

        /** Writes new INI text safely (temp file + rename) so a crash can't leave a half-written file. */
        fun save(ctx: Context, text: String) {
            val file = configFile(ctx)
            val tmp = File(file.parentFile, FILE_NAME + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }

        fun fromText(ctx: Context, text: String): KioskConfig = from(IniFile.parse(text), androidId(ctx), null)

        @SuppressLint("HardwareIds")
        private fun androidId(ctx: Context): String =
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        private fun color(value: String, fallback: Int): Int =
            try { Color.parseColor(value) } catch (e: Exception) { fallback }

        fun from(ini: IniFile, androidId: String, error: String?): KioskConfig {
            val type = ini.get("database", "type", "none").lowercase()
            val defaultPort = when (type) { "mssql" -> 1433; "mysql" -> 3306; "postgresql" -> 5432; else -> 0 }
            return KioskConfig(
                ini = ini,
                siteId = ini.get("general", "site_id", "default"),
                deviceId = ini.get("general", "device_id", androidId),
                adminPin = ini.get("general", "admin_pin", "2468"),
                orientation = ini.get("general", "orientation", "landscape").lowercase(),
                thankYouSeconds = ini.int("general", "thank_you_seconds", 4).coerceIn(1, 60),
                question = ini.get("display", "question", "How was your experience today?"),
                questionSecondary = ini.get("display", "question_secondary"),
                thankYou = ini.get("display", "thank_you", "Thank you for your feedback!"),
                thankYouSecondary = ini.get("display", "thank_you_secondary"),
                showLabels = ini.bool("display", "show_labels", true),
                labels = (1..5).map { ini.get("display", "label_$it") },
                backgroundColor = color(ini.get("display", "background_color"), Color.WHITE),
                textColor = color(ini.get("display", "text_color"), Color.parseColor("#212121")),
                dbType = type,
                host = ini.get("database", "host"),
                port = ini.int("database", "port", defaultPort),
                database = ini.get("database", "database"),
                instance = ini.get("database", "instance"),
                username = ini.get("database", "username"),
                password = ini.get("database", "password"),
                table = ini.get("database", "table", "SurveyResponses"),
                useSsl = ini.bool("database", "use_ssl", false),
                url = ini.get("database", "url"),
                apiKeyHeader = ini.get("database", "api_key_header", "X-Api-Key"),
                apiKey = ini.get("database", "api_key"),
                retrySeconds = ini.int("queue", "retry_seconds", 30).coerceIn(5, 3600),
                batchSize = ini.int("queue", "batch_size", 50).coerceIn(1, 500),
                csvBackup = ini.bool("local", "csv_backup", true),
                lockDevice = ini.bool("kiosk", "lock_device", true),
                screenPinning = ini.bool("kiosk", "screen_pinning", ini.bool("general", "pin_screen", false)),
                keepAwake = ini.bool("kiosk", "keep_awake", true),
                loadError = error,
            )
        }
    }
}
