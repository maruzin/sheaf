package com.sheaf.feature.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.NormPoint
import com.sheaf.feature.reader.print.printPdf
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

    var zoom by remember { mutableFloatStateOf(1f) }
    var menuOpen by remember { mutableStateOf(false) }

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
                        onColor = { viewModel.onEvent(ReaderEvent.SetInkColor(it)) },
                        onClearPage = { viewModel.clearPageAnnotations(state.currentPage) },
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
                    inkColor = Color(state.inkColorArgb),
                    annotationsByPage = state.annotationsByPage,
                    onZoom = { factor -> zoom = (zoom * factor).coerceIn(1f, 5f) },
                    onStroke = { page, pts -> viewModel.saveStroke(page, pts) },
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
}

@Composable
private fun InkToolbar(selected: Int, onColor: (Int) -> Unit, onClearPage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
    inkColor: Color,
    annotationsByPage: Map<Int, List<Annotation>>,
    onZoom: (Float) -> Unit,
    onStroke: (Int, List<NormPoint>) -> Unit,
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
                inkColor = inkColor,
                savedStrokes = annotationsByPage[index].orEmpty(),
                onStroke = { pts -> onStroke(index, pts) },
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
    inkColor: Color,
    savedStrokes: List<Annotation>,
    onStroke: (List<NormPoint>) -> Unit,
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
            Modifier.matchParentSize().pointerInput(annotating) {
                if (annotating) {
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
            savedStrokes.forEach { ann -> drawInk(ann.points, Color(ann.colorArgb), ann.strokeWidth) }
            if (live.size >= 2) {
                val path = Path().apply {
                    moveTo(live[0].x, live[0].y)
                    for (i in 1 until live.size) lineTo(live[i].x, live[i].y)
                }
                drawPath(
                    path = path,
                    color = inkColor,
                    style = Stroke(width = (0.004f * size.width).coerceAtLeast(2f), cap = StrokeCap.Round),
                )
            }
        }
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
