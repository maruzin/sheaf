package com.sheaf.feature.reader.pages

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(
    documentId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(documentId) { viewModel.open(documentId) }
    LaunchedEffect(state.savedDocumentId) { state.savedDocumentId?.let { onSaved(it) } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Manage pages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 12.dp))
                    } else {
                        TextButton(onClick = { viewModel.save() }, enabled = state.items.isNotEmpty()) {
                            Text("Save copy")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error ?: "Error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items, key = { _, it -> it.sourceIndex }) { index, item ->
                        PageCard(
                            item = item,
                            number = index + 1,
                            total = state.items.size,
                            aspect = viewModel.aspectRatio(item.sourceIndex),
                            renderThumb = { w, h -> viewModel.renderThumb(item.sourceIndex, w, h) },
                            onRotate = { viewModel.rotate(index) },
                            onDelete = { viewModel.delete(index) },
                            onLeft = { viewModel.move(index, -1) },
                            onRight = { viewModel.move(index, 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCard(
    item: PageItem,
    number: Int,
    total: Int,
    aspect: Float,
    renderThumb: suspend (Int, Int) -> Bitmap?,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    var bitmap by remember(item.sourceIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.sourceIndex) {
        bitmap = renderThumb(240, (240 * aspect).roundToInt().coerceIn(1, 2000))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / aspect), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page $number",
                    modifier = Modifier.fillMaxWidth().rotate(item.rotation.toFloat()),
                )
            } else {
                CircularProgressIndicator()
            }
        }
        Text(
            text = "$number",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLeft, enabled = number > 1) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Move left")
            }
            IconButton(onClick = onRotate) {
                Icon(Icons.Filled.RotateRight, contentDescription = "Rotate")
            }
            IconButton(onClick = onDelete, enabled = total > 1) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
            IconButton(onClick = onRight, enabled = number < total) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Move right")
            }
        }
    }
}
