package ca.rtp.satisfaction

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.sql.DriverManager

/** A destination for responses. send() must throw if the batch was not stored. */
interface ResponseSink {
    fun send(batch: List<SurveyResponse>)

    /** Connection check used by the settings screen. Throws on failure. */
    fun test() = send(emptyList())
}

object Sinks {
    /** Add new destination types here; select them with [database] type = ... in kiosk.ini. */
    fun create(cfg: KioskConfig): ResponseSink? = when (cfg.dbType) {
        "http", "https", "rest", "api" -> HttpSink(cfg)
        "mssql", "sqlserver" -> JdbcSink(cfg, "mssql")
        "mysql", "mariadb" -> JdbcSink(cfg, "mysql")
        "postgresql", "postgres" -> JdbcSink(cfg, "postgresql")
        "none", "" -> null
        else -> throw IllegalArgumentException("Unknown [database] type '${cfg.dbType}'")
    }
}

/**
 * POSTs {"responses":[{...}, ...]} as JSON. Any 2xx = success.
 * The server should ignore duplicate response_id values (a batch may be re-sent after a timeout).
 */
class HttpSink(private val cfg: KioskConfig) : ResponseSink {
    override fun send(batch: List<SurveyResponse>) {
        require(cfg.url.isNotBlank()) { "[database] url is empty" }
        val arr = JSONArray()
        batch.forEach {
            arr.put(JSONObject()
                .put("response_id", it.responseId)
                .put("rating", it.rating)
                .put("created_utc", it.createdUtc)
                .put("created_local", it.createdLocal)
                .put("site_id", it.siteId)
                .put("device_id", it.deviceId))
        }
        val body = JSONObject().put("responses", arr).toString().toByteArray(Charsets.UTF_8)
        val conn = URL(cfg.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (cfg.apiKey.isNotBlank()) conn.setRequestProperty(cfg.apiKeyHeader, cfg.apiKey)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code from ${cfg.url}")
        } finally {
            conn.disconnect()
        }
    }
}

/** Direct insert into SQL Server / MySQL / PostgreSQL. Duplicate response_ids are skipped. */
class JdbcSink(private val cfg: KioskConfig, private val kind: String) : ResponseSink {

    private val tableRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$")
    private val cols = "response_id, rating, created_utc, created_local, site_id, device_id"

    private fun jdbcUrl(): String {
        val h = cfg.host
        val p = cfg.port
        val db = cfg.database
        return when (kind) {
            "mssql" -> buildString {
                append("jdbc:jtds:sqlserver://$h:$p/$db;loginTimeout=15;socketTimeout=30")
                if (cfg.instance.isNotBlank()) append(";instance=${cfg.instance}")
                append(if (cfg.useSsl) ";ssl=require" else ";ssl=request")
            }
            "mysql" -> "jdbc:mysql://$h:$p/$db?connectTimeout=15000&socketTimeout=30000" +
                    "&useUnicode=true&characterEncoding=UTF-8&useSSL=${cfg.useSsl}"
            else -> "jdbc:postgresql://$h:$p/$db?connectTimeout=15&socketTimeout=30" +
                    "&stringtype=unspecified&sslmode=" + (if (cfg.useSsl) "require" else "prefer")
        }
    }

    private fun driverClass() = when (kind) {
        "mssql" -> "net.sourceforge.jtds.jdbc.Driver"
        "mysql" -> "com.mysql.jdbc.Driver"
        else -> "org.postgresql.Driver"
    }

    override fun test() {
        require(cfg.host.isNotBlank()) { "[database] host is empty" }
        require(tableRegex.matches(cfg.table)) { "[database] table name '${cfg.table}' is not valid" }
        Class.forName(driverClass())
        DriverManager.setLoginTimeout(15)
        DriverManager.getConnection(jdbcUrl(), cfg.username, cfg.password).use { conn ->
            conn.createStatement().use { st -> st.executeQuery("SELECT 1").use { it.next() } }
        }
    }

    override fun send(batch: List<SurveyResponse>) {
        require(cfg.host.isNotBlank()) { "[database] host is empty" }
        require(tableRegex.matches(cfg.table)) { "[database] table name '${cfg.table}' is not valid" }
        val t = cfg.table
        val sql = when (kind) {
            "mssql" -> "IF NOT EXISTS (SELECT 1 FROM $t WHERE response_id = ?) " +
                    "INSERT INTO $t ($cols) VALUES (?, ?, ?, ?, ?, ?)"
            "mysql" -> "INSERT IGNORE INTO $t ($cols) VALUES (?, ?, ?, ?, ?, ?)"
            else -> "INSERT INTO $t ($cols) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (response_id) DO NOTHING"
        }

        Class.forName(driverClass())
        DriverManager.setLoginTimeout(15)
        DriverManager.getConnection(jdbcUrl(), cfg.username, cfg.password).use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { ps ->
                    for (r in batch) {
                        var i = 1
                        if (kind == "mssql") ps.setString(i++, r.responseId)
                        ps.setString(i++, r.responseId)
                        ps.setInt(i++, r.rating)
                        ps.setString(i++, r.createdUtc)
                        ps.setString(i++, r.createdLocal)
                        ps.setString(i++, r.siteId)
                        ps.setString(i, r.deviceId)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                try { conn.rollback() } catch (_: Exception) {}
                throw e
            }
        }
    }
}
