package com.sheaf.feature.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.FormField
import com.sheaf.core.domain.model.FormFieldType
import com.sheaf.core.domain.model.NormPoint
import com.sheaf.feature.reader.print.printPdf
import java.io.File
import kotlin.math.roundToInt

private val InkColors = listOf(
    0xFFE4713B.toInt(), 0xFFE5484D.toInt(), 0xFF3457D5.toInt(),
    0xFF2F9E44.toInt(), 0xFFF2C037.toInt(), 0xFF15171C.toInt(),
)

/** Reader: page scroll, pinch-zoom, resume, themes, share, search, ToC, ink annotations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(documentId) { viewModel.onEvent(ReaderEvent.Open(documentId)) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.onEvent(ReaderEvent.PageChanged(it)) }
    }
    LaunchedEffect(state.pendingScrollPage) {
        state.pendingScrollPage?.let { page ->
            listState.animateScrollToItem(page.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)))
            viewModel.onEvent(ReaderEvent.ConsumeScroll)
        }
    }
    LaunchedEffect(state.filledUri) {
        state.filledUri?.let { path ->
            shareFilledPdf(context, path, state.displayName)
            viewModel.onEvent(ReaderEvent.ConsumeFilled)
        }
    }
    val formByPage = remember(state.formFields) { state.formFields.groupBy { it.pageIndex } }

    var zoom by remember { mutableFloatStateOf(1f) }
    var menuOpen by remember { mutableStateOf(false) }
    var showSignatureCapture by remember { mutableStateOf(false) }
    var pendingNote by remember { mutableStateOf<Pair<Int, NormPoint>?>(null) }
    var editingNote by remember { mutableStateOf<Annotation?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.foundation.layout.Column {
                TopAppBar(
                    title = { Text(text = state.displayName.ifBlank { "Reader" }, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (state.annotationsByPage.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleAnnotationsList) }) {
                                Icon(Icons.Filled.EditNote, contentDescription = "Annotations")
                            }
                        }
                        if (state.formFields.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleFormMode) }) {
                                Icon(
                                    Icons.Filled.Assignment,
                                    contentDescription = "Fill form",
                                    tint = if (state.formMode) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleAnnotate) }) {
                            Icon(
                                Icons.Filled.Draw,
                                contentDescription = "Annotate",
                                tint = if (state.annotating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (state.outline.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleOutline) }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents")
                            }
                        }
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleSearch) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { if (state.uri.isNotBlank()) shareDocument(context, state.uri) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ReaderTheme.entries.forEach { theme ->
                                DropdownMenuItem(
                                    text = { Text(themeLabel(theme) + if (theme == state.theme) "  ✓" else "") },
                                    onClick = {
                                        viewModel.onEvent(ReaderEvent.SetTheme(theme))
                                        menuOpen = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Print") },
                                onClick = {
                                    menuOpen = false
                                    if (state.uri.isNotBlank()) printPdf(context, state.uri, state.displayName)
                                },
                            )
                        }
                    },
                )
                if (state.searchActive) {
                    SearchBar(
                        query = state.searchQuery,
                        searching = state.searching,
                        resultCount = state.searchResults.size,
                        resultIndex = state.searchIndex,
                        onSearch = { viewModel.onEvent(ReaderEvent.Search(it)) },
                        onPrev = { viewModel.onEvent(ReaderEvent.PrevResult) },
                        onNext = { viewModel.onEvent(ReaderEvent.NextResult) },
                    )
                }
                if (state.annotating) {
                    InkToolbar(
                        selected = state.inkColorArgb,
                        highlighter = state.highlighter,
                        noteMode = state.noteMode,
                        signatureMode = state.signatureMode,
                        onTool = { viewModel.onEvent(ReaderEvent.SetHighlighter(it)) },
                        onNote = { viewModel.onEvent(ReaderEvent.SetNoteMode(true)) },
                        onSignature = {
                            if (state.hasSignature) viewModel.onEvent(ReaderEvent.SetSignatureMode(true))
                            else showSignatureCapture = true
                        },
                        onRedraw = { showSignatureCapture = true },
                        onColor = { viewModel.onEvent(ReaderEvent.SetInkColor(it)) },
                        onClearPage = { viewModel.clearPageAnnotations(state.currentPage) },
                    )
                }
                if (state.formMode) {
                    FormBar(
                        fieldCount = state.formFields.size,
                        saving = state.savingForm,
                        onSave = { viewModel.onEvent(ReaderEvent.SaveForm) },
                    )
                }
            }
        },
    ) { padding ->
        val bg = readerBackground(state.theme)
        Box(Modifier.fillMaxSize().padding(padding).background(bg)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error ?: "Error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> PageList(
                    pageCount = state.pageCount,
                    zoom = zoom,
                    annotating = state.annotating,
                    highlighter = state.highlighter,
                    noteMode = state.noteMode,
                    inkColor = Color(state.inkColorArgb),
                    annotationsByPage = state.annotationsByPage,
                    onZoom = { factor -> zoom = (zoom * factor).coerceIn(1f, 5f) },
                    onStroke = { page, pts -> viewModel.saveStroke(page, pts) },
                    onTapNote = { page, pt -> pendingNote = page to pt },
                    onEditNote = { ann -> editingNote = ann },
                    signatureMode = state.signatureMode,
                    onStampSignature = { page, pt -> viewModel.stampSignature(page, pt) },
                    formMode = state.formMode,
                    formFieldsByPage = formByPage,
                    formValues = state.formValues,
                    onFormValue = { name, value -> viewModel.onEvent(ReaderEvent.SetFormValue(name, value)) },
                    aspectRatioOf = viewModel::aspectRatio,
                    renderPage = { i, w, h -> viewModel.renderPage(i, w, h) },
                    listState = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                )
            }
            if (!state.isLoading && state.error == null && state.pageCount > 0) {
                Text(
                    text = "${state.currentPage + 1} / ${state.pageCount}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(Color(0x99000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (state.outlineVisible) {
        ModalBottomSheet(onDismissRequest = { viewModel.onEvent(ReaderEvent.ToggleOutline) }) {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(state.outline, key = { it.title + it.pageIndex + it.depth }) { entry ->
                    Text(
                        text = entry.title.ifBlank { "(untitled)" },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onEvent(ReaderEvent.JumpTo(entry.pageIndex))
                                viewModel.onEvent(ReaderEvent.ToggleOutline)
                            }
                            .padding(start = (12 + entry.depth * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }

    if (state.annotationsListVisible) {
        val all = remember(state.annotationsByPage) {
            state.annotationsByPage.values.flatten().sortedWith(compareBy({ it.pageIndex }, { it.createdAt }))
        }
        ModalBottomSheet(onDismissRequest = { viewModel.onEvent(ReaderEvent.ToggleAnnotationsList) }) {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(all, key = { it.id }) { ann ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onEvent(ReaderEvent.JumpTo(ann.pageIndex))
                                viewModel.onEvent(ReaderEvent.ToggleAnnotationsList)
                            }
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(12.dp).background(Color(ann.colorArgb), CircleShape),
                        )
                        Text(
                            text = annotationLabel(ann),
                            maxLines = 2,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        Text(
                            "p.${ann.pageIndex + 1}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        IconButton(onClick = { viewModel.deleteAnnotation(ann.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showSignatureCapture) {
        SignatureCaptureDialog(
            onDismiss = { showSignatureCapture = false },
            onSave = { pts ->
                viewModel.saveSignature(pts)
                showSignatureCapture = false
                viewModel.onEvent(ReaderEvent.SetSignatureMode(true))
            },
        )
    }

    pendingNote?.let { (page, pt) ->
        NoteDialog("",  { pendingNote = null }, { t -> viewModel.saveNote(page, pt, t); pendingNote = null }, null)
    }
    editingNote?.let { ann ->
        NoteDialog(ann.note.orEmpty(), { editingNote = null }, { t -> viewModel.updateNote(ann, t); editingNote = null }, { viewModel.deleteAnnotation(ann.id); editingNote = null })
    }
}

@Composable
private fun SignatureCaptureDialog(onDismiss: () -> Unit, onSave: (List<NormPoint>) -> Unit) {
    val pts = remember { mutableStateListOf<NormPoint>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw your signature") },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
            ) {
                Canvas(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o ->
                                pts.clear()
                                pts.add(NormPoint(o.x / size.width.toFloat(), o.y / size.height.toFloat()))
                            },
                            onDrag = { c, _ ->
                                pts.add(NormPoint(c.position.x / size.width.toFloat(), c.position.y / size.height.toFloat()))
                            },
                        )
                    },
                ) {
                    if (pts.size >= 2) {
                        val path = Path()
                        path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
                        for (i in 1 until pts.size) path.lineTo(pts[i].x * size.width, pts[i].y * size.height)
                        drawPath(path, Color(0xFF15171C), style = Stroke(width = 4f, cap = StrokeCap.Round))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pts.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NoteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Type a note") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun InkToolbar(
    selected: Int,
    highlighter: Boolean,
    noteMode: Boolean,
    signatureMode: Boolean,
    onTool: (Boolean) -> Unit,
    onNote: () -> Unit,
    onSignature: () -> Unit,
    onRedraw: () -> Unit,
    onColor: (Int) -> Unit,
    onClearPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { onTool(false) }) {
            Icon(
                Icons.Filled.Draw,
                contentDescription = "Pen",
                tint = if (!highlighter && !noteMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onTool(true) }) {
            Icon(
                Icons.Filled.Brush,
                contentDescription = "Highlighter",
                tint = if (highlighter && !noteMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNote) {
            Icon(
                Icons.Filled.StickyNote2,
                contentDescription = "Note",
                tint = if (noteMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSignature) {
            Icon(
                Icons.Filled.Gesture,
                contentDescription = "Signature",
                tint = if (signatureMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (signatureMode) {
            TextButton(onClick = onRedraw) { Text("Redraw") }
        }
        InkColors.forEach { argb ->
            val c = Color(argb)
            Box(
                Modifier
                    .size(26.dp)
                    .background(c, CircleShape)
                    .border(
                        width = if (argb == selected) 3.dp else 1.dp,
                        color = if (argb == selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onColor(argb) },
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClearPage) { Text("Clear page") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    searching: Boolean,
    resultCount: Int,
    resultIndex: Int,
    onSearch: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    var text by remember(query) { mutableStateOf(query) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("Search in document") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
        )
        val label = when {
            searching -> "…"
            resultCount == 0 && text.isNotBlank() -> "0"
            resultCount == 0 -> ""
            else -> "${resultIndex + 1}/$resultCount"
        }
        Text(label)
        IconButton(onClick = onPrev, enabled = resultCount > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous result")
        }
        IconButton(onClick = onNext, enabled = resultCount > 0) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next result")
        }
    }
}

private fun shareDocument(context: android.content.Context, uri: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share PDF")) }
}

@Composable
private fun PageList(
    pageCount: Int,
    zoom: Float,
    annotating: Boolean,
    highlighter: Boolean,
    noteMode: Boolean,
    inkColor: Color,
    annotationsByPage: Map<Int, List<Annotation>>,
    onZoom: (Float) -> Unit,
    onStroke: (Int, List<NormPoint>) -> Unit,
    onTapNote: (Int, NormPoint) -> Unit,
    onEditNote: (Annotation) -> Unit,
    signatureMode: Boolean,
    onStampSignature: (Int, NormPoint) -> Unit,
    formMode: Boolean,
    formFieldsByPage: Map<Int, List<FormField>>,
    formValues: Map<String, String>,
    onFormValue: (String, String) -> Unit,
    aspectRatioOf: (Int) -> Float,
    renderPage: suspend (Int, Int, Int) -> android.graphics.Bitmap?,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val pages = remember(pageCount) { (0 until pageCount).toList() }
    val listModifier = if (!annotating) {
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, _, gestureZoom, _ -> onZoom(gestureZoom) }
        }
    } else {
        Modifier.fillMaxSize()
    }
    LazyColumn(state = listState, contentPadding = contentPadding, modifier = listModifier) {
        items(pages, key = { it }) { index ->
            PdfPageItem(
                pageIndex = index,
                zoom = zoom,
                aspectRatio = aspectRatioOf(index),
                annotating = annotating,
                highlighter = highlighter,
                noteMode = noteMode,
                inkColor = inkColor,
                savedStrokes = annotationsByPage[index].orEmpty(),
                onStroke = { pts -> onStroke(index, pts) },
                onTapNote = { pt -> onTapNote(index, pt) },
                onEditNote = onEditNote,
                signatureMode = signatureMode,
                onStampSignature = { pt -> onStampSignature(index, pt) },
                formMode = formMode,
                formFields = formFieldsByPage[index].orEmpty(),
                formValues = formValues,
                onFormValue = onFormValue,
                renderPage = renderPage,
            )
        }
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    zoom: Float,
    aspectRatio: Float,
    annotating: Boolean,
    highlighter: Boolean,
    noteMode: Boolean,
    inkColor: Color,
    savedStrokes: List<Annotation>,
    onStroke: (List<NormPoint>) -> Unit,
    onTapNote: (NormPoint) -> Unit,
    onEditNote: (Annotation) -> Unit,
    signatureMode: Boolean,
    onStampSignature: (NormPoint) -> Unit,
    formMode: Boolean,
    formFields: List<FormField>,
    formValues: Map<String, String>,
    onFormValue: (String, String) -> Unit,
    renderPage: suspend (Int, Int, Int) -> android.graphics.Bitmap?,
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val targetWidthPx = (screenWidthPx * zoom).roundToInt().coerceIn(1, 3000)
    val targetHeightPx = (targetWidthPx * aspectRatio).roundToInt().coerceIn(1, 6000)

    var bitmap by remember(pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(pageIndex, targetWidthPx, targetHeightPx) {
        bitmap = renderPage(pageIndex, targetWidthPx, targetHeightPx)
    }

    val live = remember(pageIndex) { mutableStateListOf<Offset>() }

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f / aspectRatio).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator()
        }
        Canvas(
            Modifier.matchParentSize().pointerInput(annotating, noteMode, signatureMode) {
                if (!annotating) return@pointerInput
                if (signatureMode) {
                    detectTapGestures { pos ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        onStampSignature(NormPoint(pos.x / w, pos.y / h))
                    }
                } else if (noteMode) {
                    detectTapGestures { pos ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        onTapNote(NormPoint(pos.x / w, pos.y / h))
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { o -> live.clear(); live.add(o) },
                        onDrag = { change, _ -> live.add(change.position) },
                        onDragEnd = {
                            if (live.size >= 2) {
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                onStroke(live.map { NormPoint(it.x / w, it.y / h) })
                            }
                            live.clear()
                        },
                        onDragCancel = { live.clear() },
                    )
                }
            },
        ) {
            savedStrokes.forEach { ann ->
                val base = Color(ann.colorArgb)
                val c = if (ann.type == AnnotationType.Highlight) base.copy(alpha = 0.30f) else base
                drawInk(ann.points, c, ann.strokeWidth)
            }
            if (live.size >= 2) {
                val path = Path().apply {
                    moveTo(live[0].x, live[0].y)
                    for (i in 1 until live.size) lineTo(live[i].x, live[i].y)
                }
                val widthFrac = if (highlighter) 0.02f else 0.004f
                val liveColor = if (highlighter) inkColor.copy(alpha = 0.30f) else inkColor
                drawPath(
                    path = path,
                    color = liveColor,
                    style = Stroke(
                        width = (widthFrac * size.width).coerceAtLeast(2f),
                        cap = if (highlighter) StrokeCap.Square else StrokeCap.Round,
                    ),
                )
            }
        }
        savedStrokes.forEach { ann ->
            if (ann.type == AnnotationType.Note) {
                val pt = ann.points.firstOrNull()
                if (pt != null) {
                    Icon(
                        imageVector = Icons.Filled.StickyNote2,
                        contentDescription = "Note",
                        tint = Color(ann.colorArgb),
                        modifier = Modifier
                            .align(BiasAlignment(2f * pt.x - 1f, 2f * pt.y - 1f))
                            .size(28.dp)
                            .clickable { onEditNote(ann) },
                    )
                }
            }
        }
        if (formMode && formFields.isNotEmpty()) {
            FormFieldOverlay(fields = formFields, values = formValues, onValue = onFormValue)
        }
    }
}

@Composable
private fun FormFieldOverlay(
    fields: List<FormField>,
    values: Map<String, String>,
    onValue: (String, String) -> Unit,
) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val w = maxWidth
        val h = maxHeight
        fields.forEach { f ->
            val x = w * f.rect.x.coerceIn(0f, 1f)
            val y = h * f.rect.y.coerceIn(0f, 1f)
            val fw = (w * f.rect.w.coerceIn(0f, 1f)).coerceAtLeast(24.dp)
            val fh = (h * f.rect.h.coerceIn(0f, 1f)).coerceAtLeast(16.dp)
            val current = values[f.name] ?: f.value
            when (f.type) {
                FormFieldType.Checkbox -> {
                    val checked = current.isNotBlank() && !current.equals("Off", ignoreCase = true)
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onValue(f.name, if (it) "Yes" else "Off") },
                        modifier = Modifier.offset(x = x, y = y),
                    )
                }
                FormFieldType.Unsupported -> Unit
                else -> {
                    BasicTextField(
                        value = current,
                        onValueChange = { onValue(f.name, it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF15171C)),
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .size(width = fw, height = fh)
                            .background(Color(0x223457D5))
                            .border(1.dp, Color(0xFF3457D5))
                            .padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormBar(fieldCount: Int, saving: Boolean, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$fieldCount form field${if (fieldCount == 1) "" else "s"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (saving) CircularProgressIndicator(Modifier.size(20.dp))
        Button(onClick = onSave, enabled = !saving) { Text("Save filled PDF") }
    }
}

private fun shareFilledPdf(context: android.content.Context, path: String, displayName: String) {
    runCatching {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share filled PDF"))
    }
}

private fun DrawScope.drawInk(points: List<NormPoint>, color: Color, widthFrac: Float) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x * size.width, points[0].y * size.height)
    for (i in 1 until points.size) path.lineTo(points[i].x * size.width, points[i].y * size.height)
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = (widthFrac * size.width).coerceAtLeast(2f), cap = StrokeCap.Round),
    )
}

private fun annotationLabel(ann: Annotation): String = when (ann.type) {
    AnnotationType.Note -> ann.note?.takeIf { it.isNotBlank() } ?: "Note"
    AnnotationType.Highlight -> "Highlight"
    AnnotationType.Underline -> "Underline"
    AnnotationType.Strikethrough -> "Strikethrough"
    AnnotationType.Ink -> "Ink"
}

private fun themeLabel(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.System -> "System"
    ReaderTheme.Light -> "Light"
    ReaderTheme.Dark -> "Dark"
    ReaderTheme.Sepia -> "Sepia"
}

private fun readerBackground(theme: ReaderTheme): Color = when (theme) {
    ReaderTheme.Sepia -> Color(0xFFF4ECD8)
    ReaderTheme.Light -> Color(0xFFFFFFFF)
    ReaderTheme.Dark -> Color(0xFF121212)
    ReaderTheme.System -> Color(0xFF15171C)
}
