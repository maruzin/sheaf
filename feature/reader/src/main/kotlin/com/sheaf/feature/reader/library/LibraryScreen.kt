package com.sheaf.feature.reader.library

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheaf.core.domain.model.Document

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openId by viewModel.openDocument.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(openId) {
        openId?.let { id ->
            viewModel.consumeOpen()
            onOpenDocument(id)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist read permission so the document reopens across app restarts.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val (name, size) = queryNameAndSize(context, uri)
            viewModel.onDocumentPicked(uri.toString(), name, size)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Sheaf") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Open PDF") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { picker.launch(arrayOf("application/pdf")) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
        ) {
            if (state.bookmarked.isNotEmpty()) {
                item { SectionHeader("Bookmarked") }
                items(state.bookmarked, key = { "b" + it.id }) { doc ->
                    DocumentRow(doc, onOpen = onOpenDocument, onBookmark = viewModel::toggleBookmark)
                }
            }
            item { SectionHeader("Recent") }
            if (state.recents.isEmpty()) {
                item { EmptyState() }
            } else {
                items(state.recents, key = { "r" + it.id }) { doc ->
                    DocumentRow(doc, onOpen = onOpenDocument, onBookmark = viewModel::toggleBookmark)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
        Text("No documents yet", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Tap \"Open PDF\" to add one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocumentRow(
    doc: Document,
    onOpen: (Long) -> Unit,
    onBookmark: (Document) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(doc.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text("${doc.pageCount} pages · ${formatSize(doc.sizeBytes)}")
        },
        trailingContent = {
            IconButton(onClick = { onBookmark(doc) }) {
                Icon(
                    imageVector = if (doc.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (doc.isBookmarked) "Remove bookmark" else "Add bookmark",
                )
            }
        },
        modifier = Modifier.clickable { onOpen(doc.id) },
    )
}

private fun queryNameAndSize(context: android.content.Context, uri: Uri): Pair<String, Long> {
    var name = "Document"
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
    }
    return name to size
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
