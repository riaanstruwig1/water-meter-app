package com.watermeter.app

import com.watermeter.app.data.BillingPeriod
import com.watermeter.app.data.Reading
import com.watermeter.app.data.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class PeriodTest {
    private val zone = ZoneOffset.UTC

    private fun r(id: Long, date: String, value: Double) = Reading(
        id = id,
        timestamp = LocalDateTime.parse(date).toInstant(zone).toEpochMilli(),
        value = value,
        photoPath = null,
    )

    @Test fun periodRunsFromStartDayToEndDayNextMonth() {
        val p = BillingPeriod.containing(LocalDate.of(2026, 11, 27), 8, 9)
        assertEquals(LocalDate.of(2026, 11, 8), p.start)
        assertEquals(LocalDate.of(2026, 12, 9), p.endInclusive)
        assertEquals(32L, p.lengthDays)
    }

    @Test fun dateBeforeStartDayBelongsToPreviousPeriod() {
        val p = BillingPeriod.containing(LocalDate.of(2026, 12, 5), 8, 9)
        assertEquals(LocalDate.of(2026, 11, 8), p.start)
        val q = BillingPeriod.containing(LocalDate.of(2026, 12, 8), 8, 9)
        assertEquals(LocalDate.of(2026, 12, 8), q.start)
        assertEquals(LocalDate.of(2027, 1, 9), q.endInclusive)
    }

    @Test fun navigationAndShortMonths() {
        val p = BillingPeriod.startingIn(java.time.YearMonth.of(2027, 1), 31, 31)
        assertEquals(LocalDate.of(2027, 1, 31), p.start)
        assertEquals(LocalDate.of(2027, 2, 28), p.endInclusive)
        val n = BillingPeriod.containing(LocalDate.of(2026, 11, 27), 8, 9).next(8, 9)
        assertEquals(LocalDate.of(2026, 12, 8), n.start)
        val prev = n.previous(8, 9).previous(8, 9)
        assertEquals(LocalDate.of(2026, 10, 8), prev.start)
    }

    @Test fun usageForPeriodUsesReadingBeforeStart() {
        val p = BillingPeriod.containing(LocalDate.of(2026, 11, 27), 8, 9)
        val readings = listOf(
            r(1, "2026-11-07T18:00", 1100.0),
            r(2, "2026-11-20T06:00", 1104.5),
            r(3, "2026-12-09T18:00", 1110.0),
            r(4, "2026-12-10T08:00", 1111.0),
        )
        val u = Usage.forRange(readings, p.startMillis(zone), p.endMillisExclusive(zone))
        assertEquals(10.0, u.usedKl!!, 1e-9)
        assertEquals(32L * 86_400_000L, u.spanMillis)
        assertEquals(10.0 / 32, u.perDayKl!!, 1e-9)
    }

    @Test fun intervalsGiveUsageTimeAndRate() {
        val iv = Usage.intervals(
            listOf(r(2, "2026-11-10T14:00", 1102.0), r(1, "2026-11-08T08:00", 1100.0))
        )
        assertNull(iv[0].usedKl)
        assertEquals(2.0, iv[1].usedKl!!, 1e-9)
        assertEquals((2L * 24 + 6) * 3_600_000L, iv[1].spanMillis)
        assertEquals(2.0 / 2.25, iv[1].perDayKl!!, 1e-9)
    }
}
