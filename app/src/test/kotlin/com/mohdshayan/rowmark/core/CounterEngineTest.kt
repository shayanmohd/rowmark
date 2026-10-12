package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterEngineTest {
    private val section = CounterSnapshot(3, CounterKind.SECTION, 1, length = 3)
    private val repeat = CounterSnapshot(2, CounterKind.REPEAT, 1, length = 8, parentId = 3, alertEvery = 4, alertLabel = "Cable cross")
    private val row = CounterSnapshot(1, CounterKind.ROW, 0, length = 18, parentId = 2, alertEvery = 6, alertLabel = "Cable cross")
    private val stitch = CounterSnapshot(4, CounterKind.STITCH, 7, resetsWithRow = true)
    private val all = listOf(row, repeat, section, stitch)

    private fun values(cs: List<CounterSnapshot>) = cs.associate { it.id to it.value }

    @Test fun countMovesRowAndResetsStitch() {
        val r = CounterEngine.count(all, 1)
        val after = values(CounterEngine.apply(all, r.changes))
        assertEquals(1, after[1]); assertEquals(1, after[2]); assertEquals(0, after[4])
        assertEquals(1, r.totalDelta); assertFalse(r.rolledOver); assertTrue(r.alerts.isEmpty())
    }

    @Test fun alertFiresOnMultiple() {
        val r = CounterEngine.count(listOf(row.copy(value = 5)), 1)
        assertEquals(listOf(Alert(1, "Cable cross", 6)), r.alerts)
    }

    @Test fun nineteenCountsRollRowIntoRepeat() {
        var cs = all
        repeat(19) { cs = CounterEngine.apply(cs, CounterEngine.count(cs, 1).changes) }
        val v = values(cs)
        assertEquals(1, v[1]); assertEquals(2, v[2]); assertEquals(1, v[3])
    }

    @Test fun rolloverCascadesIntoSectionAndReportsDelta() {
        val cs = listOf(row.copy(value = 18), repeat.copy(value = 8), section.copy(value = 2))
        val r = CounterEngine.count(cs, 1)
        val v = values(CounterEngine.apply(cs, r.changes))
        assertEquals(1, v[1]); assertEquals(1, v[2]); assertEquals(3, v[3])
        assertTrue(r.rolledOver)
        assertEquals(-17, r.changes.first { it.counterId == 1L }.delta)
        assertEquals(18, r.changes.first { it.counterId == 1L }.valueBefore)
    }

    @Test fun undoReversesAWholeRollover() {
        val cs = listOf(row.copy(value = 18), repeat.copy(value = 3), section)
        val r = CounterEngine.count(cs, 1)
        val rolled = CounterEngine.apply(cs, r.changes)
        val back = CounterEngine.apply(rolled, CounterEngine.undo(r.changes))
        assertEquals(values(cs), values(back))
    }

    @Test fun minusCrossesTheBoundary() {
        val cs = listOf(row.copy(value = 1), repeat.copy(value = 2), section)
        val r = CounterEngine.minus(cs, 1)
        val v = values(CounterEngine.apply(cs, r.changes))
        assertEquals(18, v[1]); assertEquals(1, v[2]); assertEquals(-1, r.totalDelta)
    }

    @Test fun minusAtRowOneOfFirstRepeatGoesToZero() {
        val cs = listOf(row.copy(value = 1), repeat.copy(value = 1), section)
        val v = values(CounterEngine.apply(cs, CounterEngine.minus(cs, 1).changes))
        assertEquals(0, v[1]); assertEquals(1, v[2])
    }

    @Test fun minusAtZeroDoesNothing() {
        val r = CounterEngine.minus(all, 1)
        assertTrue(r.isEmpty); assertEquals(0, r.totalDelta)
    }

    @Test fun lengthRulesAndAlertRules() {
        assertTrue(CounterRules.validLength(null)); assertTrue(CounterRules.validLength(2)); assertTrue(CounterRules.validLength(999))
        assertFalse(CounterRules.validLength(1)); assertFalse(CounterRules.validLength(1000))
        assertFalse(AlertRules.due(0, 6)); assertTrue(AlertRules.due(12, 6)); assertFalse(AlertRules.due(7, 6)); assertFalse(AlertRules.due(6, null))
    }

    @Test fun namedCounterWithoutLengthNeverRollsAndNeverMovesTotal() {
        val custom = CounterSnapshot(9, CounterKind.CUSTOM, 41)
        val r = CounterEngine.count(listOf(custom), 9)
        assertEquals(42, r.changes.single().valueAfter); assertEquals(0, r.totalDelta); assertFalse(r.rolledOver)
    }
}
