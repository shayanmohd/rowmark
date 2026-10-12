package com.mohdshayan.rowmark.core

/** Drops a repeat of the same key inside [windowMs]: a bouncy shutter remote sends two for one press. */
class KeyDebouncer(private val windowMs: Long = 200L) {
    private var lastKey: Int = Int.MIN_VALUE
    private var lastAt: Long = Long.MIN_VALUE

    fun accept(keyCode: Int, nowMs: Long): Boolean {
        val repeat = keyCode == lastKey && nowMs - lastAt in 0 until windowMs
        if (!repeat) {
            lastKey = keyCode
            lastAt = nowMs
        }
        return !repeat
    }
}
