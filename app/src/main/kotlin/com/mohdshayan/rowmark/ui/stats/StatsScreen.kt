package com.mohdshayan.rowmark.ui.stats

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.core.DayRows
import com.mohdshayan.rowmark.core.SessionRecord
import com.mohdshayan.rowmark.core.StatsCalc
import com.mohdshayan.rowmark.data.db.Project
import com.mohdshayan.rowmark.data.db.Session
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.ui.components.EmptyState
import com.mohdshayan.rowmark.ui.components.Hairline
import com.mohdshayan.rowmark.ui.components.KeyValue
import com.mohdshayan.rowmark.ui.components.SectionLabel
import com.mohdshayan.rowmark.ui.components.SkeletonBlock
import com.mohdshayan.rowmark.ui.components.Teaser
import com.mohdshayan.rowmark.ui.components.UnlockGate
import com.mohdshayan.rowmark.ui.components.duration
import com.mohdshayan.rowmark.ui.components.relativeTime
import com.mohdshayan.rowmark.ui.components.shortDateTime
import com.mohdshayan.rowmark.ui.theme.RadiusSm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

sealed class StatsState {
    object Loading : StatsState()
    object Error : StatsState()
    data class Ready(val project: Project?, val sessions: List<Session>) : StatsState()
}

class StatsViewModel(app: Application, private val projectId: Long) : AndroidViewModel(app) {
    private val repo = ServiceLocator.projects
    val state: StateFlow<StatsState> = combine(repo.observeProject(projectId), repo.observeSessions(projectId)) { p, s -> StatsState.Ready(p, s) as StatsState }
        .catch { emit(StatsState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState.Loading)
    private val _ended = MutableStateFlow(0)
    val ended: StateFlow<Int> = _ended.asStateFlow()
    fun endSession() { viewModelScope.launch { if (repo.endSessionNow(projectId)) _ended.value += 1 } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(projectId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: StatsViewModel = viewModel(key = "stats-$projectId") { StatsViewModel(app, projectId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ended by viewModel.ended.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val endedText = stringResource(R.string.stats_session_ended)
    LaunchedEffect(ended) { if (ended > 0) snackbar.showSnackbar(endedText) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) } },
            )
        },
    ) { padding ->
        when (val s = state) {
            StatsState.Loading -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth().height(48.dp)); SkeletonBlock(Modifier.fillMaxWidth().height(48.dp)); SkeletonBlock(Modifier.fillMaxWidth().height(160.dp))
            }
            StatsState.Error -> EmptyState(title = stringResource(R.string.stats_error), body = "", modifier = Modifier.fillMaxSize().padding(padding))
            is StatsState.Ready -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                // Total rows and last worked stay visible whatever the tier.
                KeyValue(stringResource(R.string.stats_total_rows), (s.project?.totalRows ?: 0).toString())
                Hairline(Modifier.fillMaxWidth())
                KeyValue(stringResource(R.string.stats_last_worked), s.project?.lastCountAt?.let { relativeTime(it) } ?: stringResource(R.string.projects_never_worked))
                Hairline(Modifier.fillMaxWidth())
                UnlockGate(
                    "stats",
                    locked = { open ->
                        Teaser(sentence = stringResource(R.string.stats_teaser), onUnlock = open, modifier = Modifier.fillMaxWidth()) {
                            SampleBars()
                        }
                    },
                ) { FullStats(s, viewModel) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FullStats(s: StatsState.Ready, viewModel: StatsViewModel) {
    val records = s.sessions.map { SessionRecord(it.startedAt, it.endedAt, it.activeMs, it.rows) }
    if (records.isEmpty()) {
        Spacer(Modifier.height(32.dp))
        EmptyState(title = stringResource(R.string.stats_empty), body = "")
        return
    }
    val perDay = StatsCalc.rowsPerDay(records, 14, System.currentTimeMillis(), ZoneId.systemDefault())
    val perHour = StatsCalc.rowsPerHour(records)
    val perWorkingDay = StatsCalc.averageRowsPerWorkingDay(perDay)
    val finish = StatsCalc.finishEstimateDays(s.project?.targetRows, s.project?.totalRows ?: 0, perWorkingDay)
    KeyValue(stringResource(R.string.stats_active_time), duration(StatsCalc.totalActiveMs(records)))
    Hairline(Modifier.fillMaxWidth())
    KeyValue(stringResource(R.string.stats_rows_per_hour), perHour?.let { "%.0f".format(it) } ?: stringResource(R.string.stats_not_enough))
    Hairline(Modifier.fillMaxWidth())
    KeyValue(
        stringResource(R.string.stats_finish),
        when {
            s.project?.targetRows == null -> stringResource(R.string.stats_finish_none)
            finish == null -> stringResource(R.string.stats_not_enough)
            finish == 0 -> stringResource(R.string.stats_finish_today)
            else -> stringResource(R.string.stats_finish_days, finish)
        },
    )
    SectionLabel(stringResource(R.string.stats_rows_per_day))
    DayBars(perDay, Modifier.fillMaxWidth().height(140.dp))
    SectionLabel(stringResource(R.string.stats_sessions))
    val open = s.sessions.firstOrNull { it.endedAt == null }
    if (open != null) {
        OutlinedButton(onClick = viewModel::endSession, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.stats_end_session)) }
        Spacer(Modifier.height(8.dp))
    }
    for (session in s.sessions) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.stats_session_line, session.rows, shortDateTime(session.startedAt), session.rows, duration(session.activeMs)), style = MaterialTheme.typography.bodyLarge)
                if (session.endedAt == null) Text(stringResource(R.string.stats_session_open), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/** Fourteen bars, no axis chrome: the tallest day sets the scale, today is in Fuchsia. */
@Composable
private fun DayBars(perDay: List<DayRows>, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val description = stringResource(R.string.stats_chart_description)
    val max = (perDay.maxOfOrNull { it.rows } ?: 0).coerceAtLeast(1)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = perDay.size
            val gap = 6.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            val radius = RadiusSm.toPx() / 2
            perDay.forEachIndexed { i, d ->
                val h = size.height * (d.rows.toFloat() / max)
                val x = i * (barW + gap)
                val color = if (i == n - 1) accent else if (d.rows == 0) quiet else accent.copy(alpha = 0.55f)
                val bh = if (d.rows == 0) 3.dp.toPx() else h
                drawRoundRect(color, topLeft = Offset(x, size.height - bh), size = Size(barW, bh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(perDay.firstOrNull()?.date?.let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)}" } ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(perDay.lastOrNull()?.date?.let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)}" } ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The teaser's preview: a believable fortnight of rows, labelled as a sample. */
@Composable
private fun SampleBars() {
    val sample = listOf(0, 14, 22, 0, 9, 31, 18, 0, 0, 27, 12, 40, 6, 19)
    val today = java.time.LocalDate.now()
    val bars = sample.mapIndexed { i, rows -> DayRows(today.minusDays((sample.size - 1 - i).toLong()), rows) }
    Column(Modifier.widthIn(max = 420.dp)) {
        Text(stringResource(R.string.stats_rows_per_day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        DayBars(bars, Modifier.fillMaxWidth().height(120.dp))
    }
}
