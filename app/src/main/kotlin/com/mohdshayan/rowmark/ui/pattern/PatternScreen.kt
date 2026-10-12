package com.mohdshayan.rowmark.ui.pattern

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.core.Access
import com.mohdshayan.rowmark.core.Gates
import com.mohdshayan.rowmark.core.RulerMath
import com.mohdshayan.rowmark.data.db.Markup
import com.mohdshayan.rowmark.data.db.Pattern
import com.mohdshayan.rowmark.data.repo.PatternKind
import com.mohdshayan.rowmark.data.repo.CountSource
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.ui.components.CountKeyHandler
import com.mohdshayan.rowmark.ui.components.EmptyState
import com.mohdshayan.rowmark.ui.components.KeepScreenAwake
import com.mohdshayan.rowmark.ui.components.LocalUnlock
import com.mohdshayan.rowmark.ui.components.ReadOnlyNote
import com.mohdshayan.rowmark.ui.components.SkeletonBlock
import com.mohdshayan.rowmark.ui.components.Teaser
import com.mohdshayan.rowmark.ui.components.UnlockGate
import com.mohdshayan.rowmark.ui.theme.DrumChip
import com.mohdshayan.rowmark.ui.theme.HighlighterAlpha
import com.mohdshayan.rowmark.ui.theme.HighlighterInk
import kotlin.math.roundToInt

@Composable
private fun patternViewModel(projectId: Long): PatternViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(key = "pattern-$projectId") { PatternViewModel(app, projectId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternScreen(projectId: Long, onBack: () -> Unit) {
    val viewModel = patternViewModel(projectId)
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val note by viewModel.note.collectAsStateWithLifecycle()
    val settings by ServiceLocator.feedback.settings.collectAsStateWithLifecycle()
    // Counting carries on here, so the ruler climbs while the pattern is on screen.
    KeepScreenAwake(settings.keepAwake)
    CountKeyHandler(settings, onCount = viewModel::countRow, onUndo = viewModel::undo, onMinus = viewModel::minusRow)
    val added = stringResource(R.string.pattern_added)
    val password = stringResource(R.string.pattern_password)
    val unreadable = stringResource(R.string.pattern_unreadable)
    val renderError = stringResource(R.string.pattern_render_error)
    LaunchedEffect(note) {
        val text = when (note) {
            PatternNote.ADDED -> added
            PatternNote.PASSWORD -> password
            PatternNote.UNREADABLE -> unreadable
            PatternNote.RENDER_ERROR -> renderError
            PatternNote.NONE -> null
        }
        if (text != null) { snackbar.showSnackbar(text); viewModel.clearNote() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pattern_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            UnlockGate(
                "pattern",
                locked = { open ->
                    val access = Gates.patternAccess(false, patterns?.size ?: 0)
                    if (access == Access.READ_ONLY) {
                        Column(Modifier.fillMaxSize()) {
                            ReadOnlyNote(stringResource(R.string.pattern_read_only), open)
                            PatternBody(viewModel, readOnly = true)
                        }
                    } else {
                        LockedPatternTeaser(viewModel, open)
                    }
                },
            ) { PatternBody(viewModel, readOnly = false) }
        }
    }
}

/** The right pane of a wide Counter screen: the same viewer without its own top bar. */
@Composable
fun PatternPane(projectId: Long) {
    val viewModel = patternViewModel(projectId)
    PatternBody(viewModel, readOnly = !LocalUnlock.current.unlocked)
}

@Composable
private fun LockedPatternTeaser(viewModel: PatternViewModel, open: () -> Unit) {
    val sample = remember { viewModel.sampleChart() }
    val accent = MaterialTheme.colorScheme.primary
    Teaser(sentence = stringResource(R.string.pattern_teaser), onUnlock = open, modifier = Modifier.fillMaxSize()) {
        if (sample != null) {
            Box(Modifier.fillMaxWidth().widthIn(max = 480.dp).clip(MaterialTheme.shapes.small)) {
                Image(sample.asImageBitmap(), contentDescription = stringResource(R.string.pattern_sample_caption), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                // The ruler band over the sample's row 4, as it would sit on a real chart.
                Canvas(Modifier.matchParentSize()) {
                    val top = size.height * 0.60f
                    val h = size.height * 0.075f
                    drawRect(color = accent.copy(alpha = 0.24f), topLeft = Offset(0f, top), size = Size(size.width, h))
                    drawRect(color = accent, topLeft = Offset(0f, top), size = Size(size.width, 3.dp.toPx()))
                    drawRect(color = accent, topLeft = Offset(0f, top + h - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun PatternBody(viewModel: PatternViewModel, readOnly: Boolean) {
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val markup by viewModel.markup.collectAsStateWithLifecycle()
    val row by viewModel.rowValue.collectAsStateWithLifecycle()
    val highlighter by viewModel.highlighter.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Pattern?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.add(it, PatternKind.PDF, it.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "") }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.add(it, PatternKind.IMAGE, "") } }

    val list = patterns
    when {
        list == null -> Column(Modifier.fillMaxSize().padding(20.dp)) { SkeletonBlock(Modifier.fillMaxWidth().weight(1f)) }
        list.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(title = stringResource(R.string.pattern_empty_title), body = stringResource(R.string.pattern_empty_body))
            if (!readOnly) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pattern_add_pdf)) }
                    OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pattern_add_photo)) }
                }
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            if (list.size > 1 || !readOnly) {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { p ->
                        FilterChip(selected = p.id == selected?.id, onClick = { viewModel.select(p.id) }, label = { Text(p.title) })
                    }
                    if (!readOnly) {
                        item { FilterChip(selected = false, onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, label = { Text(stringResource(R.string.pattern_add_pdf)) }) }
                        item { FilterChip(selected = false, onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, label = { Text(stringResource(R.string.pattern_add_photo)) }) }
                    }
                }
            }
            val p = selected
            if (p != null) {
                PageView(viewModel, p, page, markup.filter { it.page == page }, row, highlighter && !readOnly, readOnly, Modifier.weight(1f).fillMaxWidth())
                Toolbar(viewModel, p, page, row, highlighter, readOnly, onDelete = { confirmDelete = p })
            }
        }
    }
    val del = confirmDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.pattern_delete)) },
            text = { Text(stringResource(R.string.pattern_delete_confirm, del.title)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(del); confirmDelete = null }) { Text(stringResource(R.string.pattern_remove_yes), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun Toolbar(viewModel: PatternViewModel, p: Pattern, page: Int, row: Int, highlighter: Boolean, readOnly: Boolean, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        // Counting row: the drum as a chip, a count and an undo (never locked), and the ruler controls.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pattern_row_chip, row), style = DrumChip, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 8.dp))
            FilledIconButton(onClick = { viewModel.countRow(CountSource.TAP) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.pattern_count_row))
            }
            IconButton(onClick = viewModel::undo) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.pattern_undo_row)) }
            Spacer(Modifier.weight(1f))
            if (!readOnly) {
                IconButton(onClick = { viewModel.stepRuler(up = true) }) { Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.pattern_step_up)) }
                IconButton(onClick = { viewModel.stepRuler(up = false) }) { Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.pattern_step_down)) }
                IconButton(onClick = viewModel::toggleDirection) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(if (p.readsUpward) R.string.pattern_direction_up else R.string.pattern_direction_down))
                }
            }
        }
        // Page row: page turning, and the highlighter tools when editing is allowed.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.setPage(page - 1) }, enabled = page > 0) { Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.pattern_prev_page)) }
            Text(stringResource(R.string.pattern_page_of, page + 1, p.pageCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { viewModel.setPage(page + 1) }, enabled = page < p.pageCount - 1) { Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.pattern_next_page)) }
            // The caption yields before the tools do: in a narrow pane it ellipsises instead of clipping them.
            Text(
                if (readOnly) "" else stringResource(if (p.readsUpward) R.string.pattern_direction_up else R.string.pattern_direction_down),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (!readOnly) {
                FilledIconToggleButton(checked = highlighter, onCheckedChange = { viewModel.toggleHighlighter() }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(if (highlighter) R.string.pattern_highlighter_on else R.string.pattern_highlighter))
                }
                IconButton(onClick = viewModel::undoStroke) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.pattern_undo_stroke)) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.pattern_delete)) }
            }
        }
    }
}

/** One page with pinch zoom to 4x, the ruler band with its handles, and highlighter strokes, all in page fractions. */
@Composable
private fun PageView(viewModel: PatternViewModel, p: Pattern, page: Int, strokes: List<Markup>, row: Int, highlighter: Boolean, readOnly: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds()) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val bitmap by produceState<Bitmap?>(null, p.id, p.file, page, widthPx) {
            value = viewModel.render(p, page, widthPx)
            if (value == null) viewModel.renderFailed()
        }
        var scale by remember(p.id) { mutableFloatStateOf(1f) }
        var offset by remember(p.id) { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 4f)
            offset = if (scale <= 1f) Offset.Zero else offset + pan
        }
        val bmp = bitmap
        if (bmp == null) {
            Column(Modifier.fillMaxSize().padding(16.dp)) { SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(0.72f)) }
            return@BoxWithConstraints
        }
        val accent = MaterialTheme.colorScheme.primary
        val handleDescription = stringResource(R.string.pattern_ruler_handle)
        // Live edits while dragging, written to Room on release.
        var dragTop by remember(p.id, page) { mutableStateOf<Float?>(null) }
        var dragHeight by remember(p.id, page) { mutableStateOf<Float?>(null) }
        var stroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
        val showRuler = p.rulerPage == page || dragTop != null
        Box(
            Modifier
                .fillMaxSize()
                .transformable(transform)
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height)
                    .semantics { contentDescription = handleDescription }
                    .pointerInput(p.id, page, highlighter, readOnly, p.rulerTop, p.rulerHeight) {
                        if (readOnly) return@pointerInput
                        val h = size.height.toFloat()
                        val w = size.width.toFloat()
                        var mode = 0 // 1 move band, 2 resize band, 3 highlight
                        var startTop = p.rulerTop
                        var startHeight = p.rulerHeight
                        detectDragGestures(
                            onDragStart = { pos ->
                                val top = p.rulerTop * h
                                val bottom = (p.rulerTop + p.rulerHeight) * h
                                val grip = 24.dp.toPx()
                                mode = when {
                                    highlighter -> 3
                                    p.rulerPage == page && pos.y in (bottom - grip)..(bottom + grip) -> 2
                                    p.rulerPage == page && pos.y in top..bottom -> 1
                                    else -> 0
                                }
                                startTop = p.rulerTop; startHeight = p.rulerHeight
                                if (mode == 3) stroke = listOf(pos.x / w to pos.y / h)
                                if (mode == 1) dragTop = p.rulerTop
                                if (mode == 2) dragHeight = p.rulerHeight
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pos = change.position
                                when (mode) {
                                    1 -> dragTop = RulerMath.clampTop(pos.y / h - startHeight / 2f, startHeight)
                                    2 -> { val top = dragTop ?: startTop; dragHeight = RulerMath.clampHeight(pos.y / h - top) }
                                    3 -> stroke = stroke + (pos.x / w to pos.y / h)
                                }
                            },
                            onDragEnd = {
                                when (mode) {
                                    1 -> dragTop?.let { viewModel.setRuler(it, startHeight) }
                                    2 -> dragHeight?.let { viewModel.setRuler(dragTop ?: startTop, it) }
                                    3 -> viewModel.addStroke(stroke)
                                }
                                dragTop = null; dragHeight = null; stroke = emptyList()
                            },
                            onDragCancel = { dragTop = null; dragHeight = null; stroke = emptyList() },
                        )
                    },
            ) {
                Image(bmp.asImageBitmap(), contentDescription = p.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val ink = HighlighterInk.copy(alpha = HighlighterAlpha)
                    val width = 14.dp.toPx() / scale
                    for (m in strokes) drawStrokePath(parsePoints(m.points), w, h, ink, width)
                    if (stroke.size > 1) drawStrokePath(stroke, w, h, ink, width)
                    if (showRuler) {
                        val top = (dragTop ?: p.rulerTop) * h
                        val bandH = (dragHeight ?: p.rulerHeight) * h
                        val edge = 3.dp.toPx() / scale
                        drawRect(accent.copy(alpha = 0.24f), topLeft = Offset(0f, top), size = Size(w, bandH))
                        drawRect(accent, topLeft = Offset(0f, top), size = Size(w, edge))
                        drawRect(accent, topLeft = Offset(0f, top + bandH - edge), size = Size(w, edge))
                        // Handles at both ends of the bottom edge.
                        val r = 10.dp.toPx() / scale
                        drawCircle(accent, r, Offset(r * 1.5f, top + bandH))
                        drawCircle(accent, r, Offset(w - r * 1.5f, top + bandH))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(points: List<Pair<Float, Float>>, w: Float, h: Float, color: androidx.compose.ui.graphics.Color, width: Float) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].first * w, points[0].second * h)
    for (i in 1 until points.size) path.lineTo(points[i].first * w, points[i].second * h)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** "[[x,y],[x,y]]" back to pairs; anything malformed is skipped. */
fun parsePoints(json: String): List<Pair<Float, Float>> =
    Regex("""\[\s*(-?[0-9.]+)\s*,\s*(-?[0-9.]+)\s*]""").findAll(json)
        .mapNotNull { m -> m.groupValues[1].toFloatOrNull()?.let { x -> m.groupValues[2].toFloatOrNull()?.let { y -> x to y } } }
        .toList()

@Suppress("unused")
private fun Int.px() = this.toFloat().roundToInt()
