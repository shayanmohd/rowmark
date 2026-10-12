package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GatesTest {
    @Test fun patternAccessFollowsEntitlementThenExistingData() {
        assertEquals(Access.FULL, Gates.patternAccess(true, 0))
        assertEquals(Access.TEASER, Gates.patternAccess(false, 0))
        assertEquals(Access.READ_ONLY, Gates.patternAccess(false, 2))
    }

    @Test fun relockKeepsSchedulesAndStashReadable() {
        assertEquals(Access.READ_ONLY, Gates.scheduleAccess(false, 1)); assertEquals(Access.READ_ONLY, Gates.stashAccess(false, 5))
        assertEquals(Access.TEASER, Gates.stashAccess(false, 0)); assertEquals(Access.FULL, Gates.statsAccess(true)); assertEquals(Access.TEASER, Gates.statsAccess(false))
    }
}
