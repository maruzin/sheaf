package com.sheaf.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Open documents as tabs. Shown only when more than one document is open. */
@Composable
internal fun ReaderTabsRow(
    tabs: List<ReaderTab>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentId, tabs.size) {
        val i = tabs.indexOfFirst { it.documentId == currentId }
        if (i >= 0) listState.animateScrollToItem(i)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs, key = { it.documentId }) { tab ->
            val selected = tab.documentId == currentId
            InputChip(
                selected = selected,
                onClick = { if (!selected) onSelect(tab.documentId) },
                label = {
                    Text(
                        tab.title.removeSuffix(".pdf"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onClose(tab.documentId) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close ${tab.title}", Modifier.size(16.dp))
                    }
                },
                modifier = Modifier.semantics {
                    stateDescription = if (selected) "Current tab" else "Tab"
                    customActions = listOf(
                        CustomAccessibilityAction("Close tab") { onClose(tab.documentId); true },
                    )
                },
            )
        }
    }
}
