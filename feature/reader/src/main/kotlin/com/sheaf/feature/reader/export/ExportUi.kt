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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/** Pick an export format. Word and Excel carry a PRO label for free users. */
@Composable
internal fun ExportDialog(isPro: Boolean, onDismiss: () -> Unit, onPick: (ExportFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export as") },
        text = {
            Column {
                ExportFormat.entries.forEach { f ->
                    val pro = !isPro && (f == ExportFormat.Word || f == ExportFormat.Excel)
                    Text(
                        text = if (pro) "${f.label}  ·  PRO" else f.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { role = Role.Button }
                            .clickable { onPick(f); onDismiss() }
                            .padding(vertical = 14.dp),
                    )
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
    val initial = remember(pages) {
        pages.mapIndexed { i, t -> if (pages.size > 1) "— Page ${i + 1} —\n${t.trim()}" else t.trim() }
            .joinToString("\n\n")
            .take(MAX_CHARS)
    }
    var text by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                TopAppBar(
                    title = { Text("Text") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close text")
                        }
                    },
                    actions = {
                        val enabled = text.isNotBlank()
                        IconButton(onClick = { copyText(context, text) }, enabled = enabled) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all text")
                        }
                        IconButton(onClick = { translateText(context, text) }, enabled = enabled) {
                            Icon(Icons.Filled.Translate, contentDescription = "Translate")
                        }
                        IconButton(onClick = { shareText(context, text, title) }, enabled = enabled) {
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
                        Text("This document is a scan, so its text is part of the picture.")
                        Text("Text recognition (OCR) reads it so you can copy, edit, translate and export it.")
                        Button(onClick = onUnlockOcr) { Text("Recognize text  ·  PRO") }
                    }
                    initial.isBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No text found in this document.")
                    }
                    else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Edit the text, then copy, translate or share it. The PDF itself isn't changed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_CHARS = 300_000

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Text", text))
    // Android 13+ shows its own "Copied" confirmation.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

private fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
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
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text.take(5000))
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
