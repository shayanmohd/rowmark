package com.mohdshayan.rowmark.ui.projectedit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.rowmark.core.CounterKind
import com.mohdshayan.rowmark.core.CounterRules
import com.mohdshayan.rowmark.data.db.Counter
import com.mohdshayan.rowmark.data.db.Project
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A counter in the editor. New counters carry negative ids until saved; the repository maps them. */
data class CounterDraft(
    val id: Long,
    val name: String,
    val kind: CounterKind,
    val value: Int = 0,
    val length: String = "",
    val parentId: Long? = null,
    val resetsWithRow: Boolean = false,
    val alertEvery: String = "",
    val alertLabel: String = "",
) {
    val lengthInt: Int? get() = length.trim().toIntOrNull()
    val alertInt: Int? get() = alertEvery.trim().toIntOrNull()
    val lengthValid: Boolean get() = length.isBlank() || CounterRules.validLength(lengthInt)
    val alertValid: Boolean get() = alertEvery.isBlank() || (alertInt != null && CounterRules.validAlert(alertInt))
}

data class EditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val project: Project? = null,
    val name: String = "",
    val craft: String = "knitting",
    val status: String = "active",
    val needle: String = "",
    val yarn: String = "",
    val target: String = "",
    val notes: String = "",
    val coverFile: String? = null,
    val counters: List<CounterDraft> = emptyList(),
    val tried: Boolean = false,
    val saving: Boolean = false,
) {
    val nameValid: Boolean get() = name.isNotBlank()
    val valid: Boolean get() = nameValid && counters.all { it.lengthValid && it.alertValid && it.name.isNotBlank() }
}

class ProjectEditViewModel(app: Application, private val projectId: Long) : AndroidViewModel(app) {
    private val repo = ServiceLocator.projects
    private val patterns = ServiceLocator.patterns
    private var nextTempId = -1L

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (projectId == 0L) {
                _state.update {
                    it.copy(
                        loaded = true, isNew = true,
                        counters = listOf(CounterDraft(nextTempId--, app.getString(com.mohdshayan.rowmark.R.string.kind_row), CounterKind.ROW)),
                    )
                }
            } else {
                val p = repo.project(projectId)
                val cs = repo.counters(projectId)
                _state.update {
                    it.copy(
                        loaded = true, isNew = false, project = p,
                        name = p?.name ?: "", craft = p?.craft ?: "knitting", status = p?.status ?: "active",
                        needle = p?.needleOrHook ?: "", yarn = p?.yarnText ?: "", target = p?.targetRows?.toString() ?: "",
                        notes = p?.notes ?: "", coverFile = p?.coverFile,
                        counters = cs.map { c ->
                            CounterDraft(c.id, c.name, runCatching { CounterKind.valueOf(c.kind) }.getOrDefault(CounterKind.CUSTOM), c.value, c.length?.toString() ?: "", c.parentId, c.resetsWithRow, c.alertEvery?.toString() ?: "", c.alertLabel)
                        },
                    )
                }
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setCraft(v: String) = _state.update { it.copy(craft = v) }
    fun setStatus(v: String) = _state.update { it.copy(status = v) }
    fun setNeedle(v: String) = _state.update { it.copy(needle = v) }
    fun setYarn(v: String) = _state.update { it.copy(yarn = v) }
    fun setTarget(v: String) = _state.update { it.copy(target = v.filter(Char::isDigit).take(5)) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun removeCover() = _state.update { it.copy(coverFile = null) }

    fun setCover(uri: Uri) {
        viewModelScope.launch {
            val name = patterns.importPhoto(uri) ?: return@launch
            _state.update { it.copy(coverFile = name) }
        }
    }

    fun addCounter(kind: CounterKind) {
        val app = getApplication<Application>()
        val name = when (kind) {
            CounterKind.ROW -> app.getString(com.mohdshayan.rowmark.R.string.kind_row)
            CounterKind.REPEAT -> app.getString(com.mohdshayan.rowmark.R.string.kind_repeat)
            CounterKind.SECTION -> app.getString(com.mohdshayan.rowmark.R.string.kind_section)
            CounterKind.STITCH -> app.getString(com.mohdshayan.rowmark.R.string.kind_stitch)
            CounterKind.CUSTOM -> app.getString(com.mohdshayan.rowmark.R.string.kind_custom)
        }
        _state.update { s ->
            val row = s.counters.firstOrNull { it.kind == CounterKind.ROW }
            val repeat = s.counters.firstOrNull { it.kind == CounterKind.REPEAT }
            val draft = CounterDraft(
                id = nextTempId--, name = name, kind = kind,
                value = if (kind == CounterKind.REPEAT || kind == CounterKind.SECTION) 1 else 0,
                resetsWithRow = kind == CounterKind.STITCH,
            )
            // A new Repeat becomes the Row's parent, a new Section the Repeat's: the common nesting.
            var counters = s.counters + draft
            if (kind == CounterKind.REPEAT && row != null && row.parentId == null) counters = counters.map { if (it.id == row.id) it.copy(parentId = draft.id) else it }
            if (kind == CounterKind.SECTION && repeat != null && repeat.parentId == null) counters = counters.map { if (it.id == repeat.id) it.copy(parentId = draft.id) else it }
            s.copy(counters = counters)
        }
    }

    fun updateCounter(draft: CounterDraft) = _state.update { s -> s.copy(counters = s.counters.map { if (it.id == draft.id) draft else it }) }

    fun removeCounter(id: Long) = _state.update { s ->
        s.copy(counters = s.counters.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = null) else it })
    }

    /** Saves, then reports the project id. Invalid input marks the fields and stays. */
    fun save(onSaved: (Long, Boolean) -> Unit) {
        val s = _state.value
        if (!s.valid) { _state.update { it.copy(tried = true) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val base = s.project ?: Project(uuid = "", name = "", createdAt = 0, updatedAt = 0)
            val project = base.copy(
                name = s.name.trim(), craft = s.craft, status = s.status, needleOrHook = s.needle.trim(), yarnText = s.yarn.trim(),
                targetRows = s.target.toIntOrNull()?.takeIf { it > 0 }, notes = s.notes.trim(), coverFile = s.coverFile,
            )
            val counters = s.counters.map { d ->
                Counter(
                    id = if (d.id > 0) d.id else d.id, projectId = projectId, name = d.name.trim(), kind = d.kind.name, value = d.value,
                    length = d.lengthInt, parentId = d.parentId, resetsWithRow = d.resetsWithRow && d.kind == CounterKind.STITCH,
                    alertEvery = d.alertInt, alertLabel = d.alertLabel.trim(),
                )
            }
            val id = repo.saveProject(project, counters)
            onSaved(id, s.isNew)
        }
    }
}
