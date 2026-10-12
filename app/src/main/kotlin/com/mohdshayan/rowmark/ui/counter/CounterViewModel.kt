package com.mohdshayan.rowmark.ui.counter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.rowmark.core.CounterKind
import com.mohdshayan.rowmark.core.ScheduleEngine
import com.mohdshayan.rowmark.data.db.Counter
import com.mohdshayan.rowmark.data.db.Project
import com.mohdshayan.rowmark.data.db.Schedule
import com.mohdshayan.rowmark.data.repo.CountSource
import com.mohdshayan.rowmark.data.repo.kindEnum
import com.mohdshayan.rowmark.data.repo.spec
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the banner shows after a count: a due alert or shaping step, until the next count or a tap. */
data class Banner(val text: String, val isSchedule: Boolean)

sealed class CounterState {
    object Loading : CounterState()
    object Deleted : CounterState()
    data class Ready(
        val project: Project,
        val counters: List<Counter>,
        val schedules: List<Schedule>,
        val patternCount: Int,
    ) : CounterState() {
        val row: Counter? get() = counters.firstOrNull { it.kindEnum() == CounterKind.ROW } ?: counters.firstOrNull()
        val repeat: Counter? get() = counters.firstOrNull { it.kindEnum() == CounterKind.REPEAT }
        val section: Counter? get() = counters.firstOrNull { it.kindEnum() == CounterKind.SECTION }
        val others: List<Counter> get() = counters.filter { it.id != row?.id && it.id != repeat?.id && it.id != section?.id }
    }
}

class CounterViewModel(app: Application, private val projectId: Long) : AndroidViewModel(app) {
    private val repo = ServiceLocator.projects
    private val prefs = ServiceLocator.appPrefs

    val state: StateFlow<CounterState> = combine(
        repo.observeProject(projectId),
        repo.observeCounters(projectId),
        repo.observeSchedules(projectId),
        ServiceLocator.patterns.observePatternCount(projectId),
    ) { project, counters, schedules, patterns ->
        if (project == null) CounterState.Deleted else CounterState.Ready(project, counters, schedules, patterns)
    }.catch { emit(CounterState.Deleted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CounterState.Loading)

    val settings = ServiceLocator.feedback.settings

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _undone = MutableStateFlow(0)
    val undone: StateFlow<Int> = _undone.asStateFlow()

    private val _showKeyHint = MutableStateFlow(false)
    val showKeyHint: StateFlow<Boolean> = _showKeyHint.asStateFlow()

    init {
        viewModelScope.launch { prefs.setLastProjectId(projectId) }
        viewModelScope.launch {
            repo.outcomes.collect { o ->
                if (o.projectId != projectId) return@collect
                if (o.undone) { _banner.value = null; _undone.value += 1; return@collect }
                val alert = o.result.alerts.firstOrNull()
                val due = o.dueSchedules.firstOrNull()
                _banner.value = when {
                    due != null -> Banner(scheduleText(due.schedule, due.step), isSchedule = true)
                    alert != null -> Banner(alert.label.ifBlank { app.getString(com.mohdshayan.rowmark.R.string.counter_pad_label) }, isSchedule = false)
                    else -> null
                }
            }
        }
    }

    private fun scheduleText(s: Schedule, step: Int): String =
        getApplication<Application>().getString(com.mohdshayan.rowmark.R.string.counter_schedule_due, s.label.ifBlank { s.action }, step, s.times)

    fun countRow(source: CountSource) {
        viewModelScope.launch {
            val hintWasUp = _showKeyHint.value
            repo.countRow(projectId, source)
            val s = prefs.current()
            if (hintWasUp) {
                _showKeyHint.value = false
            } else if (!s.keyHintShown && s.countWithKeys && source == CountSource.TAP) {
                _showKeyHint.value = true
                prefs.setKeyHintShown(true)
            }
        }
    }

    fun count(counterId: Long) { viewModelScope.launch { repo.count(projectId, counterId, CountSource.TAP) } }
    fun minus(counterId: Long) { viewModelScope.launch { repo.minus(projectId, counterId, CountSource.TAP) } }
    fun minusRow() { viewModelScope.launch { repo.minusRow(projectId, CountSource.TAP) } }
    fun undo() { viewModelScope.launch { repo.undo(projectId) } }
    fun dismissBanner() { _banner.value = null }
    fun dismissKeyHint() { _showKeyHint.value = false }

    fun deleteProject(onDone: () -> Unit) {
        viewModelScope.launch { repo.deleteProject(projectId); onDone() }
    }

    fun saveSchedule(schedule: Schedule) { viewModelScope.launch { repo.saveSchedule(schedule.copy(projectId = projectId)) } }
    fun deleteSchedule(schedule: Schedule) { viewModelScope.launch { repo.deleteSchedule(schedule) } }

    /** "Raglan increase next at row 18" or "finished", for the schedule list under the drum. */
    fun scheduleStatus(s: Schedule, ready: CounterState.Ready): Pair<Int?, Boolean> {
        val row = s.counterId?.let { id -> ready.counters.firstOrNull { it.id == id }?.value } ?: ready.project.totalRows
        val spec = s.spec()
        return ScheduleEngine.nextDueRow(spec, row + 1) to ScheduleEngine.isFinished(spec, row)
    }
}
