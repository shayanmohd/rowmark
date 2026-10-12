package com.mohdshayan.rowmark.ui.components

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.mohdshayan.rowmark.core.KeyDebouncer
import com.mohdshayan.rowmark.data.prefs.Settings
import com.mohdshayan.rowmark.data.prefs.VolumeDownAction
import com.mohdshayan.rowmark.data.repo.CountSource
import com.mohdshayan.rowmark.input.KeyRouter

/**
 * Volume up, Enter, numpad Enter and the user's extra key count while this is in the composition;
 * volume down undoes, takes a row off or does nothing, as Settings says. Both the Counter and the
 * Pattern screen register it, so the ruler climbs while the pattern is on screen. Elsewhere the keys
 * reach the system untouched.
 */
@Composable
fun CountKeyHandler(settings: Settings, onCount: (CountSource) -> Unit, onUndo: () -> Unit, onMinus: () -> Unit) {
    DisposableEffect(settings.countWithKeys, settings.volumeDownAction, settings.extraCountKeycode) {
        val debouncer = KeyDebouncer()
        val mine: (KeyEvent) -> Boolean = handler@{ event ->
            if (!settings.countWithKeys) return@handler false
            val code = event.keyCode
            val counts = KeyRouter.countsRow(code, settings.extraCountKeycode)
            val down = code == KeyEvent.KEYCODE_VOLUME_DOWN
            if (!counts && !down) return@handler false
            if (down && settings.volumeDownAction == VolumeDownAction.NOTHING) return@handler false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && debouncer.accept(code, event.eventTime)) {
                if (counts) {
                    onCount(if (code == KeyEvent.KEYCODE_VOLUME_UP) CountSource.KEY else CountSource.REMOTE)
                } else if (settings.volumeDownAction == VolumeDownAction.UNDO) {
                    onUndo()
                } else {
                    onMinus()
                }
            }
            // ACTION_UP is consumed too, so no volume panel appears.
            true
        }
        KeyRouter.handler = mine
        // Only clear our own handler: during a screen transition the next screen has already registered
        // its handler, and the leaving screen must not take it down.
        onDispose { if (KeyRouter.handler === mine) KeyRouter.handler = null }
    }
}

/** Keeps the screen on while [enabled] and this is in the composition. */
@Composable
fun KeepScreenAwake(enabled: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
