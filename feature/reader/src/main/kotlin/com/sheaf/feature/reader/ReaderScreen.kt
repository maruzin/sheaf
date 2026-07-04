package com.sheaf.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/** Reader: vertical scroll of pages, pinch-to-zoom, resume position, reading themes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(documentId) { viewModel.onEvent(ReaderEvent.Open(documentId)) }

    val listState = rememberLazyListState()
    // Report the first visible page back into state so resume + page indicator stay in sync.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.onEvent(ReaderEvent.PageChanged(it)) }
    }

    var zoom by remember { mutableFloatStateOf(1f) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.displayName.ifBlank { "Reader" },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val bg = readerBackground(state.theme)
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bg),
        ) {
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
                    onZoom = { factor -> zoom = (zoom * factor).coerceIn(1f, 5f) },
                    aspectRatioOf = viewModel::aspectRatio,
                    renderPage = viewModel::renderPage,
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
}

@Composable
private fun PageList(
    pageCount: Int,
    zoom: Float,
    onZoom: (Float) -> Unit,
    aspectRatioOf: (Int) -> Float,
    renderPage: suspend (Int, Int, Int) -> android.graphics.Bitmap?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    val pages = remember(pageCount) { (0 until pageCount).toList() }
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, gestureZoom, _ -> onZoom(gestureZoom) }
            },
    ) {
        items(pages, key = { it }) { index ->
            PdfPageItem(
                pageIndex = index,
                zoom = zoom,
                aspectRatio = aspectRatioOf(index),
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
    renderPage: suspend (Int, Int, Int) -> android.graphics.Bitmap?,
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    // Cap render resolution to avoid oversized bitmaps at high zoom (memory-safe windowing).
    val targetWidthPx = (screenWidthPx * zoom).roundToInt().coerceIn(1, 3000)
    val targetHeightPx = (targetWidthPx * aspectRatio).roundToInt().coerceIn(1, 6000)

    var bitmap by remember(pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(pageIndex, targetWidthPx, targetHeightPx) {
        bitmap = renderPage(pageIndex, targetWidthPx, targetHeightPx)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspectRatio)
            .padding(vertical = 6.dp),
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
    }
}

private fun readerBackground(theme: ReaderTheme): Color = when (theme) {
    ReaderTheme.Sepia -> Color(0xFFF4ECD8)
    ReaderTheme.Light -> Color(0xFFFFFFFF)
    ReaderTheme.Dark -> Color(0xFF121212)
    ReaderTheme.System -> Color(0xFF2A2A2A)
}
