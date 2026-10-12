package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** The edges the first pass skipped: zero, negative, huge, boundaries, time zones, DST, empty and corrupt input. */
class EdgeCasesTest {

    private fun values(cs: List<CounterSnapshot>) = cs.associate { it.id to it.value }

    // CounterEngine

    @Test fun aParentCycleNeverLoops() {
        val a = CounterSnapshot(1, CounterKind.ROW, 2, length = 2, parentId = 2)
        val b = CounterSnapshot(2, CounterKind.REPEAT, 2, length = 2, parentId = 1)
        val r = CounterEngine.count(listOf(a, b), 1)
        assertEquals(2, r.changes.size)
        assertEquals(listOf(1, 1), r.changes.map { it.valueAfter })
    }

    @Test fun aLengthBelowTheMinimumIsIgnoredSoTheCounterNeverRollsOver() {
        val c = CounterSnapshot(1, CounterKind.ROW, 5, length = 1)
        assertEquals(6, CounterEngine.count(listOf(c), 1).changes.single().valueAfter)
        assertEquals(0, CounterEngine.count(listOf(CounterSnapshot(1, CounterKind.ROW, 5, length = 0)), 1).changes.single().valueAfter - 6)
    }

    @Test fun aValueAlreadyPastAShortenedLengthRollsOnTheNextCount() {
        val row = CounterSnapshot(1, CounterKind.ROW, 40, length = 18, parentId = 2)
        val rep = CounterSnapshot(2, CounterKind.REPEAT, 1, length = 8)
        val v = values(CounterEngine.apply(listOf(row, rep), CounterEngine.count(listOf(row, rep), 1).changes))
        assertEquals(1, v[1]); assertEquals(2, v[2])
    }

    @Test fun countingAnUnknownCounterDoesNothing() {
        val r = CounterEngine.count(listOf(CounterSnapshot(1, CounterKind.ROW, 0)), 99)
        assertTrue(r.isEmpty); assertEquals(0, r.totalDelta)
        assertTrue(CounterEngine.minus(listOf(CounterSnapshot(1, CounterKind.ROW, 3)), 99).isEmpty)
    }

    @Test fun emptyCounterListsAreHarmless() {
        assertTrue(CounterEngine.count(emptyList(), 1).isEmpty)
        assertEquals(emptyList<CounterSnapshot>(), CounterEngine.apply(emptyList(), listOf(Change(1, 1, 1))))
        assertEquals(emptyList<Change>(), CounterEngine.undo(emptyList()))
    }

    @Test fun aRepeatCountedDirectlyRollsIntoItsSectionButNeverMovesTotalRows() {
        val rep = CounterSnapshot(2, CounterKind.REPEAT, 8, length = 8, parentId = 3)
        val sec = CounterSnapshot(3, CounterKind.SECTION, 1, length = 3)
        val r = CounterEngine.count(listOf(rep, sec), 2)
        val v = values(CounterEngine.apply(listOf(rep, sec), r.changes))
        assertEquals(1, v[2]); assertEquals(2, v[3]); assertEquals(0, r.totalDelta); assertTrue(r.rolledOver)
    }

    @Test fun aStitchCounterOnlyResetsWhenTheRowCounts() {
        val row = CounterSnapshot(1, CounterKind.ROW, 3)
        val stitch = CounterSnapshot(4, CounterKind.STITCH, 12, resetsWithRow = true)
        val custom = CounterSnapshot(9, CounterKind.CUSTOM, 2)
        assertEquals(1, CounterEngine.count(listOf(row, stitch, custom), 9).changes.size)
        assertEquals(1, CounterEngine.count(listOf(row, stitch, custom), 4).changes.size)
        val r = CounterEngine.count(listOf(row, stitch, custom), 1)
        assertEquals(0, r.changes.first { it.counterId == 4L }.valueAfter)
        // A stitch that never counted has nothing to reset, so the undo group stays small.
        assertEquals(1, CounterEngine.count(listOf(row, stitch.copy(value = 0)), 1).changes.size)
    }

    @Test fun minusOnARepeatAtOneWithNoParentAboveOneGoesToZero() {
        val rep = CounterSnapshot(2, CounterKind.REPEAT, 1, length = 8)
        assertEquals(0, CounterEngine.minus(listOf(rep), 2).changes.single().valueAfter)
    }

    @Test fun minusCascadesAcrossTwoBoundaries() {
        val row = CounterSnapshot(1, CounterKind.ROW, 1, length = 18, parentId = 2)
        val rep = CounterSnapshot(2, CounterKind.REPEAT, 1, length = 8, parentId = 3)
        val sec = CounterSnapshot(3, CounterKind.SECTION, 2, length = 3)
        val v = values(CounterEngine.apply(listOf(row, rep, sec), CounterEngine.minus(listOf(row, rep, sec), 1).changes))
        assertEquals(18, v[1]); assertEquals(8, v[2]); assertEquals(1, v[3])
    }

    @Test fun aHugeValueStillCountsAndAnAlertAtTheMaximumLengthFires() {
        val big = CounterSnapshot(1, CounterKind.CUSTOM, 1_000_000)
        assertEquals(1_000_001, CounterEngine.count(listOf(big), 1).changes.single().valueAfter)
        val row = CounterSnapshot(1, CounterKind.ROW, 998, length = 999, alertEvery = 999, alertLabel = "End")
        val r = CounterEngine.count(listOf(row), 1)
        assertEquals(999, r.changes.single().valueAfter); assertEquals(1, r.alerts.size)
        val next = CounterEngine.count(CounterEngine.apply(listOf(row), r.changes), 1)
        assertEquals(1, next.changes.single().valueAfter); assertTrue(next.alerts.isEmpty())
    }

    @Test fun alertAndLengthRulesRejectZeroAndNegatives() {
        assertFalse(AlertRules.due(6, 0)); assertFalse(AlertRules.due(6, -6)); assertFalse(AlertRules.due(-6, 6))
        assertFalse(CounterRules.validLength(0)); assertFalse(CounterRules.validLength(-5))
        assertFalse(CounterRules.validAlert(0)); assertFalse(CounterRules.validAlert(1000)); assertTrue(CounterRules.validAlert(null))
    }

    // KeyDebouncer

    @Test fun debouncerWithAZeroWindowNeverDropsAndAClockStepBackIsNotARepeat() {
        val none = KeyDebouncer(0)
        assertTrue(none.accept(24, 100)); assertTrue(none.accept(24, 100))
        val d = KeyDebouncer(200)
        assertTrue(d.accept(24, 1000)); assertTrue(d.accept(24, 900))
        assertFalse(d.accept(24, 1050)); assertTrue(d.accept(24, 1100))
    }

    // ScheduleEngine

    @Test fun scheduleBoundaries() {
        val once = ScheduleSpec(1, "increase", "", startRow = 1, every = 1, times = 1)
        assertEquals(1, ScheduleEngine.stepAt(once, 1)); assertNull(ScheduleEngine.stepAt(once, 2)); assertEquals(1, ScheduleEngine.lastRow(once))
        assertTrue(ScheduleEngine.isFinished(once, 2)); assertNull(ScheduleEngine.nextDueRow(once, 2))
        assertNull(ScheduleEngine.stepAt(once, 0)); assertNull(ScheduleEngine.stepAt(once, -3)); assertEquals(1, ScheduleEngine.nextDueRow(once, -3))
        val huge = ScheduleSpec(2, "decrease", "", startRow = 999, every = 999, times = 999)
        assertEquals(999 + 999 * 998, ScheduleEngine.lastRow(huge)); assertEquals(999, ScheduleEngine.stepAt(huge, ScheduleEngine.lastRow(huge)))
        assertFalse(ScheduleEngine.valid(huge.copy(times = 0))); assertFalse(ScheduleEngine.valid(huge.copy(startRow = 0))); assertFalse(ScheduleEngine.valid(huge.copy(every = -1)))
        assertFalse(ScheduleEngine.isFinished(huge.copy(every = 0), 5000))
    }

    // RulerMath

    @Test fun rulerSurvivesNanZeroPagesAndZeroSteps() {
        assertEquals(RulerMath.MIN_HEIGHT, RulerMath.clampHeight(Float.NaN), 0f)
        assertEquals(0f, RulerMath.clampTop(Float.NaN, 0.1f), 0f)
        assertEquals(RulerMath.MAX_HEIGHT, RulerMath.clampHeight(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f - RulerMath.MAX_HEIGHT, RulerMath.clampTop(0.9f, 5f), 1e-6f)
        val pos = RulerPos(0, 0.5f)
        assertEquals(pos, RulerMath.step(pos, 0.05f, true, 0, 1).let { RulerPos(it.page, (it.top * 100).toInt() / 100f + 0.05f) })
        assertEquals(pos, RulerMath.step(pos, 0.05f, true, 3, 0))
        val up = RulerMath.step(RulerPos(0, 0.02f), 0.05f, true, 0, 1)
        assertEquals(0, up.page); assertEquals(0f, up.top, 0f)
    }

    @Test fun rulerStepsAcrossManyPagesAtOnce() {
        // 0.5 band height from the bottom half of page 0: top of page 0, then two steps per page.
        val r = RulerMath.step(RulerPos(0, 0.5f), 0.5f, readsUpward = true, pageCount = 4, steps = 5)
        assertEquals(2, r.page); assertEquals(0f, r.top, 1e-5f)
        // Undo walks the same path back: page 2, page 1, page 0, never on to page 3.
        val back = RulerMath.step(r, 0.5f, readsUpward = true, pageCount = 4, steps = -5)
        assertEquals(0, back.page); assertEquals(0.5f, back.top, 1e-5f)
        val onePage = RulerMath.step(RulerPos(0, 0f), 0.5f, readsUpward = true, pageCount = 1, steps = 3)
        assertEquals(RulerPos(0, 0f), onePage)
        val downBack = RulerMath.step(RulerPos(1, 0f), 0.05f, readsUpward = false, pageCount = 2, steps = -1)
        assertEquals(0, downBack.page); assertEquals(0.95f, downBack.top, 1e-5f)
    }

    // SessionClock

    @Test fun sessionClockHandlesAClockThatJumpsBackAndAZeroIdleLimit() {
        val s = SessionClock.onCount(null, 5_000L, true).current
        val t = SessionClock.onCount(s, 4_000L, true)
        assertEquals(5_000L, t.closed!!.endedAt); assertEquals(4_000L, t.current.startedAt)
        val z = SessionClock.onCount(s, 5_001L, true, idleMs = 0L)
        assertEquals(1, z.current.rows); assertEquals(5_001L, z.current.startedAt)
        assertFalse(SessionClock.isIdle(s.copy(endedAt = 5_000L), 999_999_999L))
        assertEquals(s.copy(endedAt = 5_000L), SessionClock.closeAtLastCount(s))
    }

    // StatsCalc

    @Test fun statsWithNoSessionsOrNoDays() {
        assertEquals(0, StatsCalc.totalRows(emptyList())); assertEquals(0L, StatsCalc.totalActiveMs(emptyList()))
        assertNull(StatsCalc.rowsPerHour(emptyList())); assertNull(StatsCalc.averageRowsPerWorkingDay(emptyList()))
        val days = StatsCalc.rowsPerDay(emptyList(), 14, 1_700_000_000_000L, ZoneOffset.UTC)
        assertEquals(14, days.size); assertTrue(days.all { it.rows == 0 })
        assertEquals(0, StatsCalc.rowsPerDay(emptyList(), 0, 1_700_000_000_000L, ZoneOffset.UTC).size)
        assertNull(StatsCalc.finishEstimateDays(100, 0, 0.0)); assertNull(StatsCalc.finishEstimateDays(100, 0, -3.0))
        assertEquals(0, StatsCalc.finishEstimateDays(0, 0, 5.0))
    }

    @Test fun rowsPerDayFollowsTheZoneNotUtc() {
        // 23:30 UTC on 10 Oct is already 11 Oct in Kolkata and still 10 Oct in New York.
        val at = LocalDate.of(2026, 10, 10).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val session = listOf(SessionRecord(at, null, 0, 7))
        val now = LocalDate.of(2026, 10, 12).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val kolkata = StatsCalc.rowsPerDay(session, 3, now, ZoneId.of("Asia/Kolkata"))
        assertEquals(7, kolkata.first { it.date == LocalDate.of(2026, 10, 11) }.rows)
        val newYork = StatsCalc.rowsPerDay(session, 3, now, ZoneId.of("America/New_York"))
        assertEquals(7, newYork.first { it.date == LocalDate.of(2026, 10, 10) }.rows)
        assertEquals(0, newYork.first { it.date == LocalDate.of(2026, 10, 11) }.rows)
    }

    @Test fun rowsPerDayAcrossADstChangeStillYieldsOneBucketPerCalendarDay() {
        // Europe/London leaves summer time on 25 Oct 2026: a 25-hour day, still one bucket.
        val zone = ZoneId.of("Europe/London")
        val now = LocalDate.of(2026, 10, 26).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val early = LocalDate.of(2026, 10, 25).atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        val late = LocalDate.of(2026, 10, 25).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val perDay = StatsCalc.rowsPerDay(listOf(SessionRecord(early, null, 0, 3), SessionRecord(late, null, 0, 4)), 3, now, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)), perDay.map { it.date })
        assertEquals(listOf(0, 7, 0), perDay.map { it.rows })
    }

    @Test fun sessionsOutsideTheWindowAreIgnoredAndTheFutureDoesNotCrash() {
        val now = 1_700_000_000_000L
        val sessions = listOf(SessionRecord(now + 86_400_000L * 3, null, 0, 50), SessionRecord(now - 86_400_000L * 30, null, 0, 50))
        assertTrue(StatsCalc.rowsPerDay(sessions, 14, now, ZoneOffset.UTC).all { it.rows == 0 })
    }

    // CsvWriter

    @Test fun csvEdgeCases() {
        assertEquals("", CsvWriter.field(""))
        assertEquals("\"a\r\nb\"", CsvWriter.field("a\r\nb"))
        assertEquals("\"\"\"\"", CsvWriter.field("\""))
        assertEquals("project,rows\r\n", CsvWriter.document(listOf("project", "rows"), emptyList()))
        assertEquals("\r\n", CsvWriter.document(emptyList(), emptyList()))
        assertEquals("\r\n,\r\n", CsvWriter.document(emptyList(), listOf(listOf("", ""))))
    }

    // Gates

    @Test fun gatesTreatANegativeCountAsEmpty() {
        assertEquals(Access.TEASER, Gates.patternAccess(false, -1))
        assertEquals(Access.FULL, Gates.scheduleAccess(true, -1))
        assertEquals(Access.READ_ONLY, Gates.stashAccess(false, Int.MAX_VALUE))
    }

    // BackupCodec

    @Test fun corruptBackupsAreRejectedBeforeAnyTableIsTouched() {
        for (text in listOf("", "   ", "null", "[]", "42", "\"rowmark\"", "{}", "{\"format\":\"abc\",\"app\":\"rowmark\"}", "{\"format\":{},\"app\":\"rowmark\"}",
            "{\"format\":1,\"app\":null,\"exportedAt\":1}", "{\"format\":1,\"app\":\"rowmark\"}", "{\"format\":1,\"app\":\"rowmark\",\"exportedAt\":\"soon\"}",
            "{\"format\":1,\"app\":\"rowmark\",\"exportedAt\":1,\"projects\":[{\"uuid\":\"x\"}]}", "{\"format\":1,\"app\":\"rowmark\",\"exportedAt\":1,\"projects\":\"nope\"}",
            "{\"format\":1,\"app\":\"rowmark\",\"exportedAt\":1", "\u0000\u0001\u0002")) {
            assertThrows("input: $text", BackupFormatException::class.java) { BackupCodec.decode(text) }
        }
    }

    @Test fun anOlderOrOddFormatNumberStillDecodesAndAnEmptyBackupIsValid() {
        val empty = BackupCodec.decode("{\"format\":0,\"app\":\"rowmark\",\"exportedAt\":0}")
        assertEquals(0, empty.projects.size); assertEquals(0, empty.settings.size)
        assertEquals(-1, BackupCodec.decode("{\"format\":-1,\"app\":\"rowmark\",\"exportedAt\":0}").format)
        val huge = BackupFile(exportedAt = Long.MAX_VALUE, projects = (1..2000).map { ProjectDto("u$it", "P$it", "knitting", "active", "", "", "", null, it, null, 0, 0, null) })
        val round = BackupCodec.decode(BackupCodec.encode(huge))
        assertEquals(2000, round.projects.size); assertEquals(Long.MAX_VALUE, round.exportedAt)
    }

    @Test fun mergePlanWithNothingOnEitherSide() {
        val none = BackupFile(exportedAt = 1)
        assertEquals(RestorePlan(emptySet(), emptySet()), BackupCodec.plan(emptySet(), none, RestoreMode.MERGE))
        assertEquals(RestorePlan(emptySet(), setOf("a")), BackupCodec.plan(setOf("a"), none, RestoreMode.REPLACE))
    }
}
