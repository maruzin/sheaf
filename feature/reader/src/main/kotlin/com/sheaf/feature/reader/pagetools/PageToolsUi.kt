package com.sheaf.feature.reader.pagetools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private enum class Screen { List, Numbers, Watermark, Crop }

/**
 * Page tools for the open document. Each makes a new copy in the library; the original is not
 * changed. [page] is the page on screen (0-based), used for insert and split.
 */
@Composable
internal fun PageToolsDialog(
    page: Int,
    pageCount: Int,
    isPro: Boolean,
    onDismiss: () -> Unit,
    onRun: (PageTool) -> Unit,
    onInsertPdf: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.List) }
    var position by rememberSaveable { mutableStateOf(NumberPosition.BottomCenter) }
    var withTotal by rememberSaveable { mutableStateOf(false) }
    var mark by rememberSaveable { mutableStateOf("CONFIDENTIAL") }
    var strength by rememberSaveable { mutableFloatStateOf(0.18f) }
    var crop by rememberSaveable { mutableFloatStateOf(0.05f) }
    fun pro(label: String) = if (isPro) label else "$label (Pro)"
    val n = page + 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (screen) {
                    Screen.List -> "Page tools"
                    Screen.Numbers -> "Page numbers"
                    Screen.Watermark -> "Watermark"
                    Screen.Crop -> "Crop margins"
                },
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (screen) {
                    Screen.List -> {
                        Text(
                            "Each tool makes a new copy in your library. Your original isn't changed, and markup comes along.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Tool(pro("Add page numbers")) { screen = Screen.Numbers }
                        Tool(pro("Add a watermark")) { screen = Screen.Watermark }
                        Tool("Crop margins") { screen = Screen.Crop }
                        HorizontalDivider()
                        Tool("Insert a blank page after page $n") { onRun(PageTool.Blank(page)) }
                        Tool(pro("Insert pages from a PDF after page $n")) { onInsertPdf() }
                        if (pageCount > 1 && page < pageCount - 1) {
                            Tool("Split into two after page $n") { onRun(PageTool.Split(page)) }
                        }
                    }
                    Screen.Numbers -> {
                        Column(Modifier.selectableGroup()) {
                            NumberPosition.entries.forEach { p ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .selectable(selected = p == position, role = Role.RadioButton) { position = p },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = p == position, onClick = null)
                                    Text(p.label, Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(value = withTotal, role = Role.Switch) { withTotal = it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Write \"Page 1 of $pageCount\"", Modifier.weight(1f))
                            Switch(checked = withTotal, onCheckedChange = null)
                        }
                    }
                    Screen.Watermark -> {
                        OutlinedTextField(
                            value = mark,
                            onValueChange = { mark = it.take(40) },
                            singleLine = true,
                            label = { Text("Watermark text") },
                            supportingText = { Text("Letters, numbers and simple punctuation") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Strength: ${(strength * 100).roundToInt()}%")
                        Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.05f..0.5f)
                    }
                    Screen.Crop -> {
                        Text("Trim ${(crop * 100).roundToInt()}% from every side of every page.")
                        Slider(value = crop, onValueChange = { crop = it }, valueRange = 0.01f..0.2f)
                        Text(
                            "Only the visible area changes; nothing is deleted from the file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (screen) {
                Screen.List -> TextButton(onClick = onDismiss) { Text("Close") }
                Screen.Numbers -> Button(onClick = { onRun(PageTool.Numbers(position, withTotal)) }) { Text("Add numbers") }
                Screen.Watermark -> Button(
                    onClick = { onRun(PageTool.Watermark(mark, strength)) },
                    enabled = mark.any { it.code in 0x21..0x7E },
                ) { Text("Add watermark") }
                Screen.Crop -> Button(onClick = { onRun(PageTool.Crop(crop)) }) { Text("Crop") }
            }
        },
        dismissButton = {
            if (screen != Screen.List) TextButton(onClick = { screen = Screen.List }) { Text("Back") }
        },
    )
}

@Composable
private fun Tool(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
