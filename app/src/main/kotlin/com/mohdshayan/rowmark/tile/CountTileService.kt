package com.mohdshayan.rowmark.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mohdshayan.rowmark.MainActivity
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.data.repo.CountSource
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: counts a row on the tile's project when unlocked, else opens the unlock screen. */
class CountTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        ServiceLocator.init(this)
        scope.launch { update() }
    }

    override fun onClick() {
        ServiceLocator.init(this)
        if (!ServiceLocator.unlock.state.value.unlocked) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_UNLOCK, "tile")
            }
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        scope.launch {
            val prefs = ServiceLocator.appPrefs.current()
            val id = prefs.tileProjectId.takeIf { it > 0 } ?: prefs.lastProjectId
            if (id > 0) ServiceLocator.projects.countRow(id, CountSource.TILE)
            update()
        }
    }

    private suspend fun update() {
        val tile = qsTile ?: return
        val prefs = ServiceLocator.appPrefs.current()
        val id = prefs.tileProjectId.takeIf { it > 0 } ?: prefs.lastProjectId
        val project = if (id > 0) ServiceLocator.projects.project(id) else null
        val row = project?.let { ServiceLocator.projects.rowCounter(it.id) }
        tile.label = getString(R.string.tile_label)
        tile.subtitle = when {
            !ServiceLocator.unlock.state.value.unlocked -> getString(R.string.tile_locked)
            project == null || row == null -> getString(R.string.widget_empty)
            else -> getString(R.string.tile_subtitle, project.name, row.value)
        }
        tile.state = if (project != null || !ServiceLocator.unlock.state.value.unlocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
