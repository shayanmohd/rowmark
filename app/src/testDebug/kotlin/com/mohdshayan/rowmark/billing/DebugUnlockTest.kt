package com.mohdshayan.rowmark.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Debug builds: nothing is forced unless the global setting is set; release pins null in testRelease. */
class DebugUnlockTest {
    @Test fun nothingForcedByDefault() { assertNull(DebugUnlock.forcedFrom(null)); assertNull(DebugUnlock.forcedFrom("")) }
    @Test fun oneForcesUnlockedZeroForcesFree() { assertEquals(true, DebugUnlock.forcedFrom("1")); assertEquals(false, DebugUnlock.forcedFrom("0")) }
}
