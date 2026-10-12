@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mohdshayan.rowmark.ui.projectedit

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.core.CounterKind
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.ui.components.Hairline
import com.mohdshayan.rowmark.ui.components.SectionLabel
import com.mohdshayan.rowmark.ui.components.SkeletonBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectEditScreen(projectId: Long, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: ProjectEditViewModel = viewModel(key = "edit-$projectId") { ProjectEditViewModel(app, projectId) }
    val s by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setCover) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) R.string.edit_title_new else R.string.edit_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.edit_back)) } },
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.save { id, isNew -> if (isNew) onCreated(id) else onBack() } },
                enabled = s.loaded && !s.saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp),
            ) { Text(stringResource(if (s.isNew) R.string.edit_create else R.string.edit_save)) }
        },
    ) { padding ->
        if (!s.loaded) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth().height(56.dp)); SkeletonBlock(Modifier.width(200.dp).height(36.dp)); SkeletonBlock(Modifier.fillMaxWidth().height(56.dp))
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = s.name, onValueChange = viewModel::setName, label = { Text(stringResource(R.string.edit_name)) }, singleLine = true,
                isError = s.tried && !s.nameValid, supportingText = if (s.tried && !s.nameValid) ({ Text(stringResource(R.string.edit_name_error)) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.edit_craft))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.craft == "knitting", onClick = { viewModel.setCraft("knitting") }, label = { Text(stringResource(R.string.craft_knitting)) })
                FilterChip(selected = s.craft == "crochet", onClick = { viewModel.setCraft("crochet") }, label = { Text(stringResource(R.string.craft_crochet)) })
            }
            SectionLabel(stringResource(R.string.edit_status))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.status == "active", onClick = { viewModel.setStatus("active") }, label = { Text(stringResource(R.string.status_active)) })
                FilterChip(selected = s.status == "resting", onClick = { viewModel.setStatus("resting") }, label = { Text(stringResource(R.string.status_resting)) })
                FilterChip(selected = s.status == "finished", onClick = { viewModel.setStatus("finished") }, label = { Text(stringResource(R.string.status_finished)) })
            }
            OutlinedTextField(value = s.needle, onValueChange = viewModel::setNeedle, label = { Text(stringResource(R.string.edit_needle)) }, placeholder = { Text(stringResource(R.string.edit_needle_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = s.yarn, onValueChange = viewModel::setYarn, label = { Text(stringResource(R.string.edit_yarn)) }, placeholder = { Text(stringResource(R.string.edit_yarn_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = s.target, onValueChange = viewModel::setTarget, label = { Text(stringResource(R.string.edit_target)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text(stringResource(R.string.edit_target_hint)) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = s.notes, onValueChange = viewModel::setNotes, label = { Text(stringResource(R.string.edit_notes)) }, minLines = 3, modifier = Modifier.fillMaxWidth())

            SectionLabel(stringResource(R.string.edit_cover))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val cover = s.coverFile
                if (cover != null) {
                    val bmp by produceState<android.graphics.Bitmap?>(null, cover) {
                        value = withContext(Dispatchers.IO) { runCatching { ServiceLocator.patterns.decodeScaled(ServiceLocator.patterns.photoFile(cover), 240) }.getOrNull() }
                    }
                    bmp?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.edit_cover), modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop) }
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.edit_cover_choose)) }
                if (cover != null) TextButton(onClick = viewModel::removeCover, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.edit_cover_remove)) }
            }

            SectionLabel(stringResource(R.string.edit_counters))
            if (s.counters.isEmpty()) {
                Text(stringResource(R.string.edit_counters_empty), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.edit_counters_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp))
            }
            for (c in s.counters) {
                CounterEditor(c, s.counters.filter { it.id != c.id }, s.tried, onChange = viewModel::updateCounter, onRemove = { viewModel.removeCounter(c.id) })
                Hairline(Modifier.fillMaxWidth())
            }
            var addMenu by remember { mutableStateOf(false) }
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { addMenu = true }, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.edit_add_counter)) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    for ((kind, res) in kindLabels()) {
                        DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = { addMenu = false; viewModel.addCounter(kind) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun kindLabels() = listOf(
    CounterKind.ROW to R.string.kind_row, CounterKind.REPEAT to R.string.kind_repeat, CounterKind.SECTION to R.string.kind_section,
    CounterKind.STITCH to R.string.kind_stitch, CounterKind.CUSTOM to R.string.kind_custom,
)

@Composable
private fun CounterEditor(c: CounterDraft, others: List<CounterDraft>, tried: Boolean, onChange: (CounterDraft) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = c.name, onValueChange = { onChange(c.copy(name = it)) }, label = { Text(stringResource(R.string.edit_counter_name)) }, singleLine = true, isError = tried && c.name.isBlank(), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.edit_counter_remove), color = MaterialTheme.colorScheme.error) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((kind, res) in kindLabels()) {
                FilterChip(selected = c.kind == kind, onClick = { onChange(c.copy(kind = kind, resetsWithRow = c.resetsWithRow && kind == CounterKind.STITCH)) }, label = { Text(stringResource(res)) })
            }
        }
        if (c.kind != CounterKind.STITCH) {
            OutlinedTextField(
                value = c.length, onValueChange = { onChange(c.copy(length = it.filter(Char::isDigit).take(3))) },
                label = { Text(stringResource(R.string.edit_counter_length)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = tried && !c.lengthValid,
                supportingText = { Text(stringResource(if (tried && !c.lengthValid) R.string.edit_counter_length_error else R.string.edit_counter_length_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            var parentMenu by remember { mutableStateOf(false) }
            val parentName = others.firstOrNull { it.id == c.parentId }?.name ?: stringResource(R.string.edit_counter_parent_none)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_counter_parent), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { parentMenu = true }, modifier = Modifier.heightIn(min = 44.dp)) { Text(parentName) }
                    DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit_counter_parent_none)) }, onClick = { parentMenu = false; onChange(c.copy(parentId = null)) })
                        for (o in others) DropdownMenuItem(text = { Text(o.name) }, onClick = { parentMenu = false; onChange(c.copy(parentId = o.id)) })
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_counter_resets), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = c.resetsWithRow, onCheckedChange = { onChange(c.copy(resetsWithRow = it)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = c.alertEvery, onValueChange = { onChange(c.copy(alertEvery = it.filter(Char::isDigit).take(3))) },
                label = { Text(stringResource(R.string.edit_counter_alert)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = tried && !c.alertValid, supportingText = { Text(stringResource(if (tried && !c.alertValid) R.string.edit_counter_alert_error else R.string.edit_counter_alert_hint)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = c.alertLabel, onValueChange = { onChange(c.copy(alertLabel = it)) }, label = { Text(stringResource(R.string.edit_counter_alert_label)) },
                placeholder = { Text(stringResource(R.string.edit_counter_alert_label_hint)) }, singleLine = true, modifier = Modifier.weight(1.4f),
            )
        }
    }
}
