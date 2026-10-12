package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RulerMathTest {
    @Test fun upwardStepMovesTheBandUpOneHeight() {
        val next = RulerMath.step(RulerPos(0, 0.5f), 0.05f, readsUpward = true, pageCount = 2)
        assertEquals(0, next.page); assertEquals(0.45f, next.top, 1e-5f)
    }

    @Test fun upwardStepPastTheTopCarriesToTheNextPageBottom() {
        val next = RulerMath.step(RulerPos(0, 0.02f), 0.05f, readsUpward = true, pageCount = 3)
        assertEquals(1, next.page); assertEquals(0.95f, next.top, 1e-5f)
    }

    @Test fun downwardStepPastTheBottomCarriesToTheNextPageTop() {
        val next = RulerMath.step(RulerPos(0, 0.97f), 0.05f, readsUpward = false, pageCount = 2)
        assertEquals(1, next.page); assertEquals(0f, next.top, 1e-5f)
    }

    @Test fun lastPageClamps() {
        val up = RulerMath.step(RulerPos(1, 0.01f), 0.05f, readsUpward = true, pageCount = 2)
        assertEquals(1, up.page); assertEquals(0f, up.top, 1e-5f)
        val down = RulerMath.step(RulerPos(1, 0.97f), 0.05f, readsUpward = false, pageCount = 2)
        assertEquals(1, down.page); assertEquals(0.95f, down.top, 1e-5f)
    }

    @Test fun negativeStepsReverseTheDirectionAsUndoNeeds() {
        val start = RulerPos(0, 0.5f)
        val forward = RulerMath.step(start, 0.05f, true, 2, 3)
        val back = RulerMath.step(forward, 0.05f, true, 2, -3)
        assertEquals(start.page, back.page); assertEquals(start.top, back.top, 1e-4f)
    }

    @Test fun heightAndTopClamp() {
        assertEquals(0.5f, RulerMath.clampHeight(0.9f), 0f); assertEquals(0.01f, RulerMath.clampHeight(0f), 0f)
        assertEquals(0.9f, RulerMath.clampTop(0.99f, 0.1f), 1e-6f); assertEquals(0f, RulerMath.clampTop(-1f, 0.1f), 0f)
    }
}
