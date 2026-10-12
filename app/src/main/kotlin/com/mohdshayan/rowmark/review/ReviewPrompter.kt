package com.mohdshayan.rowmark.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import com.mohdshayan.rowmark.data.prefs.AppPrefs
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.flow.first

/**
 * The in-app review prompt, once, after the third closed session of ten or more rows, on returning to
 * Projects. Never in the first session, never from a button, never tied to buying. The Play Store app
 * does any network work; this app has no INTERNET permission.
 */
object ReviewPrompter {
    const val SESSIONS_NEEDED = 3
    const val ROWS_PER_SESSION = 10

    suspend fun maybePrompt(activity: Activity) {
        val prefs: AppPrefs = ServiceLocator.appPrefs
        if (prefs.current().reviewPrompted) return
        val qualifying = ServiceLocator.database.sessionDao().observeClosedWithAtLeast(ROWS_PER_SESSION).first()
        if (qualifying < SESSIONS_NEEDED) return
        prefs.setReviewPrompted(true)
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful && !activity.isFinishing) manager.launchReviewFlow(activity, task.result)
            }
        }
    }
}
