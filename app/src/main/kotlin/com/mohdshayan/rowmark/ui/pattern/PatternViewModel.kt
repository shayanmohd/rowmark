package com.mohdshayan.rowmark.ui.pattern

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.rowmark.core.CounterKind
import com.mohdshayan.rowmark.data.repo.CountSource
import com.mohdshayan.rowmark.core.RulerMath
import com.mohdshayan.rowmark.core.RulerPos
import com.mohdshayan.rowmark.data.db.Markup
import com.mohdshayan.rowmark.data.db.Pattern
import com.mohdshayan.rowmark.data.repo.ImportResult
import com.mohdshayan.rowmark.data.repo.PatternKind
import com.mohdshayan.rowmark.di.ServiceLocator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PatternNote { NONE, ADDED, PASSWORD, UNREADABLE, RENDER_ERROR }

@OptIn(ExperimentalCoroutinesApi::class)
class PatternViewModel(app: Application, val projectId: Long) : AndroidViewModel(app) {
    private val repo = ServiceLocator.patterns
    private val projects = ServiceLocator.projects

    val patterns: StateFlow<List<Pattern>?> = repo.observePatterns(projectId)
        .map<List<Pattern>, List<Pattern>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedId = MutableStateFlow(0L)
    val selected: StateFlow<Pattern?> = combine(patterns, _selectedId) { list, id ->
        list?.firstOrNull { it.id == id } ?: list?.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val markup: StateFlow<List<Markup>> = selected.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.observeMarkup(p.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The Row counter's value, for the corner chip. */
    val rowValue: StateFlow<Int> = projects.observeCounters(projectId)
        .map { cs -> (cs.firstOrNull { it.kind == CounterKind.ROW.name } ?: cs.firstOrNull())?.value ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Total rows drives the ruler, not the Row counter: Total rows never rolls over, so a count is
     * always one step forward and an undo or minus one step back, even when Row returns from 18 to 1.
     */
    private val totalRows: StateFlow<Int?> = projects.observeProject(projectId)
        .map { it?.totalRows }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    private val _highlighter = MutableStateFlow(false)
    val highlighter: StateFlow<Boolean> = _highlighter.asStateFlow()

    private val _note = MutableStateFlow(PatternNote.NONE)
    val note: StateFlow<PatternNote> = _note.asStateFlow()

    private var lastRow: Int? = null

    init {
        // The ruler steps per count: up for charts, down for written patterns, across pages.
        viewModelScope.launch {
            totalRows.collect { total ->
                if (total == null) return@collect
                val prev = lastRow
                lastRow = total
                val p = selected.value ?: return@collect
                if (prev == null || prev == total) return@collect
                val next = RulerMath.step(RulerPos(p.rulerPage, p.rulerTop), p.rulerHeight, p.readsUpward, p.pageCount, total - prev)
                repo.setRuler(p.id, next.page, next.top, p.rulerHeight)
                _page.value = next.page
            }
        }
        viewModelScope.launch { selected.collect { p -> if (p != null) _page.value = p.rulerPage.coerceIn(0, p.pageCount - 1) } }
    }

    fun select(id: Long) { _selectedId.value = id }
    fun setPage(p: Int) { val max = (selected.value?.pageCount ?: 1) - 1; _page.value = p.coerceIn(0, max) }
    fun toggleHighlighter() { _highlighter.value = !_highlighter.value }
    fun clearNote() { _note.value = PatternNote.NONE }
    fun renderFailed() { _note.value = PatternNote.RENDER_ERROR }

    fun add(uri: Uri, kind: PatternKind, title: String) {
        viewModelScope.launch {
            _note.value = when (val r = repo.import(projectId, uri, kind, title)) {
                is ImportResult.Added -> { _selectedId.value = r.pattern.id; PatternNote.ADDED }
                ImportResult.PasswordProtected -> PatternNote.PASSWORD
                ImportResult.Unreadable -> PatternNote.UNREADABLE
            }
        }
    }

    fun delete(p: Pattern) { viewModelScope.launch { repo.deletePattern(p) } }

    fun stepRuler(up: Boolean) {
        val p = selected.value ?: return
        viewModelScope.launch {
            val next = RulerMath.step(RulerPos(p.rulerPage, p.rulerTop), p.rulerHeight, up, p.pageCount, 1)
            repo.setRuler(p.id, next.page, next.top, p.rulerHeight)
            _page.value = next.page
        }
    }

    /** Counting from the pattern screen: the pad is not here, but the keys, a remote and this button are. */
    fun countRow(source: CountSource) { viewModelScope.launch { projects.countRow(projectId, source) } }
    fun minusRow() { viewModelScope.launch { projects.minusRow(projectId, CountSource.TAP) } }
    fun undo() { viewModelScope.launch { projects.undo(projectId) } }

    fun setRuler(top: Float, height: Float) {
        val p = selected.value ?: return
        viewModelScope.launch { repo.setRuler(p.id, _page.value, top, height) }
    }

    fun toggleDirection() {
        val p = selected.value ?: return
        viewModelScope.launch { repo.setDirection(p.id, !p.readsUpward) }
    }

    fun addStroke(points: List<Pair<Float, Float>>) {
        val p = selected.value ?: return
        if (points.size < 2) return
        viewModelScope.launch { repo.addStroke(p.id, _page.value, points) }
    }

    fun undoStroke() { val p = selected.value ?: return; viewModelScope.launch { repo.undoStroke(p.id) } }

    suspend fun render(p: Pattern, page: Int, widthPx: Int): Bitmap? = repo.renderPage(p, page, widthPx)

    fun sampleChart(): Bitmap? = repo.sampleChart()
}
