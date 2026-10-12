package com.mohdshayan.rowmark.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDebouncerTest {
    @Test fun dropsARepeatInsideTheWindow() {
        val d = KeyDebouncer(200)
        assertTrue(d.accept(24, 1000)); assertFalse(d.accept(24, 1150)); assertTrue(d.accept(24, 1201))
    }

    @Test fun aDifferentKeyIsNeverDropped() {
        val d = KeyDebouncer(200)
        assertTrue(d.accept(24, 1000)); assertTrue(d.accept(66, 1010)); assertTrue(d.accept(24, 1020))
    }
}
