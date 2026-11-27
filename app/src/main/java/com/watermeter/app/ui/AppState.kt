package com.watermeter.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.watermeter.app.data.MeterDatabase
import com.watermeter.app.data.Reading
import com.watermeter.app.data.Schedule
import com.watermeter.app.data.Settings
import com.watermeter.app.data.Submission
import com.watermeter.app.reminder.Reminders
import java.io.File
import java.time.YearMonth

/** Holds the app's data for the UI. Data is small, so the database is read directly. */
class AppState(private val context: Context) {
    private val db = MeterDatabase(context)
    val settings = Settings(context)

    var readings by mutableStateOf<List<Reading>>(emptyList())
        private set
    var submissions by mutableStateOf<List<Submission>>(emptyList())
        private set
    /** Bumped when settings change so the UI recomposes. */
    var settingsVersion by mutableStateOf(0)
        private set

    init {
        reload()
    }

    fun reload() {
        readings = db.allReadings()
        submissions = db.allSubmissions()
    }

    fun addReading(timestamp: Long, value: Double, photoPath: String?, note: String?) {
        db.insertReading(Reading(timestamp = timestamp, value = value, photoPath = photoPath, note = note))
        reload()
    }

    fun deleteReading(r: Reading) {
        db.deleteReading(r.id)
        r.photoPath?.let { File(it).delete() }
        reload()
    }

    val currentMonthKey: String get() = Schedule.monthKey(YearMonth.now())

    fun isSubmittedThisMonth(): Boolean = submissions.any { it.month == currentMonthKey }

    fun markSubmitted() {
        db.markSubmitted(currentMonthKey, readings.maxByOrNull { it.timestamp }?.id, System.currentTimeMillis())
        reload()
        Reminders.cancelNotification(context)
        Reminders.scheduleNext(context)
    }

    fun undoSubmitted() {
        db.unmarkSubmitted(currentMonthKey)
        reload()
        Reminders.scheduleNext(context)
    }

    fun isDueNow(): Boolean = Reminders.isDueNow(context)

    fun saveSettings(day: Int, hour: Int, minute: Int, account: String) {
        settings.submissionDay = day
        settings.reminderHour = hour
        settings.reminderMinute = minute
        settings.accountNumber = account.trim()
        settingsVersion++
        Reminders.scheduleNext(context)
    }
}
