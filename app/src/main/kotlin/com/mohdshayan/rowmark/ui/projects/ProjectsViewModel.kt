package com.mohdshayan.rowmark.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.rowmark.core.CounterKind
import com.mohdshayan.rowmark.data.db.Counter
import com.mohdshayan.rowmark.data.db.Project
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectRow(val project: Project, val row: Counter?, val repeat: Counter?)

sealed class ProjectsState {
    object Loading : ProjectsState()
    object Error : ProjectsState()
    data class Ready(val rows: List<ProjectRow>) : ProjectsState()
}

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.projects
    private val prefs = ServiceLocator.appPrefs

    val state: StateFlow<ProjectsState> = combine(repo.observeProjects(), repo.observeAllCounters()) { projects, counters ->
        val byProject = counters.groupBy { it.projectId }
        ProjectsState.Ready(
            projects.map { p ->
                val mine = byProject[p.id].orEmpty()
                ProjectRow(p, mine.firstOrNull { it.kind == CounterKind.ROW.name } ?: mine.firstOrNull(), mine.firstOrNull { it.kind == CounterKind.REPEAT.name })
            },
        ) as ProjectsState
    }.catch { emit(ProjectsState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsState.Loading)

    /** The one-time card: after the first session of 20 or more rows, until dismissed or unlocked. */
    val showUnlockCard: StateFlow<Boolean> = combine(
        ServiceLocator.database.sessionDao().observeMaxRows(),
        prefs.settings.map { it.unlockCardShown },
    ) { maxRows, shown -> maxRows >= 20 && !shown }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Yarn and needles on this phone: after a relock they stay readable, so the stash button still opens them. */
    val stashCount: StateFlow<Int> = ServiceLocator.stash.observeItemCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun dismissCard() {
        viewModelScope.launch { prefs.setUnlockCardShown(true) }
    }
}
