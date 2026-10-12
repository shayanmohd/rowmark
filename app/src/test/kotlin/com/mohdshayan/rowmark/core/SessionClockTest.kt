package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionClockTest {
    private val minute = 60_000L

    @Test fun firstCountOpensASession() {
        val t = SessionClock.onCount(null, 1000L, countsAsRow = true)
        assertNull(t.closed); assertEquals(SessionState(1000L, 1000L, 0L, 1), t.current)
    }

    @Test fun countsInsideTheIdleLimitAccumulateActiveTime() {
        var s = SessionClock.onCount(null, 0L, true).current
        s = SessionClock.onCount(s, 2 * minute, true).current
        s = SessionClock.onCount(s, 5 * minute, false).current
        assertEquals(5 * minute, s.activeMs); assertEquals(2, s.rows); assertEquals(5 * minute, s.lastAt)
    }

    @Test fun aGapPastTenMinutesClosesAtTheLastCountAndStartsANewOne() {
        var s = SessionClock.onCount(null, 0L, true).current
        s = SessionClock.onCount(s, 3 * minute, true).current
        val t = SessionClock.onCount(s, 14 * minute, true)
        assertNotNull(t.closed)
        assertEquals(3 * minute, t.closed!!.endedAt); assertEquals(3 * minute, t.closed!!.activeMs); assertEquals(2, t.closed!!.rows)
        assertEquals(SessionState(14 * minute, 14 * minute, 0L, 1), t.current)
    }

    @Test fun exactlyTenMinutesStillCounts() {
        val s = SessionClock.onCount(null, 0L, true).current
        val t = SessionClock.onCount(s, 10 * minute, true)
        assertNull(t.closed); assertEquals(10 * minute, t.current.activeMs)
    }
}
