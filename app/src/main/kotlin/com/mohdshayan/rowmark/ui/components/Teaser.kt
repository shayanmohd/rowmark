package com.mohdshayan.rowmark.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mohdshayan.rowmark.R

/**
 * A locked area's honest preview: a real sample of what it does, one sentence, and one button to the
 * unlock screen. The lock glyph names the state; nothing nags.
 */
@Composable
fun Teaser(
    sentence: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        preview()
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 420.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.common_locked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(sentence, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUnlock, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.pattern_see_unlock))
        }
    }
}

/**
 * An icon whose label says when its feature is part of the unlock: a small lock sits on the glyph and
 * TalkBack reads "Pattern, part of the unlock".
 */
@Composable
fun LockedIcon(icon: ImageVector, label: String, locked: Boolean) {
    val description = if (locked) label + ", " + stringResource(R.string.common_locked) else label
    Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description)
        if (locked) {
            Icon(
                Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).size(14.dp),
            )
        }
    }
}

/** After a relock: what the user made stays readable, and the way back to the unlock sits beside the note. */
@Composable
fun ReadOnlyNote(text: String, onSeeUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeUnlock, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.pattern_see_unlock)) }
    }
}

/** Section label: quiet Twist text, sentence case, no caps. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
