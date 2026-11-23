package ca.rtp.satisfaction

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SurveyResponse(
    val responseId: String,
    val rating: Int,
    val createdUtc: String,    // yyyy-MM-dd HH:mm:ss
    val createdLocal: String,  // yyyy-MM-dd HH:mm:ss (tablet's time zone)
    val siteId: String,
    val deviceId: String,
)

/** On-device SQLite store. Every tap lands here first so nothing is lost when offline. */
class LocalQueue(ctx: Context) : SQLiteOpenHelper(ctx, "queue.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE pending (
                 response_id TEXT PRIMARY KEY,
                 rating INTEGER NOT NULL,
                 created_utc TEXT NOT NULL,
                 created_local TEXT NOT NULL,
                 site_id TEXT NOT NULL,
                 device_id TEXT NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun add(r: SurveyResponse) {
        writableDatabase.insert("pending", null, ContentValues().apply {
            put("response_id", r.responseId)
            put("rating", r.rating)
            put("created_utc", r.createdUtc)
            put("created_local", r.createdLocal)
            put("site_id", r.siteId)
            put("device_id", r.deviceId)
        })
    }

    @Synchronized
    fun peek(limit: Int): List<SurveyResponse> {
        val out = ArrayList<SurveyResponse>()
        readableDatabase.rawQuery(
            "SELECT response_id, rating, created_utc, created_local, site_id, device_id " +
                    "FROM pending ORDER BY created_utc LIMIT ?", arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += SurveyResponse(c.getString(0), c.getInt(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5))
            }
        }
        return out
    }

    @Synchronized
    fun remove(ids: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete("pending", "response_id = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun count(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM pending", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
}
