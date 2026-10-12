package com.mohdshayan.rowmark.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mohdshayan.rowmark.MainActivity
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.data.repo.CountSource
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The home screen widget: the last counted project, its row, and a plus one button. */
class RowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ServiceLocator.init(context)
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COUNT) {
            ServiceLocator.init(context)
            val projectId = intent.getLongExtra(EXTRA_PROJECT, 0L)
            val result = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                try {
                    if (projectId > 0) ServiceLocator.projects.countRow(projectId, CountSource.WIDGET)
                    render(context)
                } finally {
                    result.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_COUNT = "com.mohdshayan.rowmark.WIDGET_COUNT"
        const val EXTRA_PROJECT = "project"
        const val EXTRA_OPEN_PROJECT = "open_project"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        /** Repositories call this after every change; cheap when no widget is placed. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, RowWidgetProvider::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(context) }
        }

        suspend fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, RowWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val prefs = ServiceLocator.appPrefs
            val lastId = prefs.current().lastProjectId
            val project = if (lastId > 0) ServiceLocator.projects.project(lastId) else null
            val row = project?.let { ServiceLocator.projects.rowCounter(it.id) }
            val views = RemoteViews(context.packageName, R.layout.widget_row)
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (project != null) putExtra(EXTRA_OPEN_PROJECT, project.id)
            }
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            when {
                lastId > 0 && project == null -> {
                    views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_deleted))
                    views.setTextViewText(R.id.widget_value, "")
                    views.setTextViewText(R.id.widget_label, "")
                    views.setViewVisibility(R.id.widget_plus, android.view.View.GONE)
                }
                project == null || row == null -> {
                    views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_empty))
                    views.setTextViewText(R.id.widget_value, "")
                    views.setTextViewText(R.id.widget_label, "")
                    views.setViewVisibility(R.id.widget_plus, android.view.View.GONE)
                }
                else -> {
                    views.setTextViewText(R.id.widget_name, project.name)
                    views.setTextViewText(R.id.widget_value, row.value.toString())
                    views.setTextViewText(R.id.widget_label, row.name)
                    views.setViewVisibility(R.id.widget_plus, android.view.View.VISIBLE)
                    val count = Intent(context, RowWidgetProvider::class.java).apply {
                        action = ACTION_COUNT
                        putExtra(EXTRA_PROJECT, project.id)
                    }
                    views.setOnClickPendingIntent(R.id.widget_plus, PendingIntent.getBroadcast(context, 2, count, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
