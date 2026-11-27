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

/** Usage between two readings, or over a period. */
data class RangeUsage(
    val usedKl: Double?,
    val perDayKl: Double?,
    /** Time between the baseline and latest reading, millis. */
    val spanMillis: Long,
    val baseline: Reading?,
    val latest: Reading?,
)

/** One reading and what changed since the previous one. */
data class Interval(
    val reading: Reading,
    val previous: Reading?,
    val usedKl: Double?,
    val spanMillis: Long?,
    val perDayKl: Double?,
)

/**
 * A billing period: from [startDay] of one month to [endDay] of the next (both inclusive),
 * e.g. 8 Nov to 9 Dec. Days past the end of a short month fall on its last day.
 */
data class BillingPeriod(val start: LocalDate, val endInclusive: LocalDate) {
    fun startMillis(zone: ZoneId = ZoneId.systemDefault()) =
        start.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endMillisExclusive(zone: ZoneId = ZoneId.systemDefault()) =
        endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val lengthDays: Long get() = endInclusive.toEpochDay() - start.toEpochDay() + 1

    companion object {
        private fun day(month: YearMonth, d: Int) = month.atDay(d.coerceIn(1, month.lengthOfMonth()))

        /** The period that starts in [startMonth]. */
        fun startingIn(startMonth: YearMonth, startDay: Int, endDay: Int) =
            BillingPeriod(day(startMonth, startDay), day(startMonth.plusMonths(1), endDay))

        /** The period containing [date]: the latest period whose start is on or before it. */
        fun containing(date: LocalDate, startDay: Int, endDay: Int): BillingPeriod {
            val month = YearMonth.from(date)
            val thisStart = day(month, startDay)
            val startMonth = if (date.isBefore(thisStart)) month.minusMonths(1) else month
            return startingIn(startMonth, startDay, endDay)
        }
    }

    fun next(startDay: Int, endDay: Int) = startingIn(YearMonth.from(start).plusMonths(1), startDay, endDay)
    fun previous(startDay: Int, endDay: Int) = startingIn(YearMonth.from(start).minusMonths(1), startDay, endDay)
}

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

    /**
     * Water used between [startMillis] (inclusive) and [endMillis] (exclusive): latest reading in
     * the range minus the last reading before it (or the first reading in it, if there is none).
     */
    fun forRange(readings: List<Reading>, startMillis: Long, endMillis: Long): RangeUsage {
        val sorted = readings.sortedBy { it.timestamp }
        val inRange = sorted.filter { it.timestamp in startMillis until endMillis }
        val latest = inRange.lastOrNull()
        val baseline = sorted.lastOrNull { it.timestamp < startMillis } ?: inRange.firstOrNull()
        if (latest == null || baseline == null || latest.id == baseline.id) {
            return RangeUsage(null, null, 0L, baseline, latest)
        }
        val used = latest.value - baseline.value
        val span = latest.timestamp - baseline.timestamp
        val days = span / 86_400_000.0
        return RangeUsage(used, if (days >= 1.0 / 24) used / days else null, span, baseline, latest)
    }

    /** Every reading with the usage, time and rate since the reading before it (oldest first). */
    fun intervals(readings: List<Reading>): List<Interval> {
        val sorted = readings.sortedBy { it.timestamp }
        return sorted.mapIndexed { i, r ->
            val prev = sorted.getOrNull(i - 1)
            if (prev == null) Interval(r, null, null, null, null)
            else {
                val used = r.value - prev.value
                val span = r.timestamp - prev.timestamp
                val days = span / 86_400_000.0
                Interval(r, prev, used, span, if (days >= 1.0 / 24) used / days else null)
            }
        }
    }

    /**
     * kL used since the last submission: latest reading minus the last reading taken at or
     * before the most recent submission. Pass the readings of a single meter.
     */
    fun sinceLastSubmission(readings: List<Reading>, submissions: List<Submission>): Double? {
        val lastSubmittedAt = submissions.maxOfOrNull { it.submittedAt } ?: return null
        val sorted = readings.sortedBy { it.timestamp }
        val base = sorted.lastOrNull { it.timestamp <= lastSubmittedAt } ?: return null
        val latest = sorted.last()
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
