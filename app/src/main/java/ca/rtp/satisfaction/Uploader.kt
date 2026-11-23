package ca.rtp.satisfaction

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Background thread that drains the local queue to the configured destination, retrying forever. */
class Uploader(private val queue: LocalQueue, initial: KioskConfig) : Thread("uploader") {

    @Volatile var config: KioskConfig = initial
    @Volatile var lastStatus: String = "Starting"
        private set

    private val lock = Object()
    @Volatile private var poked = false

    init { isDaemon = true }

    /** Wake the thread now (new response, config reload, or "send now"). */
    fun poke() = synchronized(lock) { poked = true; lock.notifyAll() }

    private fun now() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    override fun run() {
        while (true) {
            val cfg = config
            var more = false
            try {
                val sink = Sinks.create(cfg)
                val batch = queue.peek(cfg.batchSize)
                when {
                    batch.isEmpty() -> lastStatus = "Up to date (${now()})"
                    sink == null -> {
                        // type = none: local-only mode, CSV backup already has the data.
                        queue.remove(batch.map { it.responseId })
                        lastStatus = "Local-only mode (type = none)"
                        more = true
                    }
                    else -> {
                        sink.send(batch)
                        queue.remove(batch.map { it.responseId })
                        lastStatus = "Sent ${batch.size} to ${cfg.dbType} at ${now()}"
                        more = batch.size >= cfg.batchSize
                    }
                }
            } catch (e: Throwable) {
                lastStatus = "Error at ${now()}: ${e.javaClass.simpleName}: ${e.message}"
                Log.w("Uploader", lastStatus, e)
            }
            if (more) continue
            synchronized(lock) {
                if (!poked) {
                    try { lock.wait(cfg.retrySeconds * 1000L) } catch (_: InterruptedException) {}
                }
                poked = false
            }
        }
    }
}
