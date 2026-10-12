package com.mohdshayan.rowmark

import android.app.Application
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Registered in the manifest as android:name=".App". Wires the service locator before any screen,
 * widget or tile can ask for it, builds the billing client once on the main thread, and routes every
 * count outcome (pad, key, remote, widget, tile) to the tick, bell and buzz.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.unlock
        // Load the bell now, so the first alert rings instead of arriving before SoundPool has decoded it.
        ServiceLocator.feedback.warmUp()
        ServiceLocator.appScope.launch {
            ServiceLocator.projects.outcomes.collect { ServiceLocator.feedback.onOutcome(it) }
        }
        ServiceLocator.appScope.launch { ServiceLocator.projects.closeIdleSessions() }
    }
}
