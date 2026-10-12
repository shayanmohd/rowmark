package com.mohdshayan.rowmark.ui.stash

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.core.Access
import com.mohdshayan.rowmark.core.Gates
import com.mohdshayan.rowmark.data.db.Project
import com.mohdshayan.rowmark.data.db.ProjectYarn
import com.mohdshayan.rowmark.data.db.Tool
import com.mohdshayan.rowmark.data.db.Yarn
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.ui.components.EmptyState
import com.mohdshayan.rowmark.ui.components.Hairline
import com.mohdshayan.rowmark.ui.components.ReadOnlyNote
import com.mohdshayan.rowmark.ui.components.Teaser
import com.mohdshayan.rowmark.ui.components.UnlockGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StashData(val yarns: List<Yarn>, val tools: List<Tool>, val links: List<ProjectYarn>, val projects: List<Project>)

enum class StashNote { NONE, YARN_ADDED, YARN_SAVED, TOOL_ADDED, ERROR }

class StashViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.stash
    val data: StateFlow<StashData?> = combine(repo.observeYarns(), repo.observeTools(), repo.observeLinks(), ServiceLocator.projects.observeProjects()) { y, t, l, p ->
        StashData(y, t, l, p) as StashData?
    }.catch { emit(StashData(emptyList(), emptyList(), emptyList(), emptyList())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _note = MutableStateFlow(StashNote.NONE)
    val note: StateFlow<StashNote> = _note.asStateFlow()
    fun clearNote() { _note.value = StashNote.NONE }

    fun saveYarn(yarn: Yarn, projectIds: Set<Long>) {
        viewModelScope.launch {
            try {
                val id = repo.saveYarn(yarn)
                repo.setLinks(id, projectIds)
                _note.value = if (yarn.id == 0L) StashNote.YARN_ADDED else StashNote.YARN_SAVED
            } catch (e: Exception) {
                _note.value = StashNote.ERROR
            }
        }
    }
    fun deleteYarn(id: Long) { viewModelScope.launch { repo.deleteYarn(id) } }
    fun saveTool(tool: Tool) { viewModelScope.launch { runCatching { repo.saveTool(tool); _note.value = StashNote.TOOL_ADDED }.onFailure { _note.value = StashNote.ERROR } } }
    fun deleteTool(id: Long) { viewModelScope.launch { repo.deleteTool(id) } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StashScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: StashViewModel = viewModel { StashViewModel(app) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val texts = mapOf(
        StashNote.YARN_ADDED to stringResource(R.string.stash_yarn_added), StashNote.YARN_SAVED to stringResource(R.string.stash_yarn_saved),
        StashNote.TOOL_ADDED to stringResource(R.string.stash_tool_added), StashNote.ERROR to stringResource(R.string.stash_yarn_error),
    )
    LaunchedEffect(note) { texts[note]?.let { snackbar.showSnackbar(it); viewModel.clearNote() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stash_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val d = data
            UnlockGate(
                "stash",
                locked = { open ->
                    val count = (d?.yarns?.size ?: 0) + (d?.tools?.size ?: 0)
                    if (Gates.stashAccess(false, count) == Access.READ_ONLY && d != null) {
                        ReadOnlyNote(stringResource(R.string.stash_read_only), open)
                        StashBody(d, viewModel, readOnly = true)
                    } else {
                        Teaser(sentence = stringResource(R.string.stash_teaser), onUnlock = open, modifier = Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                YarnRow(brand = stringResource(R.string.stash_sample_brand), line = stringResource(R.string.stash_sample_name), usedIn = stringResource(R.string.stash_sample_project), onClick = null)
                                Hairline(Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
            ) {
                if (d != null) StashBody(d, viewModel, readOnly = false)
            }
        }
    }
}

@Composable
private fun StashBody(d: StashData, viewModel: StashViewModel, readOnly: Boolean) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var yarnEditor by remember { mutableStateOf<Yarn?>(null) }
    var toolEditor by remember { mutableStateOf<Tool?>(null) }
    TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.stash_tab_yarn)) })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.stash_tab_tools)) })
    }
    if (tab == 0) {
        if (d.yarns.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    title = stringResource(R.string.stash_empty_yarn), body = stringResource(R.string.stash_empty_yarn_body),
                    actionLabel = if (readOnly) null else stringResource(R.string.stash_add_yarn), onAction = if (readOnly) null else ({ yarnEditor = Yarn(uuid = "", name = "") }),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(d.yarns, key = { it.id }) { y ->
                    val used = d.links.filter { it.yarnId == y.id }.mapNotNull { l -> d.projects.firstOrNull { it.id == l.projectId }?.name }.joinToString(", ")
                    YarnRow(
                        brand = y.brand.ifBlank { y.name }, line = listOf(if (y.brand.isBlank()) "" else y.name, y.colourway, y.weight, pluralStringResource(R.plurals.stash_skeins_line, if (y.skeins == 1f) 1 else 2, trimFloat(y.skeins))).filter { it.isNotBlank() }.joinToString(", "),
                        usedIn = used.ifBlank { null }, onClick = if (readOnly) null else ({ yarnEditor = y }),
                    )
                    Hairline(Modifier.padding(horizontal = 20.dp))
                }
                if (!readOnly) item {
                    Button(onClick = { yarnEditor = Yarn(uuid = "", name = "") }, modifier = Modifier.padding(20.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.stash_add_yarn)) }
                }
            }
        }
    } else {
        if (d.tools.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    title = stringResource(R.string.stash_empty_tools), body = stringResource(R.string.stash_empty_tools_body),
                    actionLabel = if (readOnly) null else stringResource(R.string.stash_add_tool), onAction = if (readOnly) null else ({ toolEditor = Tool(uuid = "", sizeMm = 4f) }),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(d.tools, key = { it.id }) { t ->
                    val typeName = stringResource(when (t.type) { "circular" -> R.string.tool_circular; "hook" -> R.string.tool_hook; else -> R.string.tool_needle })
                    Row(
                        Modifier.fillMaxWidth().then(if (readOnly) Modifier else Modifier.clickable { toolEditor = t }).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$typeName, " + stringResource(R.string.stash_mm, trimFloat(t.sizeMm)), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.stash_tool_summary, t.count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Hairline(Modifier.padding(horizontal = 20.dp))
                }
                if (!readOnly) item {
                    Button(onClick = { toolEditor = Tool(uuid = "", sizeMm = 4f) }, modifier = Modifier.padding(20.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.stash_add_tool)) }
                }
            }
        }
    }
    yarnEditor?.let { y ->
        YarnSheet(y, d.projects, d.links.filter { it.yarnId == y.id }.map { it.projectId }.toSet(), onSave = { yarn, ids -> viewModel.saveYarn(yarn, ids); yarnEditor = null }, onDelete = if (y.id != 0L) ({ viewModel.deleteYarn(y.id); yarnEditor = null }) else null, onDismiss = { yarnEditor = null })
    }
    toolEditor?.let { t ->
        ToolSheet(t, onSave = { viewModel.saveTool(it); toolEditor = null }, onDelete = if (t.id != 0L) ({ viewModel.deleteTool(t.id); toolEditor = null }) else null, onDismiss = { toolEditor = null })
    }
}

private fun trimFloat(f: Float): String = if (f == f.toLong().toFloat()) f.toLong().toString() else "%.1f".format(f)

@Composable
private fun YarnRow(brand: String, line: String, usedIn: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(brand, style = MaterialTheme.typography.titleMedium)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (usedIn != null) Text(stringResource(R.string.stash_used_in) + ": " + usedIn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun YarnSheet(y: Yarn, projects: List<Project>, linked: Set<Long>, onSave: (Yarn, Set<Long>) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var brand by rememberSaveable { mutableStateOf(y.brand) }
    var name by rememberSaveable { mutableStateOf(y.name) }
    var colourway by rememberSaveable { mutableStateOf(y.colourway) }
    var weight by rememberSaveable { mutableStateOf(y.weight) }
    var fibre by rememberSaveable { mutableStateOf(y.fibre) }
    var metres by rememberSaveable { mutableStateOf(y.metresPerSkein?.toString() ?: "") }
    var skeins by rememberSaveable { mutableStateOf(if (y.id == 0L) "" else trimFloat(y.skeins)) }
    var dyeLot by rememberSaveable { mutableStateOf(y.dyeLot) }
    var ids by remember { mutableStateOf(linked) }
    var tried by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.stash_add_yarn), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text(stringResource(R.string.stash_brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.stash_name)) }, singleLine = true, isError = tried && name.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = colourway, onValueChange = { colourway = it }, label = { Text(stringResource(R.string.stash_colourway)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(stringResource(R.string.stash_weight)) }, placeholder = { Text(stringResource(R.string.stash_weight_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = fibre, onValueChange = { fibre = it }, label = { Text(stringResource(R.string.stash_fibre)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = metres, onValueChange = { metres = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.stash_metres)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = skeins, onValueChange = { skeins = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(stringResource(R.string.stash_skeins)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(value = dyeLot, onValueChange = { dyeLot = it }, label = { Text(stringResource(R.string.stash_dye_lot)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (projects.isNotEmpty()) {
                Text(stringResource(R.string.stash_used_in), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in projects) FilterChip(selected = p.id in ids, onClick = { ids = if (p.id in ids) ids - p.id else ids + p.id }, label = { Text(p.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        tried = true
                        if (name.isNotBlank()) onSave(y.copy(brand = brand.trim(), name = name.trim(), colourway = colourway.trim(), weight = weight.trim(), fibre = fibre.trim(), metresPerSkein = metres.toIntOrNull(), skeins = skeins.toFloatOrNull() ?: 1f, dyeLot = dyeLot.trim()), ids)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.stash_save)) }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.weight(1f))
                if (onDelete != null) TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.stash_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ToolSheet(t: Tool, onSave: (Tool) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(t.type) }
    var size by rememberSaveable { mutableStateOf(if (t.id == 0L) "" else trimFloat(t.sizeMm)) }
    var count by rememberSaveable { mutableStateOf(t.count.toString()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.stash_add_tool), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, res) in listOf("needle" to R.string.tool_needle, "circular" to R.string.tool_circular, "hook" to R.string.tool_hook)) {
                    FilterChip(selected = type == key, onClick = { type = key }, label = { Text(stringResource(res)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = size, onValueChange = { size = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text(stringResource(R.string.stash_size_mm)) }, singleLine = true, isError = tried && size.toFloatOrNull() == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(value = count, onValueChange = { count = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.stash_count)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { tried = true; size.toFloatOrNull()?.let { onSave(t.copy(type = type, sizeMm = it, count = count.toIntOrNull() ?: 1)) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.stash_save)) }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.weight(1f))
                if (onDelete != null) TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.stash_delete), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
