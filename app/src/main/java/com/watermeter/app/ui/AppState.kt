package com.watermeter.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.watermeter.app.data.IdSource
import com.watermeter.app.data.Meter
import com.watermeter.app.data.MeterDatabase
import com.watermeter.app.data.Reading
import com.watermeter.app.data.Schedule
import com.watermeter.app.data.Settings
import com.watermeter.app.data.Submission
import com.watermeter.app.data.SerialLearner
import com.watermeter.app.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.io.File
import java.time.YearMonth

/** Holds the app's data for the UI. Data is small, so the database is read directly. */
class AppState(private val context: Context) {
    private val db = MeterDatabase(context)
    /** For background work that must finish even after leaving a screen (e.g. gallery copies). */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = Settings(context)

    var meters by mutableStateOf<List<Meter>>(emptyList())
        private set
    var readings by mutableStateOf<List<Reading>>(emptyList())
        private set
    var submissions by mutableStateOf<List<Submission>>(emptyList())
        private set
    var selectedMeterId by mutableLongStateOf(1L)
    /** Bumped when settings change so the UI recomposes. */
    var settingsVersion by mutableStateOf(0)
        private set

    init {
        reload()
        // Version 0.1 kept one account number in settings; move it onto the first meter.
        val oldAccount = settings.accountNumber
        val first = meters.firstOrNull()
        if (oldAccount.isNotBlank() && first != null && first.accountNumber.isBlank()) {
            db.updateMeter(first.copy(accountNumber = oldAccount))
            settings.accountNumber = ""
            reload()
        }
        selectedMeterId = meters.firstOrNull()?.id ?: 1L
    }

    fun reload() {
        meters = db.allMeters()
        readings = db.allReadings()
        submissions = db.allSubmissions()
        if (meters.none { it.id == selectedMeterId }) selectedMeterId = meters.firstOrNull()?.id ?: 1L
    }

    val selectedMeter: Meter? get() = meters.firstOrNull { it.id == selectedMeterId }

    fun meter(id: Long): Meter? = meters.firstOrNull { it.id == id }

    fun readingsFor(meterId: Long): List<Reading> = readings.filter { it.meterId == meterId }

    fun latestFor(meterId: Long): Reading? = readingsFor(meterId).maxByOrNull { it.timestamp }

    fun addReading(
        meterId: Long,
        value: Double,
        photoPath: String?,
        note: String?,
        source: IdSource,
        scan: ScanResult? = null,
    ) {
        val now = System.currentTimeMillis()
        db.insertReading(
            Reading(
                meterId = meterId,
                timestamp = now,
                value = value,
                photoPath = photoPath,
                note = note,
                idSource = source,
            )
        )
        selectedMeterId = meterId
        if (scan != null) learnSerial(meterId, scan, value)
        reload()

        val m = meter(meterId)
        if (photoPath != null && m != null && settings.saveToGallery) {
            val stamp = Schedule.toLocal(now)
            val fileName = "${m.name.replace(Regex("[^A-Za-z0-9]+"), "_")}_" +
                stamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".jpg"
            val lines = listOf(
                "${m.name}  ·  ${fmtReading(value)}",
                stamp.format(dateTimeFmt) + if (m.serial.isNotBlank()) "  ·  SN ${m.serial}" else "",
            )
            background.launch { Photos.saveStampedToGallery(context, photoPath, fileName, lines) }
        }
    }

    /** Remembers the barcode / printed serial seen on a photo the user confirmed for [meterId]. */
    private fun learnSerial(meterId: Long, scan: ScanResult, value: Double) {
        val m = meter(meterId) ?: return
        val readingDigits = String.format(java.util.Locale.ROOT, "%.4f", value).filter { it.isDigit() }.trimStart('0')
        val learned = SerialLearner.learn(m, scan.barcodes, scan.labelFragments, readingDigits, meters)
        if (learned != m) db.updateMeter(learned)
    }

    fun deleteReading(r: Reading) {
        db.deleteReading(r.id)
        r.photoPath?.let { File(it).delete() }
        reload()
    }

    /** Adds or updates a meter and returns its id. */
    fun saveMeter(m: Meter): Long {
        val id = if (m.id == 0L) db.insertMeter(m) else {
            db.updateMeter(m); m.id
        }
        reload()
        return id
    }

    fun deleteMeter(m: Meter) {
        if (meters.size <= 1) return
        db.deleteMeter(m.id).forEach { File(it).delete() }
        reload()
    }

    val currentMonthKey: String get() = Schedule.monthKey(YearMonth.now())

    fun isSubmittedThisMonth(): Boolean = submissions.any { it.month == currentMonthKey }

    fun markSubmitted() {
        db.markSubmitted(currentMonthKey, System.currentTimeMillis())
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

    fun saveSettings(
        day: Int,
        hour: Int,
        minute: Int,
        periodStart: Int,
        periodEnd: Int,
        unitPrice: Double,
        currency: String,
    ) {
        settings.submissionDay = day
        settings.reminderHour = hour
        settings.reminderMinute = minute
        settings.periodStartDay = periodStart
        settings.periodEndDay = periodEnd
        settings.unitPrice = unitPrice
        settings.currency = currency
        settingsVersion++
        Reminders.scheduleNext(context)
    }
}
