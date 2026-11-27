package com.watermeter.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MeterDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "meter.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                value REAL NOT NULL,
                photo_path TEXT,
                note TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE submissions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                month TEXT NOT NULL UNIQUE,
                submitted_at INTEGER NOT NULL,
                reading_id INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun allReadings(): List<Reading> {
        val out = mutableListOf<Reading>()
        readableDatabase.query(
            "readings", null, null, null, null, null, "timestamp ASC"
        ).use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val ts = c.getColumnIndexOrThrow("timestamp")
            val v = c.getColumnIndexOrThrow("value")
            val p = c.getColumnIndexOrThrow("photo_path")
            val n = c.getColumnIndexOrThrow("note")
            while (c.moveToNext()) {
                out += Reading(
                    id = c.getLong(id),
                    timestamp = c.getLong(ts),
                    value = c.getDouble(v),
                    photoPath = if (c.isNull(p)) null else c.getString(p),
                    note = if (c.isNull(n)) null else c.getString(n),
                )
            }
        }
        return out
    }

    fun insertReading(r: Reading): Long {
        val cv = ContentValues().apply {
            put("timestamp", r.timestamp)
            put("value", r.value)
            put("photo_path", r.photoPath)
            put("note", r.note)
        }
        return writableDatabase.insertOrThrow("readings", null, cv)
    }

    fun deleteReading(id: Long) {
        writableDatabase.delete("readings", "id = ?", arrayOf(id.toString()))
    }

    fun allSubmissions(): List<Submission> {
        val out = mutableListOf<Submission>()
        readableDatabase.query(
            "submissions", null, null, null, null, null, "submitted_at ASC"
        ).use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val m = c.getColumnIndexOrThrow("month")
            val at = c.getColumnIndexOrThrow("submitted_at")
            val r = c.getColumnIndexOrThrow("reading_id")
            while (c.moveToNext()) {
                out += Submission(
                    id = c.getLong(id),
                    month = c.getString(m),
                    submittedAt = c.getLong(at),
                    readingId = if (c.isNull(r)) null else c.getLong(r),
                )
            }
        }
        return out
    }

    fun markSubmitted(month: String, readingId: Long?, at: Long) {
        val cv = ContentValues().apply {
            put("month", month)
            put("submitted_at", at)
            put("reading_id", readingId)
        }
        writableDatabase.insertWithOnConflict(
            "submissions", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun unmarkSubmitted(month: String) {
        writableDatabase.delete("submissions", "month = ?", arrayOf(month))
    }

    fun isSubmitted(month: String): Boolean =
        readableDatabase.query(
            "submissions", arrayOf("id"), "month = ?", arrayOf(month), null, null, null
        ).use { it.moveToFirst() }
}
