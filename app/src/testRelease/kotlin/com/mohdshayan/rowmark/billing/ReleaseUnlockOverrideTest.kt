package com.mohdshayan.rowmark.billing

import org.junit.Assert.assertNull
import org.junit.Test

/** Pins that a release build has no debug unlock, whatever anyone writes into system settings. */
class ReleaseUnlockOverrideTest {
    @Test fun releaseOverrideIsAlwaysNull() {
        assertNull(DebugUnlock.forcedFrom("1"))
        assertNull(DebugUnlock.forcedFrom("0"))
        assertNull(DebugUnlock.forcedFrom(null))
    }
}
