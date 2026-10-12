package com.mohdshayan.rowmark.ui.components

import androidx.compose.animation.Animatable as ColorAnimatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohdshayan.rowmark.ui.theme.DividerAlpha
import com.mohdshayan.rowmark.ui.theme.DrumLarge
import com.mohdshayan.rowmark.ui.theme.DrumMedium
import com.mohdshayan.rowmark.ui.theme.DrumSmall
import com.mohdshayan.rowmark.ui.theme.LocalReducedMotion
import com.mohdshayan.rowmark.ui.theme.RadiusSm

/**
 * The barrel drum: each digit in a fixed cell, the changed digit rolling up in Fuchsia and settling to
 * Ink. [delayMs] staggers a parent drum behind the row drum. Reduced motion snaps everything.
 */
@Composable
fun Drum(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    compact: Boolean = false,
    minCells: Int = if (large) 2 else 1,
    delayMs: Int = 0,
    suffix: String? = null,
    contentDescription: String = "$label $value",
) {
    val reduced = LocalReducedMotion.current
    val style: TextStyle = if (large && !compact) DrumLarge else if (large) DrumMedium else DrumSmall
    // Cells scale with the digit size, so a large system font never spills a digit past its cell.
    val cellWidth: Dp = with(LocalDensity.current) { (style.fontSize * 0.64f).toDp() }
    val text = value.toString()
    val cells = maxOf(minCells, text.length)
    // The big drum pads with zeros like a barrel counter; the small drums leave the cell blank.
    val padded = text.padStart(cells, if (large) '0' else ' ')

    // The first count springs the drum in once; after that every change is a roll.
    var sprung by remember { mutableStateOf(value != 0) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (!sprung && value != 0) {
            sprung = true
            if (!reduced) {
                scale.snapTo(0.92f)
                scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
            }
        }
    }

    Column(modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            for ((i, ch) in padded.withIndex()) {
                DigitCell(ch = ch, width = cellWidth, style = style, reduced = reduced, delayMs = delayMs, key = i)
                if (i < padded.lastIndex) Spacer(Modifier.width(if (large) 4.dp else 2.dp))
            }
            if (suffix != null) {
                Text(
                    suffix,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = if (large && !compact) 20.dp else if (large) 14.dp else 8.dp),
                )
            }
        }
        Spacer(Modifier.height(if (large) 2.dp else 4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DigitCell(ch: Char, width: Dp, style: TextStyle, reduced: Boolean, delayMs: Int, key: Int) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val color = remember { ColorAnimatable(ink) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(ch) {
        if (first) { first = false; color.snapTo(ink); return@LaunchedEffect }
        if (reduced) { color.snapTo(ink) } else {
            color.snapTo(accent)
            color.animateTo(ink, tween(durationMillis = 520, delayMillis = delayMs))
        }
    }
    Box(
        modifier = Modifier
            .width(width)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = ch,
            transitionSpec = {
                if (reduced) {
                    ContentTransform(fadeIn(snap()), fadeOut(snap()), sizeTransform = null)
                } else {
                    (slideInVertically(tween(160, delayMs)) { it } + fadeIn(tween(160, delayMs)))
                        .togetherWith(slideOutVertically(tween(160, delayMs)) { -it } + fadeOut(tween(120, delayMs)))
                        .apply { targetContentZIndex = 1f }
                }
            },
            label = "digit$key",
        ) { c ->
            Text(text = c.toString(), style = style, color = color.value)
        }
    }
}

/** A hairline in Twist at 24 percent, the only divider in the app. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = DividerAlpha)))
}

/** A loading block in the shape of the content. Static: no shimmer. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(RadiusSm)))
}
