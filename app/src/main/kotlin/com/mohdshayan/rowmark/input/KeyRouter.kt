package com.mohdshayan.rowmark.input

import android.view.KeyEvent

/**
 * MainActivity forwards every key event here. A screen that counts (Counter, Pattern) registers a
 * handler while it is on screen; with no handler the system keeps its volume keys. The remote test in
 * Settings registers a probe that reports but never consumes.
 */
object KeyRouter {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null
    @Volatile var probe: ((KeyEvent) -> Unit)? = null

    fun dispatch(event: KeyEvent): Boolean {
        probe?.invoke(event)
        return handler?.invoke(event) ?: false
    }

    /** The keys that count: volume up, Enter and numpad Enter, plus one the user chose in Settings. */
    fun countsRow(keyCode: Int, extra: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            (extra > 0 && keyCode == extra)
}
