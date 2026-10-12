package com.mohdshayan.rowmark.ui.counter

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.core.Access
import com.mohdshayan.rowmark.core.CounterKind
import com.mohdshayan.rowmark.core.Gates
import com.mohdshayan.rowmark.data.db.Counter
import com.mohdshayan.rowmark.data.db.Schedule
import com.mohdshayan.rowmark.data.repo.CountSource
import com.mohdshayan.rowmark.data.repo.kindEnum
import com.mohdshayan.rowmark.ui.components.CountKeyHandler
import com.mohdshayan.rowmark.ui.components.Drum
import com.mohdshayan.rowmark.ui.components.KeepScreenAwake
import com.mohdshayan.rowmark.ui.components.LockedIcon
import com.mohdshayan.rowmark.ui.components.ReadOnlyNote
import com.mohdshayan.rowmark.ui.components.EmptyState
import com.mohdshayan.rowmark.ui.components.Hairline
import com.mohdshayan.rowmark.ui.components.LocalUnlock
import com.mohdshayan.rowmark.ui.components.SectionLabel
import com.mohdshayan.rowmark.ui.components.SkeletonBlock
import com.mohdshayan.rowmark.ui.components.lockedAction
import com.mohdshayan.rowmark.ui.pattern.PatternPane
import com.mohdshayan.rowmark.ui.theme.RadiusLg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    projectId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenPattern: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: CounterViewModel = viewModel(key = "counter-$projectId") { CounterViewModel(app, projectId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val undone by viewModel.undone.collectAsStateWithLifecycle()
    val showKeyHint by viewModel.showKeyHint.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val access = LocalUnlock.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var scheduleEditor by remember { mutableStateOf<Schedule?>(null) }
    val undoneText = stringResource(R.string.counter_undone)

    KeepScreenAwake(settings.keepAwake)
    CountKeyHandler(settings, onCount = viewModel::countRow, onUndo = viewModel::undo, onMinus = viewModel::minusRow)
    LaunchedEffect(undone) { if (undone > 0) snackbar.showSnackbar(undoneText) }

    val ready = state as? CounterState.Ready
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(ready?.project?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) } },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = onOpenPattern) { LockedIcon(Icons.Rounded.MenuBook, stringResource(R.string.counter_pattern), locked = !access.unlocked) }
                        IconButton(onClick = onOpenStats) { LockedIcon(Icons.Rounded.Timeline, stringResource(R.string.counter_stats), locked = !access.unlocked) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.counter_more)) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.counter_edit)) }, onClick = { menuOpen = false; onEdit() })
                                val addSchedule = lockedAction("schedules") { scheduleEditor = Schedule(projectId = projectId) }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.counter_add_schedule)) },
                                    trailingIcon = { if (!access.unlocked) Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.common_locked)) },
                                    onClick = { menuOpen = false; addSchedule() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.counter_delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            CounterState.Loading -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                SkeletonBlock(Modifier.size(width = 160.dp, height = 120.dp))
                Spacer(Modifier.height(32.dp))
                SkeletonBlock(Modifier.fillMaxWidth().weight(1f))
            }
            CounterState.Deleted -> EmptyState(
                title = stringResource(R.string.counter_deleted),
                body = "",
                modifier = Modifier.fillMaxSize().padding(padding),
                actionLabel = stringResource(R.string.counter_back),
                onAction = onBack,
            )
            is CounterState.Ready -> BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val twoPane = maxWidth >= 600.dp
                // A short screen (landscape phone) drops the space held for the banner and shrinks the pad.
                val compact = maxHeight < 560.dp
                if (twoPane) {
                    Row(Modifier.fillMaxSize()) {
                        CounterBody(s, banner, showKeyHint, viewModel, access.unlocked, Modifier.weight(1f).fillMaxHeight(), compact = compact, onEditSchedule = { scheduleEditor = it })
                        Box(Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp, bottom = 16.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                            if (access.unlocked && s.patternCount > 0) {
                                PatternPane(projectId = projectId)
                            } else {
                                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                                    Text(stringResource(R.string.counter_notes_title), style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        s.project.notes.ifBlank { stringResource(R.string.counter_notes_empty) },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (s.project.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    CounterBody(s, banner, showKeyHint, viewModel, access.unlocked, Modifier.fillMaxSize(), compact = compact, onEditSchedule = { scheduleEditor = it })
                }
            }
        }
    }

    if (confirmDelete && ready != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.counter_delete)) },
            text = { Text(stringResource(R.string.counter_delete_confirm, ready.project.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteProject(onBack) }) {
                    Text(stringResource(R.string.counter_delete_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.counter_cancel)) } },
        )
    }
    val editing = scheduleEditor
    if (editing != null && ready != null) {
        ScheduleDialog(
            schedule = editing,
            counters = ready.counters,
            onSave = { viewModel.saveSchedule(it); scheduleEditor = null },
            onDelete = if (editing.id != 0L) ({ viewModel.deleteSchedule(editing); scheduleEditor = null }) else null,
            onDismiss = { scheduleEditor = null },
        )
    }
}

@Composable
private fun CounterBody(
    s: CounterState.Ready,
    banner: Banner?,
    showKeyHint: Boolean,
    viewModel: CounterViewModel,
    unlocked: Boolean,
    modifier: Modifier,
    compact: Boolean,
    onEditSchedule: (Schedule) -> Unit,
) {
    val access = LocalUnlock.current
    val seeUnlock = { access.open("schedules") }
    val row = s.row
    val repeat = s.repeat
    val section = s.section
    val rowLabel = row?.name ?: stringResource(R.string.kind_row)
    val talkback = if (repeat != null && repeat.length != null) {
        stringResource(R.string.counter_talkback_repeat, rowLabel, row?.value ?: 0, repeat.name, repeat.value, repeat.length)
    } else {
        stringResource(R.string.counter_talkback, rowLabel, row?.value ?: 0)
    }
    Column(modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            // The banner: an alert or a shaping step, in Fuchsia, dismissed by the next count or a tap.
            if (banner != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(banner.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::dismissBanner) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.counter_alert_dismiss), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                Spacer(Modifier.height(if (compact) 4.dp else 52.dp))
            }
            Spacer(Modifier.height(8.dp))
            // The one memorable thing: the big row drum, with the smaller repeat and section drums beside it.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                if (row != null) {
                    Drum(value = row.value, label = row.name, large = true, compact = compact, suffix = row.length?.let { stringResource(R.string.counter_of_length, it) }, contentDescription = talkback)
                }
                if (repeat != null || section != null) {
                    Spacer(Modifier.width(20.dp))
                    val sectionDrum: @Composable () -> Unit = {
                        if (section != null) Drum(value = section.value, label = section.name, delayMs = 160, suffix = section.length?.let { stringResource(R.string.counter_of_length, it) })
                    }
                    val repeatDrum: @Composable () -> Unit = {
                        if (repeat != null) Drum(value = repeat.value, label = repeat.name, delayMs = 80, suffix = repeat.length?.let { stringResource(R.string.counter_of_length, it) })
                    }
                    if (compact) {
                        // Side by side on a short screen, so the drum row stays within the landscape height.
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 6.dp)) {
                            repeatDrum()
                            if (section != null && repeat != null) Spacer(Modifier.width(16.dp))
                            sectionDrum()
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 6.dp)) {
                            sectionDrum()
                            if (section != null && repeat != null) Spacer(Modifier.height(12.dp))
                            repeatDrum()
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val target = s.project.targetRows
            Text(
                if (target != null) stringResource(R.string.counter_target_rows, s.project.totalRows, target) else pluralStringResource(R.plurals.counter_total_rows, s.project.totalRows, s.project.totalRows),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (c in s.others) {
                SmallCounterRow(c, onPlus = { viewModel.count(c.id) }, onMinus = { viewModel.minus(c.id) })
            }
            if (s.schedules.isNotEmpty()) {
                SectionLabel(stringResource(R.string.counter_schedules), Modifier.fillMaxWidth())
                val scheduleAccess = Gates.scheduleAccess(unlocked, s.schedules.size)
                if (scheduleAccess == Access.READ_ONLY) {
                    ReadOnlyNote(stringResource(R.string.schedule_read_only), onSeeUnlock = seeUnlock, modifier = Modifier.fillMaxWidth())
                }
                for (sch in s.schedules) {
                    val (next, finished) = viewModel.scheduleStatus(sch, s)
                    val label = sch.label.ifBlank { sch.action }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = scheduleAccess == Access.FULL, role = Role.Button) { onEditSchedule(sch) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.schedule_summary, label, sch.every, sch.times, sch.startRow), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    finished -> stringResource(R.string.counter_schedule_done, label)
                                    next != null -> stringResource(R.string.counter_schedule_next, label, next)
                                    else -> stringResource(R.string.counter_schedule_done, label)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Hairline(Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // The count pad: the lower half of the screen, every tap a row. Minus and undo sit at its foot.
        CountPad(
            hint = when {
                showKeyHint -> stringResource(R.string.counter_key_hint)
                (row?.value ?: 0) == 0 -> stringResource(R.string.counter_empty_hint)
                else -> null
            },
            onCount = { viewModel.countRow(CountSource.TAP) },
            onMinus = viewModel::minusRow,
            onUndo = viewModel::undo,
            // A weighted share, capped tall; on a short landscape screen a fixed height, so the pad stays
            // a real target and the smaller drum above it scrolls if it must.
            modifier = if (compact) Modifier.fillMaxWidth().height(128.dp).padding(bottom = 8.dp)
            else Modifier.fillMaxWidth().weight(0.62f).heightIn(min = 120.dp, max = 300.dp).padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun CountPad(hint: String?, onCount: () -> Unit, onMinus: () -> Unit, onUndo: () -> Unit, modifier: Modifier) {
    val padLabel = stringResource(R.string.counter_pad_label)
    Column(modifier.clip(RoundedCornerShape(RadiusLg)).background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = padLabel, onClick = onCount),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Text(
                    hint ?: padLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 360.dp),
                )
            }
        }
        Hairline(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            TextButton(onClick = onMinus, modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(0.dp)) {
                Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.counter_minus), maxLines = 1)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = com.mohdshayan.rowmark.ui.theme.DividerAlpha)))
            TextButton(onClick = onUndo, modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(0.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.counter_undo), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SmallCounterRow(c: Counter, onPlus: () -> Unit, onMinus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            val sub = when {
                c.kindEnum() == CounterKind.STITCH && c.resetsWithRow -> stringResource(R.string.edit_counter_resets)
                c.length != null -> stringResource(R.string.counter_of_length, c.length)
                else -> null
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onMinus, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.counter_minus_small, c.name)) }
        Text(c.value.toString(), style = com.mohdshayan.rowmark.ui.theme.DrumChip, modifier = Modifier.widthIn(min = 44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onPlus, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.counter_plus_small, c.name)) }
    }
}
