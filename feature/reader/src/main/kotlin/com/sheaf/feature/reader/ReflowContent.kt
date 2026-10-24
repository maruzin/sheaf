package com.sheaf.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Maps list rows to pages: one header row per page followed by its paragraphs. */
internal class ReflowLayout(pages: List<List<String>>) {
    val rows: List<Row>
    private val headerIndexOfPage: IntArray

    init {
        val list = ArrayList<Row>()
        headerIndexOfPage = IntArray(pages.size)
        pages.forEachIndexed { page, paragraphs ->
            headerIndexOfPage[page] = list.size
            list += Row.Header(page)
            paragraphs.forEachIndexed { i, text -> list += Row.Paragraph(page, i, text) }
        }
        rows = list
    }

    fun rowForPage(page: Int): Int = headerIndexOfPage.getOrElse(page) { 0 }

    fun pageForRow(row: Int): Int = when (val r = rows.getOrNull(row)) {
        is Row.Header -> r.page
        is Row.Paragraph -> r.page
        null -> 0
    }

    sealed interface Row {
        data class Header(val page: Int) : Row
        data class Paragraph(val page: Int, val index: Int, val text: String) : Row
    }
}

/** Reading mode: the document's text re-laid out as paragraphs, sized and themed for comfort. */
@Composable
internal fun ReflowContent(
    pages: List<List<String>>,
    loading: Boolean,
    textLayerMissing: Boolean,
    theme: ReaderTheme,
    scale: Float,
    startPage: Int,
    listState: LazyListState,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (textColor, background) = reflowColors(theme)
    Box(modifier.fillMaxSize().background(background)) {
        when {
            loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center).semantics { contentDescription = "Preparing reading mode" },
            )
            textLayerMissing || pages.all { it.isEmpty() } -> Text(
                text = "This document has no text layer (it looks like a scan). " +
                    "Use \"Make searchable (OCR)\" to add one, then try reading mode again.",
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> {
                val layout = remember(pages) { ReflowLayout(pages) }
                LaunchedEffect(layout) { listState.scrollToItem(layout.rowForPage(startPage)) }
                LaunchedEffect(layout, listState) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .collect { onPageChanged(layout.pageForRow(it)) }
                }
                val bodySize = (18 * scale).sp
                LazyColumn(
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(layout.rows.size) { i ->
                        // Comfortable measure (~70 characters) on tablets and landscape.
                        val rowModifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()
                        when (val row = layout.rows[i]) {
                            is ReflowLayout.Row.Header -> Text(
                                text = "Page ${row.page + 1}",
                                color = textColor.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = rowModifier.padding(top = 8.dp).semantics { heading() },
                            )
                            is ReflowLayout.Row.Paragraph -> Text(
                                text = row.text,
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = bodySize,
                                    lineHeight = bodySize * 1.55f,
                                ),
                                modifier = rowModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
