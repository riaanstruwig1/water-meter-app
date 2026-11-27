package com.watermeter.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Usage summary for one calendar month. */
data class MonthUsage(
    val month: YearMonth,
    /** kL used from the baseline reading to the latest reading in the month, or null if not enough data. */
    val usedKl: Double?,
    /** Average kL per day over the measured span, or null. */
    val perDayKl: Double?,
    val baseline: Reading?,
    val latest: Reading?,
)

object Usage {

    /**
     * kL used since the previous reading, for every reading (oldest first).
     * The first reading has no previous one, so its value is null.
     */
    fun deltas(readings: List<Reading>): Map<Long, Double?> {
        val sorted = readings.sortedBy { it.timestamp }
        val out = LinkedHashMap<Long, Double?>()
        sorted.forEachIndexed { i, r ->
            out[r.id] = if (i == 0) null else r.value - sorted[i - 1].value
        }
        return out
    }

    /**
     * Water used during [month].
     *
     * The baseline is the last reading taken before the month started, so usage across the
     * month boundary counts. If there is none (first month using the app), the first reading
     * in the month is the baseline. Usage = latest reading in the month − baseline.
     */
    fun forMonth(
        readings: List<Reading>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthUsage {
        val sorted = readings.sortedBy { it.timestamp }
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val inMonth = sorted.filter { it.timestamp in start until end }
        val latest = inMonth.lastOrNull()
        val baseline = sorted.lastOrNull { it.timestamp < start } ?: inMonth.firstOrNull()

        if (latest == null || baseline == null || latest.id == baseline.id) {
            return MonthUsage(month, null, null, baseline, latest)
        }
        val used = latest.value - baseline.value
        val days = (latest.timestamp - baseline.timestamp) / 86_400_000.0
        val perDay = if (days >= 0.5) used / days else null
        return MonthUsage(month, used, perDay, baseline, latest)
    }

    /** kL used since the reading that was last marked as submitted, or null. */
    fun sinceLastSubmission(readings: List<Reading>, submissions: List<Submission>): Double? {
        val lastSubmitted = submissions
            .filter { it.readingId != null }
            .maxByOrNull { it.submittedAt } ?: return null
        val base = readings.firstOrNull { it.id == lastSubmitted.readingId } ?: return null
        val latest = readings.maxByOrNull { it.timestamp } ?: return null
        if (latest.id == base.id) return 0.0
        return latest.value - base.value
    }
}

object Schedule {

    val monthKeyFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun monthKey(month: YearMonth): String = month.format(monthKeyFormat)

    /** The submission date in [month]; a day beyond the month's length becomes its last day. */
    fun dueDate(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** True when the submission date for this month has arrived and it is not submitted yet. */
    fun isDue(today: LocalDate, day: Int, submittedThisMonth: Boolean): Boolean {
        if (day <= 0 || submittedThisMonth) return false
        return !today.isBefore(dueDate(YearMonth.from(today), day))
    }

    /**
     * When the next reminder should fire.
     *
     * While this month's reading is due and not submitted, the reminder repeats daily at the
     * reminder time. Otherwise it fires on next month's (or this month's, if still ahead)
     * submission date.
     */
    fun nextReminder(
        now: LocalDateTime,
        day: Int,
        time: LocalTime,
        submittedThisMonth: Boolean,
    ): LocalDateTime? {
        if (day <= 0) return null
        val thisMonth = YearMonth.from(now)
        val dueThisMonth = dueDate(thisMonth, day).atTime(time)

        if (!submittedThisMonth) {
            if (now.isBefore(dueThisMonth)) return dueThisMonth
            // Due and outstanding: nag daily.
            val todayAt = now.toLocalDate().atTime(time)
            return if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
        }
        return dueDate(thisMonth.plusMonths(1), day).atTime(time)
    }

    fun toMillis(dt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dt.atZone(zone).toInstant().toEpochMilli()

    fun toLocal(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
}
