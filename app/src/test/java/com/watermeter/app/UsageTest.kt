package com.watermeter.app

import com.watermeter.app.data.Reading
import com.watermeter.app.data.Schedule
import com.watermeter.app.data.Submission
import com.watermeter.app.data.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

class UsageTest {
    private val zone = ZoneOffset.UTC

    private fun r(id: Long, date: String, value: Double) = Reading(
        id = id,
        timestamp = LocalDateTime.parse(date).toInstant(zone).toEpochMilli(),
        value = value,
        photoPath = null,
    )

    @Test fun deltasBetweenConsecutiveReadings() {
        val d = Usage.deltas(listOf(r(2, "2026-11-10T08:00", 105.5), r(1, "2026-11-01T08:00", 100.0)))
        assertNull(d[1])
        assertEquals(5.5, d[2]!!, 1e-9)
    }

    @Test fun monthUsesLastReadingBeforeMonthAsBaseline() {
        val readings = listOf(
            r(1, "2026-10-28T08:00", 100.0),
            r(2, "2026-11-05T08:00", 104.0),
            r(3, "2026-11-20T08:00", 112.0),
            r(4, "2026-12-02T08:00", 115.0),
        )
        val u = Usage.forMonth(readings, YearMonth.of(2026, 11), zone)
        assertEquals(12.0, u.usedKl!!, 1e-9)
        assertEquals(1L, u.baseline!!.id)
        assertEquals(3L, u.latest!!.id)
        assertEquals(12.0 / 23, u.perDayKl!!, 1e-9) // 12 kL over 23 days (28 Oct to 20 Nov)
    }

    @Test fun firstMonthUsesFirstReadingInMonth() {
        val readings = listOf(r(1, "2026-11-05T08:00", 104.0), r(2, "2026-11-15T08:00", 109.0))
        assertEquals(5.0, Usage.forMonth(readings, YearMonth.of(2026, 11), zone).usedKl!!, 1e-9)
    }

    @Test fun singleReadingHasNoUsage() {
        val u = Usage.forMonth(listOf(r(1, "2026-11-05T08:00", 104.0)), YearMonth.of(2026, 11), zone)
        assertNull(u.usedKl)
    }

    @Test fun usageSinceLastSubmission() {
        val readings = listOf(r(1, "2026-10-25T08:00", 100.0), r(2, "2026-11-10T08:00", 107.25))
        val subs = listOf(Submission(month = "2026-10", submittedAt = 1, readingId = 1))
        assertEquals(7.25, Usage.sinceLastSubmission(readings, subs)!!, 1e-9)
        assertNull(Usage.sinceLastSubmission(readings, emptyList()))
    }

    @Test fun dueDateClampsToMonthLength() {
        assertEquals(LocalDate.of(2026, 2, 28), Schedule.dueDate(YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.of(2026, 11, 25), Schedule.dueDate(YearMonth.of(2026, 11), 25))
    }

    @Test fun isDue() {
        assertFalse(Schedule.isDue(LocalDate.of(2026, 11, 24), 25, false))
        assertTrue(Schedule.isDue(LocalDate.of(2026, 11, 25), 25, false))
        assertTrue(Schedule.isDue(LocalDate.of(2026, 11, 27), 25, false))
        assertFalse(Schedule.isDue(LocalDate.of(2026, 11, 27), 25, true))
        assertFalse(Schedule.isDue(LocalDate.of(2026, 11, 27), 0, false))
    }

    @Test fun nextReminderBeforeDueDate() {
        val next = Schedule.nextReminder(LocalDateTime.parse("2026-11-10T12:00"), 25, LocalTime.of(8, 0), false)
        assertEquals(LocalDateTime.parse("2026-11-25T08:00"), next)
    }

    @Test fun nextReminderRepeatsDailyWhileOutstanding() {
        val t = LocalTime.of(8, 0)
        assertEquals(
            LocalDateTime.parse("2026-11-28T08:00"),
            Schedule.nextReminder(LocalDateTime.parse("2026-11-27T09:00"), 25, t, false),
        )
        assertEquals(
            LocalDateTime.parse("2026-11-27T08:00"),
            Schedule.nextReminder(LocalDateTime.parse("2026-11-27T07:00"), 25, t, false),
        )
    }

    @Test fun nextReminderAfterSubmittingGoesToNextMonth() {
        val next = Schedule.nextReminder(LocalDateTime.parse("2026-01-31T09:00"), 31, LocalTime.of(8, 0), true)
        assertEquals(LocalDateTime.parse("2026-02-28T08:00"), next)
    }

    @Test fun noReminderWithoutDay() {
        assertNull(Schedule.nextReminder(LocalDateTime.parse("2026-11-27T09:00"), 0, LocalTime.NOON, false))
    }
}
