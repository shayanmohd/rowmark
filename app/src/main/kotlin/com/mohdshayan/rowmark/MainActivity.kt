package com.mohdshayan.rowmark

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohdshayan.rowmark.data.prefs.ThemeMode
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.input.KeyRouter
import com.mohdshayan.rowmark.ui.nav.AppNav
import com.mohdshayan.rowmark.ui.nav.PendingNav
import com.mohdshayan.rowmark.ui.theme.AppTheme
import com.mohdshayan.rowmark.widget.RowWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Where the widget or the tile asked to go; AppNav consumes it. */
    private val pendingNav = MutableStateFlow<PendingNav?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            val settings by ServiceLocator.feedback.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(darkTheme = dark) {
                AppNav(pendingNav = pendingNav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        intent ?: return
        val unlockReason = intent.getStringExtra(EXTRA_OPEN_UNLOCK)
        val projectId = intent.getLongExtra(RowWidgetProvider.EXTRA_OPEN_PROJECT, 0L)
        when {
            unlockReason != null -> pendingNav.value = PendingNav.Unlock(unlockReason)
            projectId > 0 -> pendingNav.value = PendingNav.Counter(projectId)
        }
        intent.removeExtra(EXTRA_OPEN_UNLOCK)
        intent.removeExtra(RowWidgetProvider.EXTRA_OPEN_PROJECT)
    }

    override fun onResume() {
        super.onResume()
        // Google's advice: query purchases on every resume, so a pending payment that completed, a promo
        // code redeemed in the Play Store, a refund or a purchase on another phone shows up without a restart.
        ServiceLocator.unlock.refresh()
    }

    /** Volume keys and a remote's keys reach the open counter first; untouched keys go to the system. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        KeyRouter.dispatch(event) || super.dispatchKeyEvent(event)

    companion object {
        const val EXTRA_OPEN_UNLOCK = "open_unlock"
    }
}
