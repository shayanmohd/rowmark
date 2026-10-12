package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalcTest {
    private val zone = ZoneOffset.UTC
    private fun day(d: Int, hour: Int = 10) = LocalDate.of(2026, 10, d).atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test fun rowsPerHourNeedsAMinute() {
        assertNull(StatsCalc.rowsPerHour(listOf(SessionRecord(0, 1000, 30_000, 5))))
        assertEquals(40.0, StatsCalc.rowsPerHour(listOf(SessionRecord(0, 0, 90 * 60_000L, 60)))!!, 1e-9)
    }

    @Test fun rowsPerDayBucketsByCalendarDayOldestFirst() {
        val sessions = listOf(SessionRecord(day(10), day(10, 11), 0, 12), SessionRecord(day(12), null, 0, 7), SessionRecord(day(12, 20), null, 0, 3), SessionRecord(day(1), null, 0, 99))
        val perDay = StatsCalc.rowsPerDay(sessions, 3, day(12, 23), zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12)), perDay.map { it.date })
        assertEquals(listOf(12, 0, 10), perDay.map { it.rows })
    }

    @Test fun finishEstimateUsesWorkingDays() {
        val perDay = listOf(DayRows(LocalDate.of(2026, 10, 10), 12), DayRows(LocalDate.of(2026, 10, 11), 0), DayRows(LocalDate.of(2026, 10, 12), 8))
        val pace = StatsCalc.averageRowsPerWorkingDay(perDay)
        assertEquals(10.0, pace!!, 1e-9)
        assertEquals(5, StatsCalc.finishEstimateDays(100, 55, pace)); assertEquals(0, StatsCalc.finishEstimateDays(50, 55, pace))
        assertNull(StatsCalc.finishEstimateDays(null, 55, pace)); assertNull(StatsCalc.finishEstimateDays(100, 55, null))
    }
}
