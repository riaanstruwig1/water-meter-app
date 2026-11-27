package com.watermeter.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MeterDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "meter.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        createMeters(db)
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                meter_id INTEGER NOT NULL DEFAULT 1,
                timestamp INTEGER NOT NULL,
                value REAL NOT NULL,
                photo_path TEXT,
                note TEXT,
                id_source TEXT NOT NULL DEFAULT 'MANUAL'
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

    private fun createMeters(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE meters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                serial TEXT NOT NULL DEFAULT '',
                whole_digits INTEGER NOT NULL DEFAULT 5,
                decimal_digits INTEGER NOT NULL DEFAULT 4,
                account_number TEXT NOT NULL DEFAULT '',
                serial_hints TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO meters (id, name) VALUES (1, 'Meter 1')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createMeters(db)
            db.execSQL("ALTER TABLE readings ADD COLUMN meter_id INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE readings ADD COLUMN id_source TEXT NOT NULL DEFAULT 'MANUAL'")
        } else if (oldVersion < 3) {
            // Version 2 databases already have meters, but without learned serial hints.
            db.execSQL("ALTER TABLE meters ADD COLUMN serial_hints TEXT NOT NULL DEFAULT ''")
        }
    }

    // ---------------------------------------------------------------- meters

    fun allMeters(): List<Meter> {
        val out = mutableListOf<Meter>()
        readableDatabase.query("meters", null, null, null, null, null, "id ASC").use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val name = c.getColumnIndexOrThrow("name")
            val serial = c.getColumnIndexOrThrow("serial")
            val whole = c.getColumnIndexOrThrow("whole_digits")
            val dec = c.getColumnIndexOrThrow("decimal_digits")
            val acc = c.getColumnIndexOrThrow("account_number")
            val hints = c.getColumnIndexOrThrow("serial_hints")
            while (c.moveToNext()) {
                out += Meter(
                    id = c.getLong(id),
                    name = c.getString(name),
                    serial = c.getString(serial),
                    wholeDigits = c.getInt(whole),
                    decimalDigits = c.getInt(dec),
                    accountNumber = c.getString(acc),
                    serialHints = c.getString(hints).split(',').filter { it.isNotBlank() },
                )
            }
        }
        return out
    }

    private fun meterValues(m: Meter) = ContentValues().apply {
        put("name", m.name)
        put("serial", m.serial)
        put("whole_digits", m.wholeDigits)
        put("decimal_digits", m.decimalDigits)
        put("account_number", m.accountNumber)
        put("serial_hints", m.serialHints.joinToString(","))
    }

    fun insertMeter(m: Meter): Long = writableDatabase.insertOrThrow("meters", null, meterValues(m))

    fun updateMeter(m: Meter) {
        writableDatabase.update("meters", meterValues(m), "id = ?", arrayOf(m.id.toString()))
    }

    /** Deletes the meter and its readings; returns the photo paths of the removed readings. */
    fun deleteMeter(id: Long): List<String> {
        val photos = allReadings().filter { it.meterId == id }.mapNotNull { it.photoPath }
        writableDatabase.delete("readings", "meter_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("meters", "id = ?", arrayOf(id.toString()))
        return photos
    }

    // ---------------------------------------------------------------- readings

    fun allReadings(): List<Reading> {
        val out = mutableListOf<Reading>()
        readableDatabase.query(
            "readings", null, null, null, null, null, "timestamp ASC"
        ).use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val meter = c.getColumnIndexOrThrow("meter_id")
            val ts = c.getColumnIndexOrThrow("timestamp")
            val v = c.getColumnIndexOrThrow("value")
            val p = c.getColumnIndexOrThrow("photo_path")
            val n = c.getColumnIndexOrThrow("note")
            val src = c.getColumnIndexOrThrow("id_source")
            while (c.moveToNext()) {
                out += Reading(
                    id = c.getLong(id),
                    meterId = c.getLong(meter),
                    timestamp = c.getLong(ts),
                    value = c.getDouble(v),
                    photoPath = if (c.isNull(p)) null else c.getString(p),
                    note = if (c.isNull(n)) null else c.getString(n),
                    idSource = runCatching { IdSource.valueOf(c.getString(src)) }.getOrDefault(IdSource.MANUAL),
                )
            }
        }
        return out
    }

    fun insertReading(r: Reading): Long {
        val cv = ContentValues().apply {
            put("meter_id", r.meterId)
            put("timestamp", r.timestamp)
            put("value", r.value)
            put("photo_path", r.photoPath)
            put("note", r.note)
            put("id_source", r.idSource.name)
        }
        return writableDatabase.insertOrThrow("readings", null, cv)
    }

    fun deleteReading(id: Long) {
        writableDatabase.delete("readings", "id = ?", arrayOf(id.toString()))
    }

    // ---------------------------------------------------------------- submissions

    fun allSubmissions(): List<Submission> {
        val out = mutableListOf<Submission>()
        readableDatabase.query(
            "submissions", null, null, null, null, null, "submitted_at ASC"
        ).use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val m = c.getColumnIndexOrThrow("month")
            val at = c.getColumnIndexOrThrow("submitted_at")
            while (c.moveToNext()) {
                out += Submission(id = c.getLong(id), month = c.getString(m), submittedAt = c.getLong(at))
            }
        }
        return out
    }

    fun markSubmitted(month: String, at: Long) {
        val cv = ContentValues().apply {
            put("month", month)
            put("submitted_at", at)
        }
        writableDatabase.insertWithOnConflict("submissions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun unmarkSubmitted(month: String) {
        writableDatabase.delete("submissions", "month = ?", arrayOf(month))
    }

    fun isSubmitted(month: String): Boolean =
        readableDatabase.query(
            "submissions", arrayOf("id"), "month = ?", arrayOf(month), null, null, null
        ).use { it.moveToFirst() }
}
