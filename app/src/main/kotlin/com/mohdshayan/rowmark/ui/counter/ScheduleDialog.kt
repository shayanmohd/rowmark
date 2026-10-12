package com.mohdshayan.rowmark.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.core.ScheduleEngine
import com.mohdshayan.rowmark.data.db.Counter
import com.mohdshayan.rowmark.data.db.Schedule
import com.mohdshayan.rowmark.data.repo.spec

/** "Increase every 6 rows, 8 times, from row 12", following Total rows or one counter. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleDialog(
    schedule: Schedule,
    counters: List<Counter>,
    onSave: (Schedule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var action by rememberSaveable { mutableStateOf(schedule.action) }
    var label by rememberSaveable { mutableStateOf(schedule.label) }
    var from by rememberSaveable { mutableStateOf(if (schedule.id == 0L) "" else schedule.startRow.toString()) }
    var every by rememberSaveable { mutableStateOf(if (schedule.id == 0L) "" else schedule.every.toString()) }
    var times by rememberSaveable { mutableStateOf(if (schedule.id == 0L) "" else schedule.times.toString()) }
    var counterId by rememberSaveable { mutableStateOf(schedule.counterId ?: 0L) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val candidate = Schedule(schedule.id, schedule.projectId, counterId.takeIf { it > 0 }, action, label, from.toIntOrNull() ?: 0, every.toIntOrNull() ?: 0, times.toIntOrNull() ?: 0)
    val valid = ScheduleEngine.valid(candidate.spec())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.schedule_action), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((key, res) in listOf("increase" to R.string.action_increase, "decrease" to R.string.action_decrease, "other" to R.string.action_other)) {
                        FilterChip(selected = action == key, onClick = { action = key }, label = { Text(stringResource(res)) })
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.schedule_label)) }, placeholder = { Text(stringResource(R.string.schedule_label_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = from, onValueChange = { from = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.schedule_from)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = every, onValueChange = { every = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.schedule_every)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = times, onValueChange = { times = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.schedule_times)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                if (tried && !valid) Text(stringResource(R.string.schedule_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.schedule_counts), style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = counterId == 0L, onClick = { counterId = 0L }, label = { Text(stringResource(R.string.schedule_total_rows)) })
                    for (c in counters) FilterChip(selected = counterId == c.id, onClick = { counterId = c.id }, label = { Text(c.name) })
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.schedule_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { tried = true; if (valid) onSave(candidate) }, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.schedule_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.common_cancel)) } },
    )
}
