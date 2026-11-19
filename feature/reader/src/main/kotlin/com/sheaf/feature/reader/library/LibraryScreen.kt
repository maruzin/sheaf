package com.sheaf.feature.reader.library

import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.semantics.Role
import com.sheaf.feature.reader.export.IdKind

import com.sheaf.feature.reader.PaywallDialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.sheaf.core.domain.model.Document
import java.io.File
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
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
        val isImage = uri != null && context.contentResolver.getType(uri)?.startsWith("image/") == true
        if (uri != null && isImage) {
            // A photo or picture becomes a PDF in the library, so it can be signed and shared.
            val (name, _) = queryNameAndSize(context, uri)
            viewModel.onImagesPicked(listOf(uri), name.substringBeforeLast('.') + ".pdf")
        } else if (uri != null) {
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

    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(30)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scan?.pdf?.uri
            val imported = pdfUri?.let { importScannedPdf(context, it) }
            if (imported != null) {
                viewModel.onDocumentPicked(imported.first, imported.second, imported.third)
            } else if (pdfUri != null) {
                Toast.makeText(context, "Couldn't import the scan", Toast.LENGTH_LONG).show()
            }
        }
    }
    val startScan: () -> Unit = {
        (context as? Activity)?.let { activity ->
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Scanner isn't available on this device", Toast.LENGTH_LONG).show()
                }
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    var pendingRemove by remember { mutableStateOf<Document?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val showPaywall by viewModel.showPaywall.collectAsStateWithLifecycle()
    val proPrice by viewModel.proPrice.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()

    // Photos → PDF: pick several pictures; each becomes a page, in the order picked.
    val photosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.getDefault()).format(java.util.Date())
            viewModel.onImagesPicked(uris, "Photos $stamp.pdf")
        }
    }
    // ID card / passport: scan front and back, then choose the document type and an optional watermark.
    var idSides by remember { mutableStateOf<List<Uri>?>(null) }
    val idScanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(2)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    val idScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
            if (pages.isNotEmpty()) idSides = pages.map { it.imageUri }
        }
    }
    // Without the scanner (no Google Play services module), pick the front and back photos instead.
    val idPhotosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 2),
    ) { uris: List<Uri> -> if (uris.isNotEmpty()) idSides = uris }
    val startIdScan: () -> Unit = {
        (context as? Activity)?.let { activity ->
            idScanner.getStartScanIntent(activity)
                .addOnSuccessListener { idScanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener {
                    Toast.makeText(context, "Scanner isn't available. Choose photos of the front and back.", Toast.LENGTH_LONG).show()
                    idPhotosPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
        }
    }

    // Merge: pick several PDFs at once; they are joined in the order the picker returns them.
    val mergePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.merge(uris.map { it.toString() to queryNameAndSize(context, it).first })
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Sheaf") },
                actions = {
                    IconButton(onClick = startScan) {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = "Scan document")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("ID card or passport copy") },
                                onClick = {
                                    menuOpen = false
                                    startIdScan()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Photos to PDF") },
                                onClick = {
                                    menuOpen = false
                                    photosPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (isPro) "Merge PDFs" else "Merge PDFs  ·  PRO") },
                                onClick = {
                                    menuOpen = false
                                    if (viewModel.requestMerge()) mergePicker.launch(arrayOf("application/pdf"))
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Open file") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                // PDFs, and photos / pictures (turned into a PDF so they can be signed).
                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                // M3's extended FAB clears its text from the accessibility tree, which left TalkBack
                // announcing an unlabeled "Button" (found by the device tests). Label it explicitly.
                modifier = Modifier.semantics { contentDescription = "Open file" },
            )
        },
    ) { padding ->
        if (state.isBusy) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .semantics { contentDescription = "Opening document" },
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
        ) {
            if (state.bookmarked.isNotEmpty()) {
                item { SectionHeader("Bookmarked") }
                items(state.bookmarked, key = { "b" + it.id }) { doc ->
                    DocumentRow(
                        doc,
                        onOpen = onOpenDocument,
                        onBookmark = viewModel::toggleBookmark,
                        onRemove = { pendingRemove = it },
                        loadThumb = viewModel::thumbnail,
                    )
                }
            }
            item { SectionHeader("Recent") }
            if (state.recents.isEmpty()) {
                item { EmptyState() }
            } else {
                items(state.recents, key = { "r" + it.id }) { doc ->
                    DocumentRow(
                        doc,
                        onOpen = onOpenDocument,
                        onBookmark = viewModel::toggleBookmark,
                        onRemove = { pendingRemove = it },
                        loadThumb = viewModel::thumbnail,
                    )
                }
            }
        }
    }

    idSides?.let { sides ->
        IdCopyDialog(
            sides = sides.size,
            onDismiss = { idSides = null },
            onCreate = { kind, watermark ->
                viewModel.makeIdCopy(sides[0], sides.getOrNull(1), kind, watermark)
                idSides = null
            },
        )
    }

    if (showPaywall) {
        PaywallDialog(
            price = proPrice,
            onDismiss = viewModel::dismissPaywall,
            onUpgrade = { (context as? Activity)?.let { viewModel.upgrade(it) } },
            onRestore = viewModel::restorePurchase,
        )
    }

    pendingRemove?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove from library?") },
            text = {
                Text(
                    if (doc.uri.startsWith("file:")) {
                        "\"${doc.displayName}\" is stored only in Sheaf (a scan, OCR result, edited " +
                            "copy or opened file). It will be permanently deleted with its annotations."
                    } else {
                        "\"${doc.displayName}\" and its annotations will be removed from Sheaf. " +
                            "The original file in its app is not deleted."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(doc)
                    pendingRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Description, contentDescription = null)
        Text("No documents yet", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Tap \"Open file\" to add a PDF or a photo.",
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
    onRemove: (Document) -> Unit,
    loadThumb: suspend (String, Int) -> Bitmap?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var thumb by remember(doc.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc.uri) { thumb = loadThumb(doc.uri, 160) }
    ListItem(
        leadingContent = {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 44.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)),
                )
            } else {
                Box(
                    Modifier.size(width = 44.dp, height = 56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                }
            }
        },
        headlineContent = {
            Text(doc.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val pages = if (doc.pageCount > 0) "${doc.pageCount} pages" else "Password protected"
            Text("$pages · ${formatSize(doc.sizeBytes)}")
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onBookmark(doc) }) {
                    Icon(
                        imageVector = if (doc.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (doc.isBookmarked) "Remove bookmark" else "Add bookmark",
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${doc.displayName}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove from library") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRemove(doc)
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .clickable(onClickLabel = "Open") { onOpen(doc.id) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Remove from library") { onRemove(doc); true },
                )
            },
    )
}

/** Copies a scanned PDF (temporary GMS uri) into app storage; returns (fileUri, name, sizeBytes). */
private fun importScannedPdf(context: android.content.Context, pdfUri: Uri): Triple<String, String, Long>? =
    runCatching {
        val dir = File(context.filesDir, "scanned").apply { mkdirs() }
        val outFile = File(dir, "scan_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.getDefault()).format(java.util.Date())
        Triple(Uri.fromFile(outFile).toString(), "Scan $stamp.pdf", outFile.length())
    }.getOrNull()

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

@Composable
private fun IdCopyDialog(sides: Int, onDismiss: () -> Unit, onCreate: (IdKind, String) -> Unit) {
    var kind by remember { mutableStateOf(IdKind.IdCard) }
    var watermark by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ID copy") },
        text = {
            // Scrolls at large font sizes and with the keyboard open.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (sides >= 2) "Front and back go on one A4 page, at real size." else "The scan goes on an A4 page, at real size.")
                Column(Modifier.selectableGroup()) {
                IdKind.entries.forEach { k ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = k == kind, role = Role.RadioButton) { kind = k },
                    ) {
                        RadioButton(selected = k == kind, onClick = null)
                        Text(k.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                }
                OutlinedTextField(
                    value = watermark,
                    onValueChange = { watermark = it.take(60) },
                    singleLine = true,
                    label = { Text("Watermark (optional)") },
                    placeholder = { Text("Copy for rental application only") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "A watermark says what the copy is for, which makes it harder to misuse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onCreate(kind, watermark) }) { Text("Create PDF") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
