package com.mohdshayan.rowmark.ui.unlock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.billing.Entitlement
import com.mohdshayan.rowmark.billing.StoreState

/**
 * The one place the unlock is sold and restored. It says what the unlock adds, shows Google Play's own
 * price, and stays an honest message on a phone where Play cannot sell, where the free version carries
 * on. [reason] is the locked feature that opened it, free for a per-feature first line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(reason: String, onClose: () -> Unit, viewModel: UnlockViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val wide = Modifier.fillMaxWidth().heightIn(min = 48.dp)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.unlock_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.unlocked) {
                Text(stringResource(R.string.unlock_done), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onClose, modifier = wide) { Text(stringResource(R.string.unlock_back)) }
                return@Column
            }
            reasonLine(reason)?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.unlock_body), style = MaterialTheme.typography.bodyLarge)
            for (line in stringArrayResource(R.array.unlock_features)) {
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
            val price = state.price
            when {
                state.entitlement == Entitlement.PENDING -> Text(stringResource(R.string.unlock_pending))
                state.store == StoreState.UNAVAILABLE -> Text(stringResource(R.string.unlock_unavailable))
                state.store == StoreState.OFFLINE -> Text(stringResource(R.string.unlock_offline))
                state.store == StoreState.NOT_OFFERED -> Text(stringResource(R.string.unlock_not_offered))
                // Loading: the button's own shape, disabled, until Google Play's price arrives.
                price == null -> Button(onClick = {}, enabled = false, modifier = wide) {
                    Text(stringResource(R.string.unlock_checking))
                }
                else -> Button(onClick = { activity?.let(viewModel::buy) }, enabled = activity != null, modifier = wide) {
                    Text(stringResource(R.string.unlock_buy, price))
                }
            }
            // As large as the buy button and never delayed: declining is as easy as buying.
            OutlinedButton(onClick = onClose, modifier = wide) { Text(stringResource(R.string.unlock_close)) }
            val message = when (note) {
                UnlockNote.CANCELLED -> R.string.unlock_cancelled
                UnlockNote.FAILED -> R.string.unlock_failed
                UnlockNote.NOT_OFFERED -> R.string.unlock_not_offered
                UnlockNote.NO_ANSWER -> R.string.unlock_no_answer
                UnlockNote.NOT_OWNED -> R.string.unlock_not_owned
                UnlockNote.NONE -> null
            }
            if (message != null) Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
            if (state.store != StoreState.UNAVAILABLE) {
                // Reached from Settings too: "Restore purchase" lives here and nowhere else.
                TextButton(onClick = viewModel::restore) { Text(stringResource(R.string.unlock_restore)) }
            }
            Text(stringResource(R.string.unlock_account_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The one line that names the feature that opened the screen; null for Settings and the card. */
private fun reasonLine(reason: String): Int? = when (reason) {
    "pattern" -> R.string.unlock_reason_pattern
    "schedules" -> R.string.unlock_reason_schedules
    "stats" -> R.string.unlock_reason_stats
    "stash" -> R.string.unlock_reason_stash
    "csv" -> R.string.unlock_reason_csv
    "tile" -> R.string.unlock_reason_tile
    else -> null
}
