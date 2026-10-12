package com.mohdshayan.rowmark.ui.projects

import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.review.ReviewPrompter
import com.mohdshayan.rowmark.ui.components.EmptyState
import com.mohdshayan.rowmark.ui.components.Hairline
import com.mohdshayan.rowmark.ui.components.LocalUnlock
import com.mohdshayan.rowmark.ui.components.SkeletonBlock
import com.mohdshayan.rowmark.ui.components.lockedAction
import com.mohdshayan.rowmark.ui.components.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onOpenProject: (Long) -> Unit,
    onNewProject: () -> Unit,
    onOpenStash: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProjectsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showCard by viewModel.showUnlockCard.collectAsStateWithLifecycle()
    val stashCount by viewModel.stashCount.collectAsStateWithLifecycle()
    val access = LocalUnlock.current
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { activity?.let { ReviewPrompter.maybePrompt(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_title), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.projects_settings))
                    }
                },
            )
        },
        bottomBar = {
            val ready = state as? ProjectsState.Ready
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The first screen's locked entry: the stash when unlocked, the unlock screen when not. A
                // relocked phone that still holds yarn or needles opens the stash read-only instead.
                val openStash = lockedAction("stash", onOpenStash)
                FilledTonalButton(
                    onClick = if (stashCount > 0) onOpenStash else openStash,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        if (access.unlocked) Icons.Rounded.Inventory2 else Icons.Rounded.Lock,
                        contentDescription = if (access.unlocked) null else stringResource(R.string.common_locked),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_locked_example), maxLines = 2, textAlign = TextAlign.Center)
                }
                if (ready != null && ready.rows.isNotEmpty()) {
                    Button(onClick = onNewProject, modifier = Modifier.weight(1f).heightIn(min = 52.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.projects_new), maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            ProjectsState.Loading -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
                repeat(3) {
                    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(Modifier.size(56.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            SkeletonBlock(Modifier.width(180.dp).height(18.dp))
                            Spacer(Modifier.height(8.dp))
                            SkeletonBlock(Modifier.width(120.dp).height(14.dp))
                        }
                    }
                }
            }
            ProjectsState.Error -> EmptyState(
                title = stringResource(R.string.projects_error),
                body = "",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is ProjectsState.Ready -> if (s.rows.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.projects_empty_title),
                    body = stringResource(R.string.projects_empty_body),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    actionLabel = stringResource(R.string.projects_start),
                    onAction = onNewProject,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (showCard && !access.unlocked) {
                        item { UnlockCard(onSee = { viewModel.dismissCard(); access.open("card") }, onDismiss = viewModel::dismissCard) }
                    }
                    items(s.rows, key = { it.project.id }) { row ->
                        ProjectRowItem(row, onClick = { onOpenProject(row.project.id) })
                        Hairline(Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRowItem(row: ProjectRow, onClick: () -> Unit) {
    val p = row.project
    val rowValue = row.row?.value ?: 0
    val repeat = row.repeat
    val summary = buildString {
        append(stringResource(R.string.projects_row_summary, rowValue))
        if (repeat != null) {
            append(", ")
            val length = repeat.length
            append(if (length != null) stringResource(R.string.projects_repeat_of, repeat.value, length) else stringResource(R.string.projects_repeat, repeat.value))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(p.coverFile, p.name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (p.lastCountAt != null) stringResource(R.string.projects_last_worked, relativeTime(p.lastCountAt)) else stringResource(R.string.projects_never_worked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Cover(file: String?, name: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file) {
        value = if (file == null) null else withContext(Dispatchers.IO) {
            runCatching { ServiceLocator.patterns.decodeScaled(ServiceLocator.patterns.photoFile(file), 160) }.getOrNull()
        }
    }
    val shape = MaterialTheme.shapes.medium
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.projects_cover_description, name), modifier = Modifier.size(56.dp).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun UnlockCard(onSee: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.projects_card_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.projects_card_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSee, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.projects_card_see)) }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.projects_card_dismiss)) }
        }
    }
}
