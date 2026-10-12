package com.mohdshayan.rowmark.di

import android.content.Context
import com.mohdshayan.rowmark.billing.UnlockRepository
import com.mohdshayan.rowmark.data.db.AppDatabase
import com.mohdshayan.rowmark.data.prefs.AppPrefs
import com.mohdshayan.rowmark.data.prefs.EntitlementStore
import com.mohdshayan.rowmark.data.repo.BackupRepository
import com.mohdshayan.rowmark.data.repo.PatternRepository
import com.mohdshayan.rowmark.data.repo.ProjectRepository
import com.mohdshayan.rowmark.data.repo.StashRepository
import com.mohdshayan.rowmark.feedback.Feedback
import com.mohdshayan.rowmark.widget.RowWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. No Hilt: a single-module app does not need
 * the method count or the build time. Initialised in App.onCreate, and idempotent
 * so a widget receiver or tile service may call init() again with whatever context it has.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context =
        appContext ?: error("ServiceLocator.init() must be called before use")

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }

    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }

    /** Outlives every screen, so a purchase result that lands mid-rotation is never lost. */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    /** The one BillingClient for the process, with this phone's copy of Google Play's answer. */
    val unlock: UnlockRepository by lazy {
        UnlockRepository(context = ctx(), store = EntitlementStore(ctx()), scope = appScope)
    }

    val feedback: Feedback by lazy { Feedback(ctx(), appPrefs, appScope) }

    val projects: ProjectRepository by lazy {
        ProjectRepository(database, appPrefs) { RowWidgetProvider.refresh(ctx()) }
    }

    val patterns: PatternRepository by lazy { PatternRepository(ctx(), database) }

    val stash: StashRepository by lazy { StashRepository(database) }

    val backup: BackupRepository by lazy { BackupRepository(ctx(), database, appPrefs, patterns) }
}
