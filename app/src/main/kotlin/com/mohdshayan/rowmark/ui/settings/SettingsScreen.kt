package com.mohdshayan.rowmark.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.core.BackupFile
import com.mohdshayan.rowmark.core.RestoreMode
import com.mohdshayan.rowmark.data.prefs.Settings
import com.mohdshayan.rowmark.data.prefs.ThemeMode
import com.mohdshayan.rowmark.data.prefs.VolumeDownAction
import com.mohdshayan.rowmark.data.repo.RestoreResult
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.input.KeyRouter
import com.mohdshayan.rowmark.ui.components.Hairline
import com.mohdshayan.rowmark.ui.components.LocalUnlock
import com.mohdshayan.rowmark.ui.components.SectionLabel
import com.mohdshayan.rowmark.ui.components.lockedAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsNote { NONE, BACKUP_SAVED, BACKUP_ERROR, BACKUP_INVALID, BACKUP_RESTORED, JOURNAL_EXPORTED, JOURNAL_ERROR, NO_APP }

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    private val backup = ServiceLocator.backup
    val settings: StateFlow<Settings> = ServiceLocator.feedback.settings

    private val _note = MutableStateFlow(SettingsNote.NONE)
    val note: StateFlow<SettingsNote> = _note.asStateFlow()
    fun clearNote() { _note.value = SettingsNote.NONE }
    fun setNote(n: SettingsNote) { _note.value = n }

    private val _pendingRestore = MutableStateFlow<RestoreResult.Ready?>(null)
    val pendingRestore: StateFlow<RestoreResult.Ready?> = _pendingRestore.asStateFlow()

    fun setTheme(m: ThemeMode) = viewModelScope.launch { prefs.setTheme(m) }
    fun setKeepAwake(b: Boolean) = viewModelScope.launch { prefs.setKeepAwake(b) }
    fun setHaptics(b: Boolean) = viewModelScope.launch { prefs.setHaptics(b) }
    fun setBell(b: Boolean) = viewModelScope.launch { prefs.setBell(b) }
    fun setAlertVibrate(b: Boolean) = viewModelScope.launch { prefs.setAlertVibrate(b) }
    fun setCountWithKeys(b: Boolean) = viewModelScope.launch { prefs.setCountWithKeys(b) }
    fun setVolumeDown(a: VolumeDownAction) = viewModelScope.launch { prefs.setVolumeDownAction(a) }
    fun setExtraKey(code: Int) = viewModelScope.launch { prefs.setExtraCountKeycode(code) }

    fun backupName() = backup.suggestedName()

    fun saveBackup(uri: Uri) {
        viewModelScope.launch {
            _note.value = try {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w") ?: throw IllegalStateException("no stream")
                    out.use { backup.writeBackup(it) }
                }
                SettingsNote.BACKUP_SAVED
            } catch (e: Exception) {
                SettingsNote.BACKUP_ERROR
            }
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            when (val r = backup.read(uri)) {
                is RestoreResult.Ready -> _pendingRestore.value = r
                RestoreResult.NotABackup, RestoreResult.Unreadable -> _note.value = SettingsNote.BACKUP_INVALID
            }
        }
    }

    fun applyRestore(file: BackupFile, mode: RestoreMode) {
        _pendingRestore.value = null
        viewModelScope.launch {
            _note.value = try { backup.apply(file, mode); SettingsNote.BACKUP_RESTORED } catch (e: Exception) { SettingsNote.BACKUP_INVALID }
        }
    }

    fun cancelRestore() { _pendingRestore.value = null }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _note.value = try {
                val csv = backup.journalCsv()
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w") ?: throw IllegalStateException("no stream")
                    out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                SettingsNote.JOURNAL_EXPORTED
            } catch (e: Exception) {
                SettingsNote.JOURNAL_ERROR
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(app) }
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val access = LocalUnlock.current
    val snackbar = remember { SnackbarHostState() }
    val texts = mapOf(
        SettingsNote.BACKUP_SAVED to stringResource(R.string.settings_backup_saved), SettingsNote.BACKUP_ERROR to stringResource(R.string.settings_backup_error),
        SettingsNote.BACKUP_INVALID to stringResource(R.string.settings_backup_invalid), SettingsNote.BACKUP_RESTORED to stringResource(R.string.settings_backup_restored),
        SettingsNote.JOURNAL_EXPORTED to stringResource(R.string.settings_journal_exported), SettingsNote.JOURNAL_ERROR to stringResource(R.string.settings_journal_error),
        SettingsNote.NO_APP to stringResource(R.string.settings_no_app),
    )
    // Show first, clear after: clearing first restarts this effect and cancels the snackbar it just showed.
    LaunchedEffect(note) { texts[note]?.let { snackbar.showSnackbar(it); viewModel.clearNote() } }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::saveBackup) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::readBackup) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(viewModel::exportCsv) }
    var remoteTest by remember { mutableStateOf(false) }
    val privacyUrl = stringResource(R.string.privacy_url)
    val supportEmail = stringResource(R.string.support_email)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionLabel(stringResource(R.string.settings_appearance))
            for ((mode, res) in listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton) { viewModel.setTheme(mode) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = s.theme == mode, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionLabel(stringResource(R.string.settings_counting))
            SwitchRow(stringResource(R.string.settings_keep_awake), null, s.keepAwake, viewModel::setKeepAwake)
            SwitchRow(stringResource(R.string.settings_haptics), null, s.haptics, viewModel::setHaptics)
            SwitchRow(stringResource(R.string.settings_bell), null, s.bell, viewModel::setBell)
            SwitchRow(stringResource(R.string.settings_alert_vibrate), null, s.alertVibrate, viewModel::setAlertVibrate)
            SwitchRow(stringResource(R.string.settings_keys), stringResource(R.string.settings_keys_body), s.countWithKeys, viewModel::setCountWithKeys)
            Text(stringResource(R.string.settings_volume_down), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((a, res) in listOf(VolumeDownAction.UNDO to R.string.volume_down_undo, VolumeDownAction.MINUS to R.string.volume_down_minus, VolumeDownAction.NOTHING to R.string.volume_down_nothing)) {
                    FilterChip(selected = s.volumeDownAction == a, onClick = { viewModel.setVolumeDown(a) }, label = { Text(stringResource(res)) })
                }
            }

            SectionLabel(stringResource(R.string.settings_remote))
            // Inline, never a dialog: a dialog has its own window, and a remote's keys would never reach
            // MainActivity.dispatchKeyEvent while it was open.
            if (remoteTest) {
                RemoteTestPanel(s, onUse = { viewModel.setExtraKey(it) }, onDone = { remoteTest = false })
            } else {
                ActionRow(stringResource(R.string.settings_test_remote), stringResource(R.string.settings_test_body)) { remoteTest = true }
            }
            if (s.extraCountKeycode > 0) {
                ActionRow(stringResource(R.string.settings_test_extra, KeyEvent.keyCodeToString(s.extraCountKeycode).removePrefix("KEYCODE_")), stringResource(R.string.settings_test_forget)) { viewModel.setExtraKey(0) }
            }

            SectionLabel(stringResource(R.string.settings_backup))
            ActionRow(stringResource(R.string.settings_save_backup), stringResource(R.string.settings_save_backup_body)) { saveBackup.launch(viewModel.backupName()) }
            ActionRow(stringResource(R.string.settings_restore_backup), null) { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) }

            SectionLabel(stringResource(R.string.settings_journal))
            ActionRow(stringResource(R.string.settings_export_csv), stringResource(R.string.settings_export_csv_body), locked = !access.unlocked, onClick = lockedAction("csv") { saveCsv.launch("rowmark-journal.csv") })

            SectionLabel(stringResource(R.string.settings_unlock))
            if (access.unlocked) {
                Text(stringResource(R.string.settings_unlocked), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
            } else {
                ActionRow(stringResource(R.string.settings_unlock_everything), stringResource(R.string.settings_unlock_body)) { access.open("settings") }
            }
            ActionRow(stringResource(R.string.unlock_restore), null) { access.open("restore") }

            SectionLabel(stringResource(R.string.settings_about))
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            ActionRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_open)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))) }.onFailure { viewModel.setNote(SettingsNote.NO_APP) }
            }
            ActionRow(stringResource(R.string.settings_support), stringResource(R.string.settings_support_body)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$supportEmail"))) }.onFailure { viewModel.setNote(SettingsNote.NO_APP) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingRestore?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.settings_restore_title, r.projectCount)) },
            text = { Text(stringResource(R.string.settings_restore_body)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.applyRestore(r.file, RestoreMode.REPLACE) }) { Text(stringResource(R.string.settings_restore_replace), color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { viewModel.applyRestore(r.file, RestoreMode.MERGE) }) { Text(stringResource(R.string.settings_restore_merge)) }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
    Hairline(Modifier.fillMaxWidth())
}

@Composable
private fun ActionRow(title: String, body: String?, locked: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (locked) Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.common_locked), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    Hairline(Modifier.fillMaxWidth())
}

/** Shows the last key a remote sent and offers to count with it. The probe never consumes a key. */
@Composable
private fun RemoteTestPanel(s: Settings, onUse: (Int) -> Unit, onDone: () -> Unit) {
    var lastKey by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        KeyRouter.probe = { e -> if (e.action == KeyEvent.ACTION_DOWN && e.keyCode != KeyEvent.KEYCODE_BACK) lastKey = e.keyCode }
        onDispose { KeyRouter.probe = null }
    }
    val key = lastKey
    val alreadyCounts = key != null && KeyRouter.countsRow(key, s.extraCountKeycode)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_test_remote), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.settings_test_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (key == null) stringResource(R.string.settings_test_waiting) else stringResource(R.string.settings_test_received, KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_")),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (alreadyCounts) Text(stringResource(R.string.settings_test_counts_already), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (key != null && !alreadyCounts && key != KeyEvent.KEYCODE_VOLUME_DOWN) {
                Button(onClick = { onUse(key); onDone() }, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.settings_test_use)) }
            }
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.settings_test_done)) }
        }
    }
    Hairline(Modifier.fillMaxWidth())
}
