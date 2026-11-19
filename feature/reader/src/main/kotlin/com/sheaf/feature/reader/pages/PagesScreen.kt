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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

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
                title = {
                    Text(
                        if (state.selecting) "${state.selected.size} selected" else "Manage pages",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                },
                navigationIcon = {
                    if (state.selecting) {
                        IconButton(onClick = { viewModel.setSelecting(false) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop selecting")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    when {
                        state.saving -> CircularProgressIndicator(Modifier.size(22.dp).padding(end = 12.dp))
                        state.selecting -> {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text(if (state.selected.size == state.items.size) "Clear" else "All")
                            }
                            Button(onClick = { viewModel.extract() }, enabled = state.selected.isNotEmpty()) {
                                Text("Extract")
                            }
                        }
                        else -> {
                            // Extract: pick pages and save just those as a new PDF.
                            TextButton(onClick = { viewModel.setSelecting(true) }, enabled = state.items.isNotEmpty()) {
                                Text("Select")
                            }
                            TextButton(onClick = { viewModel.save() }, enabled = state.items.isNotEmpty()) {
                                Text("Save copy")
                            }
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
                            selecting = state.selecting,
                            selected = item.sourceIndex in state.selected,
                            onToggle = { viewModel.toggleSelected(item.sourceIndex) },
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
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit = {},
) {
    var bitmap by remember(item.sourceIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.sourceIndex) {
        bitmap = renderThumb(240, (240 * aspect).roundToInt().coerceIn(1, 2000))
    }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .then(
                if (selecting) {
                    Modifier
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape)
                        // The whole card is one checkbox in select mode.
                        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
                        .semantics { contentDescription = "Page $number" }
                } else {
                    Modifier
                },
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / aspect), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page $number" + if (item.rotation != 0) ", rotated ${item.rotation} degrees" else "",
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
        if (selecting) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = null)
                Text(if (selected) "Selected" else "Tap to select", style = MaterialTheme.typography.labelMedium)
            }
            return@Column
        }
        // Two rows of two: four 48dp touch targets don't fit the 140dp-min grid cell in one row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLeft, enabled = number > 1) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Move page $number earlier")
            }
            IconButton(onClick = onRight, enabled = number < total) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Move page $number later")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRotate) {
                Icon(Icons.Filled.RotateRight, contentDescription = "Rotate page $number")
            }
            IconButton(onClick = onDelete, enabled = total > 1) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete page $number")
            }
        }
    }
}
