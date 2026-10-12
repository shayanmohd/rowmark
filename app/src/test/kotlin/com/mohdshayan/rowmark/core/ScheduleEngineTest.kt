package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {
    private val raglan = ScheduleSpec(1, "increase", "Raglan", startRow = 12, every = 6, times = 8)

    @Test fun dueRowsAndSteps() {
        assertEquals(1, ScheduleEngine.stepAt(raglan, 12)); assertEquals(2, ScheduleEngine.stepAt(raglan, 18)); assertEquals(8, ScheduleEngine.stepAt(raglan, 54))
        assertNull(ScheduleEngine.stepAt(raglan, 11)); assertNull(ScheduleEngine.stepAt(raglan, 15)); assertNull(ScheduleEngine.stepAt(raglan, 60))
        assertEquals(54, ScheduleEngine.lastRow(raglan))
    }

    @Test fun nextDueAndFinished() {
        assertEquals(12, ScheduleEngine.nextDueRow(raglan, 1)); assertEquals(18, ScheduleEngine.nextDueRow(raglan, 13)); assertEquals(18, ScheduleEngine.nextDueRow(raglan, 18))
        assertNull(ScheduleEngine.nextDueRow(raglan, 55)); assertTrue(ScheduleEngine.isFinished(raglan, 55)); assertFalse(ScheduleEngine.isFinished(raglan, 54))
    }

    @Test fun invalidSpecsAreNeverDue() {
        val bad = raglan.copy(every = 0)
        assertFalse(ScheduleEngine.valid(bad)); assertFalse(ScheduleEngine.isDue(bad, 12)); assertNull(ScheduleEngine.nextDueRow(bad, 1))
    }
}
