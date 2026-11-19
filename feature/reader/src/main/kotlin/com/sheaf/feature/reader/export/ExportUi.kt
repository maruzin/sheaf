package com.sheaf.feature.reader.export

import android.content.ClipData
import com.sheaf.feature.reader.ExportReady
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

/** Pick an export format. Word and Excel carry a PRO label for free users. */
@Composable
internal fun ExportDialog(isPro: Boolean, onDismiss: () -> Unit, onPick: (ExportFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export as") },
        text = {
            // Scrolls at large font sizes and in landscape.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ExportFormat.entries.forEach { f ->
                    val pro = !isPro && (f == ExportFormat.Word || f == ExportFormat.Excel)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onPick(f); onDismiss() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(f.label, modifier = Modifier.weight(1f))
                        // A badge, so the paywall is expected rather than a surprise.
                        if (pro) {
                            Text(
                                "Pro",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    "PDF and JPG keep the page exactly as you see it. Word, Excel and TXT contain the text, not the layout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Export finished: share the files or save them to the phone. */
@Composable
internal fun ExportReadyDialog(ready: ExportReady, onShare: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val count = ready.files.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export ready") },
        text = {
            Text(
                if (count == 1) "\"${ready.files[0].name}\" is ready."
                else "$count ${ready.format.extension.uppercase()} files are ready.",
            )
        },
        confirmButton = { Button(onClick = onShare) { Text("Share") } },
        // Two actions (Material guidance; three wrapped badly on small phones). Back or tapping
        // outside closes it, and the export can be made again in a tap.
        dismissButton = { TextButton(onClick = onSave) { Text("Save to phone") } },
    )
}

/** Shares exported files (one or many) through Sheaf's FileProvider, with read grants attached. */
internal fun shareFiles(context: Context, files: List<File>, mime: String) {
    val uris = files.mapNotNull {
        runCatching { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it) }.getOrNull()
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, "Couldn't share the export", Toast.LENGTH_LONG).show()
        return
    }
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.apply {
        type = mime
        clipData = ClipData.newRawUri(files[0].name, uris[0]).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

/**
 * The document's text, ready to copy, edit, translate or share. Edits stay in this screen: they
 * change what you copy or share, not the PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextViewDialog(
    title: String,
    loading: Boolean,
    pages: List<String>,
    needsOcr: Boolean,
    onClose: () -> Unit,
    onUnlockOcr: () -> Unit,
) {
    val context = LocalContext.current
    // One editable field per page: a single field holding a whole long document re-lays out every
    // character on each keystroke and freezes the phone. Per page it stays instant.
    val texts = remember(pages) { mutableStateListOf<String>().apply { addAll(pages.map { it.trim() }) } }
    fun allText(): String =
        texts.mapIndexed { i, t -> if (texts.size > 1) "Page ${i + 1}\n$t" else t }.joinToString("\n\n")
    val hasText = texts.any { it.isNotBlank() }
    // Edits live only in this screen: ask before closing throws them away.
    val edited = texts.toList() != pages.map { it.trim() }
    var confirmClose by remember { mutableStateOf(false) }
    val close = { if (edited) confirmClose = true else onClose() }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Discard your edits?") },
            text = { Text("Edits here aren't saved. Copy or share the text first to keep it.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
        )
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().semantics { paneTitle = "Document text" }) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                TopAppBar(
                    title = { Text("Text") },
                    navigationIcon = {
                        IconButton(onClick = close) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close text")
                        }
                    },
                    actions = {
                        IconButton(onClick = { copyText(context, allText()) }, enabled = hasText) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all text")
                        }
                        IconButton(onClick = { translateText(context, allText()) }, enabled = hasText) {
                            Icon(Icons.Filled.Translate, contentDescription = "Translate")
                        }
                        IconButton(onClick = { shareText(context, allText(), title) }, enabled = hasText) {
                            Icon(Icons.Filled.Share, contentDescription = "Share text")
                        }
                    },
                )
                HorizontalDivider()
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Reading text…", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    needsOcr -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // True for scans and for files with no text layer (blank forms, pictures).
                        Text("No text found in this document.")
                        Text("If it's a scan or a photo, text recognition (OCR) can read it so you can copy, edit, translate and export it.")
                        Button(onClick = onUnlockOcr) { Text("Recognize text (Pro)") }
                    }
                    !hasText && pages.all { it.isBlank() } ->
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No text found in this document.")
                        }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "Edit the text, then copy, translate or share it. The PDF itself isn't changed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(texts.size) { i ->
                            val label = if (texts.size > 1) "Page ${i + 1}" else "Document text"
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f).semantics { heading() },
                                    )
                                    IconButton(onClick = { copyText(context, texts[i]) }, enabled = texts[i].isNotBlank()) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
                                    }
                                    IconButton(onClick = { translateText(context, texts[i]) }, enabled = texts[i].isNotBlank()) {
                                        Icon(Icons.Filled.Translate, contentDescription = "Translate $label")
                                    }
                                }
                                OutlinedTextField(
                                    value = texts[i],
                                    onValueChange = { texts[i] = it },
                                    // The heading above names the page; TalkBack still hears which one.
                                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label text" },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clipboard and share hand text over in one system call, which fails above roughly 1 MB; long
 * documents are capped and the user is told.
 */
private const val MAX_HANDOFF_CHARS = 100_000

/** Google Translate accepts this much at once. */
private const val MAX_TRANSLATE_CHARS = 5_000

private fun capped(context: Context, text: String, max: Int, what: String): String {
    if (text.length <= max) return text
    Toast.makeText(context, "$what the first ${"%,d".format(max)} characters", Toast.LENGTH_LONG).show()
    return text.take(max)
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    val clip = ClipData.newPlainText("Text", capped(context, text, MAX_HANDOFF_CHARS, "Copied"))
    // Document text can be private: keep it out of Android 13+'s clipboard preview.
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    if (runCatching { cm.setPrimaryClip(clip) }.isFailure) {
        Toast.makeText(context, "Couldn't copy that much text", Toast.LENGTH_LONG).show()
        return
    }
    // Android 13+ shows its own "Copied" confirmation.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU && text.length <= MAX_HANDOFF_CHARS) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

private fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, capped(context, text, MAX_HANDOFF_CHARS, "Sharing"))
        .putExtra(Intent.EXTRA_SUBJECT, title)
    runCatching { context.startActivity(Intent.createChooser(send, "Share text")) }
}

/**
 * Opens the text in Google Translate (it accepts ACTION_PROCESS_TEXT). Other "process text" apps
 * (e.g. Read aloud) are skipped on purpose: the button says Translate. Without Google Translate,
 * says so and opens its Play Store page.
 */
private fun translateText(context: Context, text: String) {
    val translate = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        .setPackage(TRANSLATE_PACKAGE)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, capped(context, text, MAX_TRANSLATE_CHARS, "Translating"))
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    if (runCatching { context.startActivity(translate) }.isSuccess) return
    Toast.makeText(context, "Install Google Translate to translate text", Toast.LENGTH_LONG).show()
    val store = Intent(Intent.ACTION_VIEW, "market://details?id=$TRANSLATE_PACKAGE".toUri())
    if (runCatching { context.startActivity(store) }.isFailure) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$TRANSLATE_PACKAGE".toUri()),
            )
        }
    }
}

private const val TRANSLATE_PACKAGE = "com.google.android.apps.translate"
